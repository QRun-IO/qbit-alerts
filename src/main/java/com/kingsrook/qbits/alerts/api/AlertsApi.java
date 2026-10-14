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

package com.kingsrook.qbits.alerts.api;


import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.alerts.AlertsPreferences;
import com.kingsrook.qbits.alerts.AlertsQBitConfig;
import com.kingsrook.qbits.alerts.AlertsQBitProducer;
import com.kingsrook.qbits.alerts.AlertsRuntime;
import com.kingsrook.qbits.alerts.AlertsSecurity;
import com.kingsrook.qbits.alerts.delivery.AlertMessageRenderer;
import com.kingsrook.qbits.alerts.engine.AlertsDb;
import com.kingsrook.qbits.alerts.home.AlertsHome;
import com.kingsrook.qbits.alerts.model.AlertsAlert;
import com.kingsrook.qbits.alerts.model.AlertsGroup;
import com.kingsrook.qbits.alerts.model.AlertsNotification;
import com.kingsrook.qbits.alerts.model.AlertsNotificationRecipient;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.ScopeType;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.Status;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.SubscriberType;
import com.kingsrook.qbits.alerts.model.AlertsRecordRef;
import com.kingsrook.qbits.alerts.model.AlertsSubscription;
import com.kingsrook.qbits.alerts.notifications.NotificationService;
import com.kingsrook.qbits.alerts.spi.AlertSeverity;
import com.kingsrook.qbits.alerts.spi.NotificationCategory;
import com.kingsrook.qbits.alerts.spi.NotificationSeverity;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper;
import com.kingsrook.qqq.backend.core.actions.permissions.TablePermissionSubType;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** The bell, notifications page and record-panel endpoints' logic (design
 ** sections 6.3, 6.4; docs/NOTIFICATIONS.md 8.1),
 ** independent of the HTTP layer. Everything is read and written in the
 ** caller's session (record locks: their own recipient rows, alerts they can
 ** see); notification content is read for the rows they hold.
 *******************************************************************************/
public class AlertsApi
{
   static final int INBOX_ITEMS        = 10;
   static final int MAX_BANNERS        = 3;
   static final int SCAN_LIMIT         = 1000;
   static final int MAX_BATCH          = 500;
   static final int DEFAULT_PAGE       = 25;
   static final int MAX_PAGE           = 100;
   static final int POLL_SECONDS       = 60;

   public static final String NOTIFICATIONS_HREF = "/app/notifications";
   static final int PANEL_ROWS         = 5;
   static final int RESOLVED_LOOKBACK  = 30;

   private final AlertsRuntime runtime;



   /*******************************************************************************
    ** Constructor
    *******************************************************************************/
   public AlertsApi(AlertsRuntime runtime)
   {
      this.runtime = runtime;
   }



   /*******************************************************************************
    ** Check alerts.view and set up the session (keys, settings).
    *******************************************************************************/
   public String requireViewer() throws QException
   {
      QSession session = QContext.getQSession();
      if(session == null || !session.hasPermission(AlertsQBitConfig.PERMISSION_VIEW))
      {
         throw (new QPermissionDeniedException("Alerts are not available to you."));
      }
      AlertsSecurity.ensureSessionKeys(runtime.getQInstance(), session);
      AlertsPreferences.ensure(runtime, session);
      return (AlertsSecurity.userId(session));
   }



   /*******************************************************************************
    ** The bell (docs/NOTIFICATIONS.md 8.1): the unread count, the highest
    ** unread severity, the latest items, and sticky banners.
    *******************************************************************************/
   public Map<String, Object> inboxSummary() throws QException
   {
      String       userId  = requireViewer();
      Instant      now     = runtime.now();
      List<Entry>  entries = entries(userId, now);

      int                       unread  = 0;
      NotificationSeverity      highest = null;
      List<Map<String, Object>> latest  = new ArrayList<>();
      List<Map<String, Object>> banners = new ArrayList<>();
      for(Entry entry : entries)
      {
         if(entry.unread())
         {
            unread++;
            NotificationSeverity severity = NotificationSeverity.fromName(entry.notification().getValueString("severity"), NotificationSeverity.INFO);
            highest = highest == null || severity.compareTo(highest) > 0 ? severity : highest;
         }
         if(latest.size() < INBOX_ITEMS)
         {
            latest.add(item(entry, now, false));
         }
         if(Boolean.TRUE.equals(entry.notification().getValueBoolean("sticky")) && banners.size() < MAX_BANNERS)
         {
            banners.add(item(entry, now, true));
         }
      }

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("unreadCount", unread);
      result.put("highestUnreadSeverity", highest == null ? null : highest.name());
      result.put("items", latest);
      result.put("banners", banners);
      result.put("notificationsHref", NOTIFICATIONS_HREF);
      result.put("preferencesSection", AlertsQBitProducer.preferencesSectionName(QContext.getQInstance()));
      result.put("alertsHref", "/app/" + AlertsQBitConfig.APP_NAME);
      result.put("pollSeconds", POLL_SECONDS);
      return (result);
   }



   /*******************************************************************************
    ** The notifications page: the user's notifications (not dismissed),
    ** filtered by status (all or unread), category and severity, a page at a
    ** time, with bodies.
    *******************************************************************************/
   public Map<String, Object> inbox(Map<String, String> params) throws QException
   {
      String  userId   = requireViewer();
      Instant now      = runtime.now();
      boolean onlyNew  = "unread".equalsIgnoreCase(params.get("status"));
      String  category = StringUtils.hasContent(params.get("category")) ? params.get("category").toUpperCase(Locale.ROOT) : null;
      String  severity = StringUtils.hasContent(params.get("severity")) ? params.get("severity").toUpperCase(Locale.ROOT) : null;
      int     offset   = Math.max(0, parseInt(params.get("offset"), 0));
      int     limit    = Math.min(MAX_PAGE, Math.max(1, parseInt(params.get("limit"), DEFAULT_PAGE)));

      List<Entry> matching = new ArrayList<>();
      for(Entry entry : entries(userId, now))
      {
         if((!onlyNew || entry.unread())
            && (category == null || category.equals(entry.notification().getValueString("category")))
            && (severity == null || severity.equals(entry.notification().getValueString("severity"))))
         {
            matching.add(entry);
         }
      }

      List<Map<String, Object>> items = new ArrayList<>();
      for(Entry entry : matching.subList(Math.min(offset, matching.size()), Math.min(offset + limit, matching.size())))
      {
         items.add(item(entry, now, true));
      }
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("items", items);
      result.put("total", matching.size());
      result.put("offset", offset);
      result.put("limit", limit);
      return (result);
   }



   /*******************************************************************************
    ** Mark read: {"id": n}, {"ids": [..]}, {"all": true}, or (0.1 clients)
    ** {"alertId": n}.
    *******************************************************************************/
   public Map<String, Object> markRead(Map<String, Object> body) throws QException
   {
      String userId = requireViewer();
      Instant now   = runtime.now();
      return (updateOwn(userId, target(userId, body, true), "readAt", now, record -> record.getValue("readAt") == null));
   }



   /*******************************************************************************
    ** Mark unread: {"id": n} or {"ids": [..]}.
    *******************************************************************************/
   public Map<String, Object> markUnread(Map<String, Object> body) throws QException
   {
      String userId = requireViewer();
      return (updateOwn(userId, target(userId, body, false), "readAt", null, record -> record.getValue("readAt") != null));
   }



   /*******************************************************************************
    ** Dismiss (hide from the bell and the page; also marks read): {"id": n},
    ** {"ids": [..]} or {"all": true}.
    *******************************************************************************/
   public Map<String, Object> dismiss(Map<String, Object> body) throws QException
   {
      String userId = requireViewer();
      Instant now   = runtime.now();
      return (updateOwn(userId, target(userId, body, true), "dismissedAt", now, record -> record.getValue("dismissedAt") == null));
   }



   /*******************************************************************************
    ** The open alerts in a user's bell (ALERT notifications not dismissed),
    ** for the Alerts home's "My alerts".
    *******************************************************************************/
   public static List<Integer> myAlertIds(String userId, int limit) throws QException
   {
      List<Integer> notificationIds = new ArrayList<>();
      for(QRecord recipient : AlertsDb.query(AlertsNotificationRecipient.TABLE_NAME, AlertsDb.eq("userId", userId)
         .withCriteria("dismissedAt", QCriteriaOperator.IS_BLANK).withLimit(limit)))
      {
         notificationIds.add(recipient.getValueInteger("notificationId"));
      }
      List<Integer> alertIds = new ArrayList<>();
      if(notificationIds.isEmpty())
      {
         return (alertIds);
      }
      for(QRecord notification : AlertsRuntime.get().asSystem(() -> AlertsDb.query(AlertsNotification.TABLE_NAME, new QQueryFilter()
         .withCriteria("id", QCriteriaOperator.IN, notificationIds)
         .withCriteria("category", QCriteriaOperator.EQUALS, NotificationCategory.ALERT.name())
         .withCriteria("status", QCriteriaOperator.EQUALS, NotificationService.STATUS_SENT))))
      {
         Integer alertId = parseInteger(notification.getValueString("sourceRecordId"));
         if(alertId != null)
         {
            alertIds.add(alertId);
         }
      }
      return (alertIds);
   }



   /*******************************************************************************
    ** One recipient row with its (live) notification.
    *******************************************************************************/
   record Entry(QRecord recipient, QRecord notification)
   {
      /***************************************************************************
       **
       ***************************************************************************/
      boolean unread()
      {
         return (recipient.getValue("readAt") == null);
      }
   }



   /*******************************************************************************
    ** The user's bell: their recipient rows (the owner lock applies) that are
    ** not dismissed, joined with notifications that are SENT, published and
    ** not expired, newest first. ALERT notifications are kept only while the
    ** user can still see the alert.
    *******************************************************************************/
   private List<Entry> entries(String userId, Instant now) throws QException
   {
      if(userId == null)
      {
         return (List.of());
      }
      List<QRecord> recipients = AlertsDb.query(AlertsNotificationRecipient.TABLE_NAME, AlertsDb.eq("userId", userId)
         .withCriteria("dismissedAt", QCriteriaOperator.IS_BLANK)
         .withOrderBy(new QFilterOrderBy("deliveredAt", false))
         .withOrderBy(new QFilterOrderBy("id", false))
         .withLimit(SCAN_LIMIT));
      if(recipients.isEmpty())
      {
         return (List.of());
      }

      List<Integer>         ids           = recipients.stream().map(recipient -> recipient.getValueInteger("notificationId")).distinct().toList();
      Map<Integer, QRecord> notifications = new HashMap<>();
      runtime.asSystem(() ->
      {
         for(QRecord notification : AlertsDb.query(AlertsNotification.TABLE_NAME, new QQueryFilter()
            .withCriteria("id", QCriteriaOperator.IN, ids)
            .withCriteria("status", QCriteriaOperator.EQUALS, NotificationService.STATUS_SENT)))
         {
            Instant publishAt = notification.getValueInstant("publishAt");
            Instant expireAt  = notification.getValueInstant("expireAt");
            if((publishAt == null || !publishAt.isAfter(now)) && (expireAt == null || expireAt.isAfter(now)))
            {
               notifications.put(notification.getValueInteger("id"), notification);
            }
         }
         return (null);
      });

      List<Integer> alertIds = new ArrayList<>();
      for(QRecord notification : notifications.values())
      {
         if(NotificationCategory.ALERT.name().equals(notification.getValueString("category")) && parseInteger(notification.getValueString("sourceRecordId")) != null)
         {
            alertIds.add(parseInteger(notification.getValueString("sourceRecordId")));
         }
      }
      Set<String> visibleAlerts = new HashSet<>();
      if(!alertIds.isEmpty())
      {
         for(QRecord alert : AlertsDb.query(AlertsAlert.TABLE_NAME, new QQueryFilter().withCriteria("id", QCriteriaOperator.IN, alertIds)))
         {
            visibleAlerts.add(String.valueOf(alert.getValue("id")));
         }
      }

      List<Entry> entries = new ArrayList<>();
      for(QRecord recipient : recipients)
      {
         QRecord notification = notifications.get(recipient.getValueInteger("notificationId"));
         if(notification != null && (!NotificationCategory.ALERT.name().equals(notification.getValueString("category")) || visibleAlerts.contains(notification.getValueString("sourceRecordId"))))
         {
            entries.add(new Entry(recipient, notification));
         }
      }
      return (entries);
   }



   /*******************************************************************************
    ** One bell or page item.
    *******************************************************************************/
   private static Map<String, Object> item(Entry entry, Instant now, boolean withBody)
   {
      QRecord             notification = entry.notification();
      Instant             deliveredAt  = entry.recipient().getValueInstant("deliveredAt");
      Map<String, Object> row          = new LinkedHashMap<>();
      row.put("id", entry.recipient().getValueInteger("id"));
      row.put("notificationId", notification.getValueInteger("id"));
      row.put("title", notification.getValueString("title"));
      if(withBody)
      {
         row.put("body", notification.getValueString("body"));
      }
      row.put("severity", notification.getValueString("severity"));
      row.put("category", notification.getValueString("category"));
      row.put("source", notification.getValueString("sourceName"));
      row.put("href", NotificationService.safeLink(notification.getValueString("linkUrl")));
      row.put("linkLabel", notification.getValueString("linkLabel"));
      row.put("deliveredAt", deliveredAt == null ? null : deliveredAt.toString());
      row.put("age", AlertMessageRenderer.age(deliveredAt, now));
      row.put("unread", entry.unread());
      row.put("sticky", Boolean.TRUE.equals(notification.getValueBoolean("sticky")));
      if(NotificationCategory.ALERT.name().equals(notification.getValueString("category")))
      {
         row.put("alertId", parseInteger(notification.getValueString("sourceRecordId")));
      }
      return (row);
   }



   /*******************************************************************************
    ** Which of the user's recipient rows a request names (null: all of them).
    *******************************************************************************/
   private List<Integer> target(String userId, Map<String, Object> body, boolean allowAll) throws QException
   {
      if(body != null && body.get("id") != null)
      {
         return (List.of(toInteger(body.get("id"))));
      }
      if(body != null && body.get("ids") instanceof List<?> list && !list.isEmpty())
      {
         List<Integer> ids = new ArrayList<>();
         for(Object value : list.subList(0, Math.min(list.size(), MAX_BATCH)))
         {
            ids.add(toInteger(value));
         }
         return (ids);
      }
      if(body != null && body.get("alertId") != null)
      {
         Integer alertId      = toInteger(body.get("alertId"));
         QRecord notification = runtime.asSystem(() -> AlertsDb.first(AlertsNotification.TABLE_NAME, AlertsDb.eq("sourceKey", "alert:" + alertId)));
         if(notification == null || userId == null)
         {
            return (List.of());
         }
         return (AlertsDb.query(AlertsNotificationRecipient.TABLE_NAME, AlertsDb.eq("userId", userId)
            .withCriteria("notificationId", QCriteriaOperator.EQUALS, notification.getValue("id"))).stream().map(recipient -> recipient.getValueInteger("id")).toList());
      }
      if(allowAll && body != null && Boolean.TRUE.equals(body.get("all")))
      {
         return (null);
      }
      throw (new QBadRequestException(allowAll ? "Send id, ids, or all: true." : "Send id or ids."));
   }



   /*******************************************************************************
    ** Set one field on the user's own recipient rows (the named ones, or all
    ** not dismissed), where the row still needs it. Dismissing also marks
    ** read. Recounts the notifications' readers.
    *******************************************************************************/
   private Map<String, Object> updateOwn(String userId, List<Integer> ids, String field, Instant value, java.util.function.Predicate<QRecord> needs) throws QException
   {
      if(userId == null || (ids != null && ids.isEmpty()))
      {
         return (Map.of("updated", 0));
      }
      QQueryFilter filter = AlertsDb.eq("userId", userId).withLimit(SCAN_LIMIT);
      if(ids != null)
      {
         filter.withCriteria("id", QCriteriaOperator.IN, ids);
      }
      else
      {
         filter.withCriteria("dismissedAt", QCriteriaOperator.IS_BLANK);
      }

      List<QRecord> updates  = new ArrayList<>();
      Set<Integer>  affected = new HashSet<>();
      for(QRecord recipient : AlertsDb.query(AlertsNotificationRecipient.TABLE_NAME, filter))
      {
         if(needs.test(recipient))
         {
            QRecord update = new QRecord().withValue("id", recipient.getValue("id")).withValue(field, value);
            if("dismissedAt".equals(field) && recipient.getValue("readAt") == null)
            {
               update.setValue("readAt", value);
            }
            updates.add(update);
            affected.add(recipient.getValueInteger("notificationId"));
         }
      }
      if(!updates.isEmpty())
      {
         UpdateAction.executeForRecords(new UpdateInput(AlertsNotificationRecipient.TABLE_NAME).withRecords(updates));
         runtime.getNotifications().recountRead(affected);
      }
      return (Map.of("updated", updates.size()));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Integer toInteger(Object value) throws QBadRequestException
   {
      Integer parsed = value == null ? null : parseInteger(String.valueOf(value));
      if(parsed == null)
      {
         throw (new QBadRequestException("Ids must be numbers."));
      }
      return (parsed);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Integer parseInteger(String value)
   {
      try
      {
         return (value == null ? null : Integer.valueOf(value.trim().replaceAll("\\.0+$", "")));
      }
      catch(NumberFormatException e)
      {
         return (null);
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static int parseInt(String value, int fallback)
   {
      Integer parsed = parseInteger(value);
      return (parsed == null ? fallback : parsed);
   }



   /*******************************************************************************
    ** The "Open alerts" panel for a record: visible open alerts that reference
    ** it (at most 5), how many were resolved in 30 days, and links. visible is
    ** false when there is nothing to show (the panel is then hidden).
    *******************************************************************************/
   public Map<String, Object> recordPanel(String tableName, String recordId) throws QException
   {
      requireViewer();
      if(!StringUtils.hasContent(tableName) || !StringUtils.hasContent(recordId) || runtime.getQInstance().getTable(tableName) == null)
      {
         throw (new QBadRequestException("Send a table name and record id."));
      }
      if(!PermissionsHelper.hasTablePermission(new AbstractActionInput(), tableName, TablePermissionSubType.READ))
      {
         throw (new QPermissionDeniedException("You can't read that table."));
      }

      Instant       now     = runtime.now();
      List<Integer> openIds = new ArrayList<>();
      List<Integer> allIds  = new ArrayList<>();
      for(QRecord ref : AlertsDb.query(AlertsRecordRef.TABLE_NAME, AlertsDb.eq("tableName", tableName).withCriteria("recordId", QCriteriaOperator.EQUALS, recordId).withLimit(500)))
      {
         allIds.add(ref.getValueInteger("alertId"));
         if(Boolean.TRUE.equals(ref.getValueBoolean("active")))
         {
            openIds.add(ref.getValueInteger("alertId"));
         }
      }
      for(QRecord alert : AlertsDb.query(AlertsAlert.TABLE_NAME, AlertsDb.eq("subjectTableName", tableName).withCriteria("subjectRecordId", QCriteriaOperator.EQUALS, recordId).withLimit(500)))
      {
         allIds.add(alert.getValueInteger("id"));
         openIds.add(alert.getValueInteger("id"));
      }

      List<QRecord> open = openIds.isEmpty() ? new ArrayList<>() : new ArrayList<>(AlertsDb.query(AlertsAlert.TABLE_NAME, new QQueryFilter()
         .withCriteria("id", QCriteriaOperator.IN, openIds)
         .withCriteria("status", QCriteriaOperator.IN, Status.openNames())));
      open.sort((a, b) -> AlertSeverity.fromLabel(b.getValueString("severity"), AlertSeverity.INFO).compareTo(AlertSeverity.fromLabel(a.getValueString("severity"), AlertSeverity.INFO)));
      int resolved = allIds.isEmpty() ? 0 : AlertsDb.count(AlertsAlert.TABLE_NAME, new QQueryFilter()
         .withCriteria("id", QCriteriaOperator.IN, allIds)
         .withCriteria("status", QCriteriaOperator.EQUALS, Status.RESOLVED.name())
         .withCriteria("resolvedAt", QCriteriaOperator.GREATER_THAN_OR_EQUALS, now.minus(RESOLVED_LOOKBACK, ChronoUnit.DAYS)));

      Map<Integer, String>      groups = groupLabels();
      List<Map<String, Object>> rows   = new ArrayList<>();
      for(QRecord alert : open.subList(0, Math.min(PANEL_ROWS, open.size())))
      {
         rows.add(alertRow(alert, groups, now));
      }

      QSession session = QContext.getQSession();
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("visible", !open.isEmpty() || resolved > 0);
      result.put("openCount", open.size());
      result.put("open", rows);
      result.put("resolvedLast30Days", resolved);
      result.put("viewAllHref", allIds.isEmpty() ? null : AlertsHome.listUrl(new QQueryFilter().withCriteria("id", QCriteriaOperator.IN, dedupe(allIds))));
      result.put("canManage", session.hasPermission(AlertsQBitConfig.PERMISSION_MANAGE));
      result.put("watching", isWatching(tableName, recordId));
      return (result);
   }



   /*******************************************************************************
    ** "Watch alerts for this record": a RECORD-scope subscription for the user.
    *******************************************************************************/
   public Map<String, Object> watchRecord(Map<String, Object> body) throws QException
   {
      String userId    = requireViewer();
      String tableName = body == null ? null : (String) body.get("tableName");
      Object recordId  = body == null ? null : body.get("recordId");
      if(userId == null || !StringUtils.hasContent(tableName) || recordId == null || runtime.getQInstance().getTable(tableName) == null)
      {
         throw (new QBadRequestException("Send a table name and record id."));
      }
      if(isWatching(tableName, String.valueOf(recordId)))
      {
         return (Map.of("watching", true, "created", false));
      }
      List<QRecord> inserted = InsertAction.executeForRecords(new InsertInput(AlertsSubscription.TABLE_NAME).withRecord(new QRecord()
         .withValue("subscriberType", SubscriberType.USER.name())
         .withValue("userId", userId)
         .withValue("scopeType", ScopeType.RECORD.name())
         .withValue("scopeValue", tableName)
         .withValue("scopeRecordId", String.valueOf(recordId))
         .withValue("minSeverity", AlertSeverity.WARNING.name())
         .withValue("events", "RAISED,ESCALATED,REMINDER,RESOLVED")
         .withValue("deliveryMode", "IMMEDIATE")
         .withValue("enabled", true)));
      if(CollectionUtils.nullSafeHasContents(inserted.get(0).getErrors()))
      {
         throw (new QBadRequestException(inserted.get(0).getErrors().get(0).getMessage()));
      }
      return (Map.of("watching", true, "created", true, "subscriptionId", inserted.get(0).getValue("id")));
   }



   /*******************************************************************************
    ** Whether the user already has a RECORD subscription for this record.
    *******************************************************************************/
   private boolean isWatching(String tableName, String recordId) throws QException
   {
      String userId = AlertsSecurity.userId(QContext.getQSession());
      if(userId == null || !PermissionsHelper.hasTablePermission(new AbstractActionInput(), AlertsSubscription.TABLE_NAME, TablePermissionSubType.READ))
      {
         return (false);
      }
      return (AlertsDb.count(AlertsSubscription.TABLE_NAME, AlertsDb.eq("userId", userId)
         .withCriteria("scopeType", QCriteriaOperator.EQUALS, ScopeType.RECORD.name())
         .withCriteria("scopeValue", QCriteriaOperator.EQUALS, tableName)
         .withCriteria("scopeRecordId", QCriteriaOperator.EQUALS, recordId)) > 0);
   }



   /*******************************************************************************
    ** One alert as the bell and panel show it.
    *******************************************************************************/
   private static Map<String, Object> alertRow(QRecord alert, Map<Integer, String> groups, Instant now)
   {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("alertId", alert.getValueInteger("id"));
      row.put("title", alert.getValueString("title"));
      row.put("severity", alert.getValueString("severity"));
      row.put("status", alert.getValueString("status"));
      row.put("group", groups.get(alert.getValueInteger("groupId")));
      row.put("age", AlertMessageRenderer.age(alert.getValueInstant("firstRaisedAt"), now));
      row.put("raisedAt", alert.getValueInstant("firstRaisedAt") == null ? null : alert.getValueInstant("firstRaisedAt").toString());
      row.put("href", "/app/" + AlertsAlert.TABLE_NAME + "/" + alert.getValue("id"));
      return (row);
   }



   /*******************************************************************************
    ** Group labels by id (empty without read permission).
    *******************************************************************************/
   private static Map<Integer, String> groupLabels() throws QException
   {
      Map<Integer, String> labels = new HashMap<>();
      if(PermissionsHelper.hasTablePermission(new AbstractActionInput(), AlertsGroup.TABLE_NAME, TablePermissionSubType.READ))
      {
         for(QRecord group : AlertsDb.query(AlertsGroup.TABLE_NAME, new QQueryFilter()))
         {
            labels.put(group.getValueInteger("id"), group.getValueString("label"));
         }
      }
      return (labels);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static List<Serializable> dedupe(List<Integer> ids)
   {
      return (new ArrayList<>(new java.util.LinkedHashSet<>(ids)));
   }
}
