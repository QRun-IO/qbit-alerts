/*
 * Copyright 2026 Kingsrook, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kingsrook.qbits.alerts.home;


import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.alerts.AlertsRuntime;
import com.kingsrook.qbits.alerts.api.AlertsApi;
import com.kingsrook.qbits.alerts.delivery.AlertMessageRenderer;
import com.kingsrook.qbits.alerts.engine.AlertsDb;
import com.kingsrook.qbits.alerts.model.AlertsAlert;
import com.kingsrook.qbits.alerts.model.AlertsChannel;
import com.kingsrook.qbits.alerts.model.AlertsDelivery;
import com.kingsrook.qbits.alerts.model.AlertsEvent;
import com.kingsrook.qbits.alerts.model.AlertsGroup;
import com.kingsrook.qbits.alerts.model.AlertsNotification;
import com.kingsrook.qbits.alerts.model.AlertsNotificationRecipient;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.DeliveryStatus;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.EventType;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.HealthStatus;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.Status;
import com.kingsrook.qbits.alerts.model.AlertsSubscription;
import com.kingsrook.qbits.alerts.model.AlertsType;
import com.kingsrook.qbits.alerts.notifications.NotificationService;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper;
import com.kingsrook.qqq.backend.core.actions.permissions.TablePermissionSubType;
import com.kingsrook.qqq.backend.core.alerts.AlertSeverity;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.ChartData;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.QWidgetData;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.RawHTML;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.TableData;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** The Alerts home (design section 6.2), the single owner of open-alert data.
 ** Every widget is full width, reads real rows in the viewer's session (so
 ** the record locks decide what is counted and listed), and has an honest
 ** empty state. Other pages get link-only summaries from AlertSummaryLoader.
 *******************************************************************************/
public final class AlertsHome
{
   public static final String KIND_SEVERITY  = "severity";
   public static final String KIND_MINE      = "mine";
   public static final String KIND_ACTION    = "needsAction";
   public static final String KIND_BY_GROUP  = "byGroup";
   public static final String KIND_TREND     = "trend";
   public static final String KIND_NOISY     = "noisy";
   public static final String KIND_DELIVERY  = "delivery";
   public static final String KIND_NOTIFICATIONS = "notifications";
   public static final String KIND_RESOLVED  = "resolved";

   static final int ROWS       = 10;
   static final int SCAN_LIMIT = 2000;
   static final int TREND_DAYS = 30;

   private static final String ALERTS_PATH = "/app/" + AlertsAlert.TABLE_NAME;

   private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US);



   /*******************************************************************************
    ** Loaders only.
    *******************************************************************************/
   private AlertsHome()
   {
   }



   /*******************************************************************************
    ** The widget data for a kind.
    *******************************************************************************/
   public static QWidgetData load(String kind, AlertsRuntime runtime) throws QException
   {
      if(!canRead(AlertsAlert.TABLE_NAME))
      {
         return (new RawHTML(null, HomeHtml.emptyState("You don't have permission to read alerts.")));
      }
      Instant now = runtime.now();
      return switch(kind == null ? "" : kind)
      {
         case KIND_SEVERITY -> severity(runtime, now);
         case KIND_MINE -> mine(now);
         case KIND_ACTION -> needsAction(now);
         case KIND_BY_GROUP -> byGroup(now);
         case KIND_TREND -> trend(now);
         case KIND_NOISY -> noisy(now);
         case KIND_DELIVERY -> delivery(now);
         case KIND_NOTIFICATIONS -> notifications(now);
         case KIND_RESOLVED -> resolved(now);
         default -> new RawHTML(null, HomeHtml.emptyState("Unknown Alerts widget."));
      };
   }



   /*******************************************************************************
    ** Open by severity: four tiles, each with the unacknowledged count and the
    ** oldest age, linking to the filtered list.
    *******************************************************************************/
   static QWidgetData severity(AlertsRuntime runtime, Instant now) throws QException
   {
      List<QRecord>       open  = openAlerts();
      List<HomeHtml.Tile> tiles = new ArrayList<>();
      for(AlertSeverity severity : List.of(AlertSeverity.CRITICAL, AlertSeverity.ERROR, AlertSeverity.WARNING, AlertSeverity.INFO))
      {
         List<QRecord> these   = open.stream().filter(alert -> severity.name().equals(alert.getValueString("severity"))).toList();
         long          unacked = these.stream().filter(alert -> Status.OPEN.name().equals(alert.getValueString("status"))).count();
         Instant       oldest  = these.stream().map(alert -> alert.getValueInstant("firstRaisedAt")).filter(java.util.Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
         String        note    = these.isEmpty() ? "none open" : unacked + " unacknowledged · oldest " + AlertMessageRenderer.age(oldest, now);
         tiles.add(new HomeHtml.Tile(AlertsPossibleValues.label(severity.name()), HomeHtml.number(these.size()), note,
            listUrl(new QQueryFilter().withCriteria("severity", QCriteriaOperator.EQUALS, severity.name()).withCriteria("status", QCriteriaOperator.IN, Status.openNames()))));
      }

      String scope = open.isEmpty() ? "No open alerts you can see." : HomeHtml.plural(open.size(), "open alert") + " you can see.";
      if(runtime.getConfig().getSchedulerName() == null)
      {
         scope += " No scheduler is configured, so monitors and email delivery only run when started by hand.";
      }
      return (new RawHTML(null, HomeHtml.tiles(tiles) + HomeHtml.note(scope)));
   }



   /*******************************************************************************
    ** My alerts: assigned to me, or in my bell (not dismissed); unacknowledged first.
    *******************************************************************************/
   static QWidgetData mine(Instant now) throws QException
   {
      QSession session = QContext.getQSession();
      String   userId  = session == null || session.getUser() == null ? null : session.getUser().getIdReference();
      if(userId == null)
      {
         return (new RawHTML(null, HomeHtml.emptyState("Your session has no user, so there are no personal alerts.")));
      }

      Map<Integer, QRecord> mine = new LinkedHashMap<>();
      for(QRecord alert : AlertsDb.query(AlertsAlert.TABLE_NAME, AlertsDb.eq("assignedUserId", userId).withCriteria("status", QCriteriaOperator.IN, Status.openNames())))
      {
         mine.put(alert.getValueInteger("id"), alert);
      }
      if(canRead(AlertsNotificationRecipient.TABLE_NAME))
      {
         List<Integer> ids = AlertsApi.myAlertIds(userId, SCAN_LIMIT);
         if(!ids.isEmpty())
         {
            for(QRecord alert : AlertsDb.query(AlertsAlert.TABLE_NAME, new QQueryFilter().withCriteria("id", QCriteriaOperator.IN, ids).withCriteria("status", QCriteriaOperator.IN, Status.openNames())))
            {
               mine.putIfAbsent(alert.getValueInteger("id"), alert);
            }
         }
      }

      List<QRecord> sorted = new ArrayList<>(mine.values());
      sorted.sort(Comparator.comparing((QRecord alert) -> Status.OPEN.name().equals(alert.getValueString("status")) ? 0 : 1).thenComparing(AlertsHome::bySeverityThenAge));

      int subscriptions = canRead(AlertsSubscription.TABLE_NAME) ? AlertsDb.count(AlertsSubscription.TABLE_NAME, AlertsDb.eq("userId", userId).withCriteria("enabled", QCriteriaOperator.EQUALS, true)) : 0;
      String empty = "Nothing needs you. You have " + HomeHtml.plural(subscriptions, "subscription") + ".";
      TableData table = alertTable(sorted.subList(0, Math.min(ROWS, sorted.size())), now, "Status", alert -> statusBadge(alert), empty);
      return (table.withLinkText(subscriptions == 0 ? "Manage subscriptions" : "View my alerts").withLinkURL(subscriptions == 0 ? "/app/" + AlertsSubscription.TABLE_NAME
         : listUrl(new QQueryFilter().withCriteria("assignedUserId", QCriteriaOperator.EQUALS, userId).withCriteria("status", QCriteriaOperator.IN, Status.openNames()))));
   }



   /*******************************************************************************
    ** Needs action: open, unacknowledged, unsnoozed; by severity, then age.
    *******************************************************************************/
   static QWidgetData needsAction(Instant now) throws QException
   {
      List<QRecord> open = new ArrayList<>(AlertsDb.query(AlertsAlert.TABLE_NAME, AlertsDb.eq("status", Status.OPEN.name()).withLimit(SCAN_LIMIT)));
      open.sort(AlertsHome::bySeverityThenAge);
      TableData table = alertTable(open.subList(0, Math.min(ROWS, open.size())), now, "Owner", AlertsHome::owner, "No open alerts need action.");
      return (table.withLinkText("View all").withLinkURL(listUrl(AlertsDb.eq("status", Status.OPEN.name()))));
   }



   /*******************************************************************************
    ** By group: open count, oldest, and a 7-day trend of opened alerts.
    *******************************************************************************/
   static QWidgetData byGroup(Instant now) throws QException
   {
      List<QRecord>          open   = openAlerts();
      Map<Integer, String>   labels = labels(AlertsGroup.TABLE_NAME, "label");
      Map<Integer, Integer>  counts = new LinkedHashMap<>();
      Map<Integer, Instant>  oldest = new HashMap<>();
      for(QRecord alert : open)
      {
         Integer group = alert.getValueInteger("groupId");
         counts.merge(group, 1, Integer::sum);
         Instant first = alert.getValueInstant("firstRaisedAt");
         if(first != null && (oldest.get(group) == null || first.isBefore(oldest.get(group))))
         {
            oldest.put(group, first);
         }
      }

      Instant                     since  = now.minus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
      Map<Integer, int[]>         trends = new HashMap<>();
      for(QRecord alert : AlertsDb.query(AlertsAlert.TABLE_NAME, new QQueryFilter().withCriteria("firstRaisedAt", QCriteriaOperator.GREATER_THAN_OR_EQUALS, since).withLimit(SCAN_LIMIT)))
      {
         int day = (int) ChronoUnit.DAYS.between(since, alert.getValueInstant("firstRaisedAt").truncatedTo(ChronoUnit.DAYS));
         if(day >= 0 && day < 8)
         {
            trends.computeIfAbsent(alert.getValueInteger("groupId"), key -> new int[8])[day]++;
         }
      }

      List<Map<String, Object>> rows = new ArrayList<>();
      List<Integer>             groups = new ArrayList<>(counts.keySet());
      groups.sort(Comparator.comparing((Integer group) -> -counts.get(group)));
      for(Integer group : groups)
      {
         Map<String, Object> row = new HashMap<>();
         String              label = group == null ? "No group" : labels.getOrDefault(group, "Group " + group);
         row.put("group", group == null ? HomeHtml.escape(label) : HomeHtml.link(listUrl(AlertsDb.eq("groupId", group).withCriteria("status", QCriteriaOperator.IN, Status.openNames())), label));
         row.put("open", HomeHtml.number(counts.get(group)));
         row.put("oldest", HomeHtml.escape(AlertMessageRenderer.age(oldest.get(group), now)));
         row.put("trend", sparkline(trends.getOrDefault(group, new int[8])));
         rows.add(row);
      }
      return (table(List.of(column("Group", "group"), column("Open", "open"), column("Oldest", "oldest"), column("7 days", "trend")), rows,
         "No open alerts in any group."));
   }



   /*******************************************************************************
    ** Trend: alerts opened and resolved per day for 30 days, with mean time to
    ** acknowledge and to resolve.
    *******************************************************************************/
   static QWidgetData trend(Instant now) throws QException
   {
      LocalDate          today  = LocalDate.ofInstant(now, ZoneOffset.UTC);
      LocalDate          start  = today.minusDays(TREND_DAYS - 1L);
      Instant            since  = start.atStartOfDay(ZoneOffset.UTC).toInstant();
      Map<LocalDate, int[]> perDay = new LinkedHashMap<>();
      for(LocalDate day = start; !day.isAfter(today); day = day.plusDays(1))
      {
         perDay.put(day, new int[2]);
      }

      List<QRecord> events = canRead(AlertsEvent.TABLE_NAME) ? AlertsDb.query(AlertsEvent.TABLE_NAME, new QQueryFilter()
         .withCriteria("eventType", QCriteriaOperator.IN, List.of(EventType.RAISED.name(), EventType.REOPENED.name(), EventType.RESOLVED.name()))
         .withCriteria("at", QCriteriaOperator.GREATER_THAN_OR_EQUALS, since).withLimit(SCAN_LIMIT * 5)) : List.of();
      LocalDate first = null;
      for(QRecord event : events)
      {
         LocalDate day    = LocalDate.ofInstant(event.getValueInstant("at"), ZoneOffset.UTC);
         int[]     counts = perDay.get(day);
         if(counts != null)
         {
            counts[EventType.RESOLVED.name().equals(event.getValueString("eventType")) ? 1 : 0]++;
            first = first == null || day.isBefore(first) ? day : first;
         }
      }

      List<String> labels   = new ArrayList<>();
      List<Number> opened   = new ArrayList<>();
      List<Number> resolved = new ArrayList<>();
      for(Map.Entry<LocalDate, int[]> entry : perDay.entrySet())
      {
         if(first != null && entry.getKey().isBefore(first))
         {
            continue;
         }
         labels.add(DAY.format(entry.getKey()));
         opened.add(entry.getValue()[0]);
         resolved.add(entry.getValue()[1]);
      }

      ChartData chart = new ChartData().withChartData(new ChartData.Data()
         .withLabels(labels)
         .withDatasets(List.of(
            new ChartData.Data.Dataset().withLabel("Opened").withData(opened).withColor(HomeHtml.BAD_COLOR),
            new ChartData.Data.Dataset().withLabel("Resolved").withData(resolved).withColor(HomeHtml.OK_COLOR))));
      chart.setSublabel(events.isEmpty() ? "No alerts opened or resolved in the last " + TREND_DAYS + " days." : meanTimes(since));
      return (chart);
   }



   /*******************************************************************************
    ** Noisy: the types that opened, reopened or flapped most in 7 days.
    *******************************************************************************/
   static QWidgetData noisy(Instant now) throws QException
   {
      Instant              since  = now.minus(7, ChronoUnit.DAYS);
      Map<String, int[]>   counts = new HashMap<>();
      Map<String, Integer> typeIds = new HashMap<>();
      if(canRead(AlertsEvent.TABLE_NAME))
      {
         Map<Integer, QRecord> alerts = new HashMap<>();
         for(QRecord event : AlertsDb.query(AlertsEvent.TABLE_NAME, new QQueryFilter()
            .withCriteria("eventType", QCriteriaOperator.IN, List.of(EventType.RAISED.name(), EventType.REOPENED.name(), EventType.FLAPPING_STARTED.name()))
            .withCriteria("at", QCriteriaOperator.GREATER_THAN_OR_EQUALS, since).withLimit(SCAN_LIMIT * 5)))
         {
            Integer alertId = event.getValueInteger("alertId");
            QRecord alert   = alerts.computeIfAbsent(alertId, id -> quietly(id));
            if(alert == null)
            {
               continue;
            }
            String key = alert.getValueString("alertTypeName");
            typeIds.put(key, alert.getValueInteger("alertTypeId"));
            int[] c = counts.computeIfAbsent(key, k -> new int[3]);
            switch(EventType.valueOf(event.getValueString("eventType")))
            {
               case RAISED -> c[0]++;
               case REOPENED -> c[1]++;
               default -> c[2]++;
            }
         }
      }

      List<String> keys = new ArrayList<>(counts.keySet());
      keys.sort(Comparator.comparing((String key) -> -(counts.get(key)[0] + counts.get(key)[1] + counts.get(key)[2])));
      Map<Integer, String>      labels = labels(AlertsType.TABLE_NAME, "label");
      List<Map<String, Object>> rows   = new ArrayList<>();
      for(String key : keys.subList(0, Math.min(ROWS, keys.size())))
      {
         Integer             typeId = typeIds.get(key);
         Map<String, Object> row    = new HashMap<>();
         String              label  = typeId != null && labels.containsKey(typeId) ? labels.get(typeId) : key;
         row.put("type", typeId == null ? HomeHtml.escape(label) : HomeHtml.link("/app/" + AlertsType.TABLE_NAME + "/" + typeId, label));
         row.put("opened", HomeHtml.number(counts.get(key)[0]));
         row.put("reopened", HomeHtml.number(counts.get(key)[1]));
         row.put("flapping", HomeHtml.number(counts.get(key)[2]));
         rows.add(row);
      }
      return (table(List.of(column("Alert Type", "type"), column("Opened", "opened"), column("Reopened", "reopened"), column("Flapping", "flapping")), rows,
         "Nothing was noisy in the last 7 days."));
   }



   /*******************************************************************************
    ** Notifications, link-only (the Notifications table is the owner):
    ** scheduled, sent this week, drafts.
    *******************************************************************************/
   static QWidgetData notifications(Instant now) throws QException
   {
      if(!canRead(AlertsNotification.TABLE_NAME))
      {
         return (new RawHTML(null, HomeHtml.emptyState("Notifications are shown to notification authors.")));
      }
      QQueryFilter scheduled = AlertsDb.eq("status", NotificationService.STATUS_SCHEDULED);
      QQueryFilter drafts    = AlertsDb.eq("status", NotificationService.STATUS_DRAFT);
      QQueryFilter sent      = AlertsDb.eq("status", NotificationService.STATUS_SENT).withCriteria("category", QCriteriaOperator.NOT_EQUALS, "ALERT")
         .withCriteria("sentAt", QCriteriaOperator.GREATER_THAN_OR_EQUALS, now.minus(7, ChronoUnit.DAYS));
      String line = HomeHtml.link(listUrl(AlertsNotification.TABLE_NAME, scheduled), HomeHtml.plural(AlertsDb.count(AlertsNotification.TABLE_NAME, scheduled), "scheduled notification"))
         + " &middot; " + HomeHtml.link(listUrl(AlertsNotification.TABLE_NAME, sent), HomeHtml.number(AlertsDb.count(AlertsNotification.TABLE_NAME, sent)) + " sent this week")
         + " &middot; " + HomeHtml.link(listUrl(AlertsNotification.TABLE_NAME, drafts), HomeHtml.plural(AlertsDb.count(AlertsNotification.TABLE_NAME, drafts), "draft"));
      return (new RawHTML(null, "<p style=\"margin:0\">" + line + "</p>"));
   }



   /*******************************************************************************
    ** Delivery health (alerts admins): unhealthy channels and failed or dead
    ** deliveries in 24 hours; one line when all is well.
    *******************************************************************************/
   static QWidgetData delivery(Instant now) throws QException
   {
      if(!canRead(AlertsChannel.TABLE_NAME) || !canRead(AlertsDelivery.TABLE_NAME))
      {
         return (new RawHTML(null, HomeHtml.emptyState("Delivery health is shown to alerts admins.")));
      }
      List<QRecord>             channels = AlertsDb.query(AlertsChannel.TABLE_NAME, new QQueryFilter().withOrderBy(new QFilterOrderBy("label")));
      Instant                   since    = now.minus(24, ChronoUnit.HOURS);
      List<Map<String, Object>> rows     = new ArrayList<>();
      for(QRecord channel : channels)
      {
         int failed = AlertsDb.count(AlertsDelivery.TABLE_NAME, AlertsDb.eq("channelId", channel.getValue("id"))
            .withCriteria("status", QCriteriaOperator.IN, List.of(DeliveryStatus.FAILED.name(), DeliveryStatus.DEAD.name()))
            .withCriteria("createDate", QCriteriaOperator.GREATER_THAN_OR_EQUALS, since));
         String health = channel.getValueString("healthStatus");
         if(failed == 0 && (health == null || HealthStatus.HEALTHY.name().equals(health)))
         {
            continue;
         }
         Map<String, Object> row = new HashMap<>();
         row.put("channel", HomeHtml.link("/app/" + AlertsChannel.TABLE_NAME + "/" + channel.getValue("id"), channel.getValueString("label")));
         row.put("health", HomeHtml.badge(AlertsPossibleValues.label(health == null ? "HEALTHY" : health), HealthStatus.UNHEALTHY.name().equals(health) ? HomeHtml.BAD_COLOR : HomeHtml.WARN_COLOR));
         row.put("failed", HomeHtml.link(listUrl(AlertsDelivery.TABLE_NAME, AlertsDb.eq("channelId", channel.getValue("id"))
            .withCriteria("status", QCriteriaOperator.IN, List.of(DeliveryStatus.FAILED.name(), DeliveryStatus.DEAD.name()))), HomeHtml.number(failed)));
         row.put("error", HomeHtml.escape(StringUtils.hasContent(channel.getValueString("lastError")) ? StringUtils.safeTruncate(channel.getValueString("lastError"), 120, "…") : ""));
         rows.add(row);
      }
      if(rows.isEmpty())
      {
         return (new RawHTML(null, HomeHtml.emptyState(channels.isEmpty() ? "No channels yet." : "All " + HomeHtml.plural(channels.size(), "channel") + " delivered normally in the last 24 hours.")));
      }
      return (table(List.of(column("Channel", "channel"), column("Health", "health"), column("Failed, 24 h", "failed"), column("Last Error", "error")), rows, ""));
   }



   /*******************************************************************************
    ** Recently resolved: the newest 5, how and by whom, and how long they ran.
    *******************************************************************************/
   static QWidgetData resolved(Instant now) throws QException
   {
      List<QRecord> recent = AlertsDb.query(AlertsAlert.TABLE_NAME, AlertsDb.eq("status", Status.RESOLVED.name()).withOrderBy(new QFilterOrderBy("resolvedAt", false)).withLimit(5));
      List<Map<String, Object>> rows = new ArrayList<>();
      for(QRecord alert : recent)
      {
         Map<String, Object> row = new HashMap<>();
         row.put("alert", alertCell(alert));
         String how = AlertsPossibleValues.label(alert.getValueString("resolution"));
         String who = alert.getValueString("resolvedByUserId");
         row.put("resolved", HomeHtml.escape((how == null ? "" : how) + (who == null ? "" : " by " + who) + ", " + AlertMessageRenderer.age(alert.getValueInstant("resolvedAt"), now) + " ago"));
         row.put("duration", HomeHtml.escape(duration(alert.getValueInstant("firstRaisedAt"), alert.getValueInstant("resolvedAt"))));
         rows.add(row);
      }
      TableData table = table(List.of(column("Alert", "alert"), column("Resolved", "resolved"), column("Duration", "duration")), rows, "No alerts have been resolved yet.");
      return (table.withLinkText("View all").withLinkURL(listUrl(AlertsDb.eq("status", Status.RESOLVED.name()))));
   }



   /*******************************************************************************
    ** Mean time to acknowledge and to resolve, for alerts opened since.
    *******************************************************************************/
   private static String meanTimes(Instant since) throws QException
   {
      long ackTotal = 0;
      long ackCount = 0;
      long resTotal = 0;
      long resCount = 0;
      for(QRecord alert : AlertsDb.query(AlertsAlert.TABLE_NAME, new QQueryFilter().withCriteria("firstRaisedAt", QCriteriaOperator.GREATER_THAN_OR_EQUALS, since).withLimit(SCAN_LIMIT)))
      {
         Instant first = alert.getValueInstant("firstRaisedAt");
         if(first == null)
         {
            continue;
         }
         if(alert.getValueInstant("acknowledgedAt") != null)
         {
            ackTotal += Duration.between(first, alert.getValueInstant("acknowledgedAt")).toMinutes();
            ackCount++;
         }
         if(alert.getValueInstant("resolvedAt") != null)
         {
            resTotal += Duration.between(first, alert.getValueInstant("resolvedAt")).toMinutes();
            resCount++;
         }
      }
      String mtta = ackCount == 0 ? "none acknowledged" : minutes(ackTotal / ackCount);
      String mttr = resCount == 0 ? "none resolved" : minutes(resTotal / resCount);
      return ("Opened and resolved per day. Mean time to acknowledge: " + mtta + ". Mean time to resolve: " + mttr + ".");
   }



   /*******************************************************************************
    ** Open alerts the viewer can see.
    *******************************************************************************/
   static List<QRecord> openAlerts() throws QException
   {
      return (AlertsDb.query(AlertsAlert.TABLE_NAME, new QQueryFilter(new QFilterCriteria("status", QCriteriaOperator.IN, Status.openNames())).withLimit(SCAN_LIMIT)));
   }



   /*******************************************************************************
    ** A four-column alert table: Alert, Severity, Age, and one more.
    *******************************************************************************/
   private static TableData alertTable(List<QRecord> alerts, Instant now, String lastHeader, java.util.function.Function<QRecord, String> last, String empty)
   {
      List<Map<String, Object>> rows = new ArrayList<>();
      for(QRecord alert : alerts)
      {
         Map<String, Object> row = new HashMap<>();
         row.put("alert", alertCell(alert));
         row.put("severity", severityBadge(alert.getValueString("severity")));
         row.put("age", HomeHtml.escape(AlertMessageRenderer.age(alert.getValueInstant("firstRaisedAt"), now)));
         row.put("last", last.apply(alert));
         rows.add(row);
      }
      return (table(List.of(column("Alert", "alert"), column("Severity", "severity"), column("Age", "age"), column(lastHeader, "last")), rows, empty));
   }



   /*******************************************************************************
    ** The alert's title, linked, with its type as a muted fact.
    *******************************************************************************/
   static String alertCell(QRecord alert)
   {
      return (HomeHtml.card(HomeHtml.link("/app/" + AlertsAlert.TABLE_NAME + "/" + alert.getValue("id"), alert.getValueString("title")),
         alert.getValue("itemCount") == null ? null : HomeHtml.escape(HomeHtml.plural(alert.getValueInteger("itemCount"), "record"))));
   }



   /*******************************************************************************
    ** A severity pill.
    *******************************************************************************/
   public static String severityBadge(String severity)
   {
      String color = switch(severity == null ? "" : severity)
      {
         case "CRITICAL", "ERROR" -> HomeHtml.BAD_COLOR;
         case "WARNING" -> HomeHtml.WARN_COLOR;
         default -> HomeHtml.INFO_COLOR;
      };
      return (HomeHtml.badge(severity == null ? "" : AlertsPossibleValues.label(severity), color));
   }



   /*******************************************************************************
    ** A status pill.
    *******************************************************************************/
   private static String statusBadge(QRecord alert)
   {
      String status = alert.getValueString("status");
      return (HomeHtml.badge(AlertsPossibleValues.label(status), Status.OPEN.name().equals(status) ? HomeHtml.BAD_COLOR : HomeHtml.OFF_COLOR));
   }



   /*******************************************************************************
    ** The assignee, team, or "Unassigned".
    *******************************************************************************/
   private static String owner(QRecord alert)
   {
      if(alert.getValueString("assignedUserId") != null)
      {
         return (HomeHtml.escape(alert.getValueString("assignedUserId")));
      }
      if(alert.getValue("assignedTeamId") != null)
      {
         return (HomeHtml.escape("Team " + alert.getValue("assignedTeamId")));
      }
      return (HomeHtml.muted("Unassigned"));
   }



   /*******************************************************************************
    ** Most severe first, then oldest first.
    *******************************************************************************/
   static int bySeverityThenAge(QRecord a, QRecord b)
   {
      int severity = AlertSeverity.fromLabel(b.getValueString("severity"), AlertSeverity.INFO).compareTo(AlertSeverity.fromLabel(a.getValueString("severity"), AlertSeverity.INFO));
      if(severity != 0)
      {
         return (severity);
      }
      Instant aFirst = a.getValueInstant("firstRaisedAt");
      Instant bFirst = b.getValueInstant("firstRaisedAt");
      if(aFirst == null || bFirst == null)
      {
         return (0);
      }
      return (aFirst.compareTo(bFirst));
   }



   /*******************************************************************************
    ** A 7-day block sparkline.
    *******************************************************************************/
   static String sparkline(int[] counts)
   {
      String blocks = "▁▂▃▄▅▆▇█";
      int    max    = 0;
      for(int count : counts)
      {
         max = Math.max(max, count);
      }
      StringBuilder line = new StringBuilder();
      for(int count : counts)
      {
         line.append(max == 0 ? blocks.charAt(0) : blocks.charAt(Math.min(7, (int) Math.round(7.0 * count / max))));
      }
      return ("<span aria-label=\"opened per day\" style=\"font-family:monospace;letter-spacing:1px\">" + line + "</span>");
   }



   /*******************************************************************************
    ** Labels by id for a small table.
    *******************************************************************************/
   private static Map<Integer, String> labels(String tableName, String field) throws QException
   {
      Map<Integer, String> labels = new HashMap<>();
      if(canRead(tableName))
      {
         for(QRecord row : AlertsDb.query(tableName, new QQueryFilter().withLimit(SCAN_LIMIT)))
         {
            labels.put(row.getValueInteger("id"), row.getValueString(field));
         }
      }
      return (labels);
   }



   /*******************************************************************************
    ** An alert by id in the viewer's session (null when not visible).
    *******************************************************************************/
   private static QRecord quietly(Integer id)
   {
      try
      {
         return (AlertsDb.byId(AlertsAlert.TABLE_NAME, id));
      }
      catch(QException e)
      {
         return (null);
      }
   }



   /*******************************************************************************
    ** Whether the viewer may read a table.
    *******************************************************************************/
   static boolean canRead(String tableName)
   {
      return (PermissionsHelper.hasTablePermission(new AbstractActionInput(), tableName, TablePermissionSubType.READ));
   }



   /*******************************************************************************
    ** The Alerts list filtered.
    *******************************************************************************/
   public static String listUrl(QQueryFilter filter)
   {
      return (listUrl(AlertsAlert.TABLE_NAME, filter));
   }



   /*******************************************************************************
    ** A table's list, filtered.
    *******************************************************************************/
   static String listUrl(String tableName, QQueryFilter filter)
   {
      return ("/app/" + tableName + "?filter=" + URLEncoder.encode(JsonUtils.toJson(filter), StandardCharsets.UTF_8));
   }



   /*******************************************************************************
    ** "2 h 5 min", "3 d".
    *******************************************************************************/
   static String duration(Instant from, Instant to)
   {
      if(from == null || to == null)
      {
         return ("");
      }
      return (minutes(Duration.between(from, to).toMinutes()));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String minutes(long minutes)
   {
      if(minutes < 60)
      {
         return (minutes + " min");
      }
      if(minutes < 48 * 60)
      {
         return ((minutes / 60) + " h" + (minutes % 60 == 0 ? "" : " " + (minutes % 60) + " min"));
      }
      return ((minutes / 1440) + " d");
   }



   /*******************************************************************************
    ** A table column of HTML cells.
    *******************************************************************************/
   private static TableData.Column column(String header, String accessor)
   {
      return (new TableData.Column("html", header, accessor, null, "left").withVerticalAlign("top"));
   }



   /*******************************************************************************
    ** A table without pagination controls, with an honest empty state.
    *******************************************************************************/
   private static TableData table(List<TableData.Column> columns, List<Map<String, Object>> rows, String empty)
   {
      return (new TableData(null, columns, rows)
         .withRowsPerPage(Math.max(ROWS, rows.size()))
         .withHidePaginationDropdown(true)
         .withNoRowsFoundHTML(HomeHtml.emptyState(empty)));
   }



   /*******************************************************************************
    ** Every widget kind.
    *******************************************************************************/
   static Set<String> kinds()
   {
      return (Set.of(KIND_SEVERITY, KIND_MINE, KIND_ACTION, KIND_BY_GROUP, KIND_TREND, KIND_NOISY, KIND_DELIVERY, KIND_RESOLVED));
   }
}
