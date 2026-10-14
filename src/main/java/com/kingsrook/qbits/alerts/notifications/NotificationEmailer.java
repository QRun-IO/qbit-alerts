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


package com.kingsrook.qbits.alerts.notifications;


import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.alerts.AlertsQBitConfig;
import com.kingsrook.qbits.alerts.AlertsRuntime;
import com.kingsrook.qbits.alerts.engine.AlertsDb;
import com.kingsrook.qbits.alerts.model.AlertsNotification;
import com.kingsrook.qbits.alerts.model.AlertsNotificationPreference;
import com.kingsrook.qbits.alerts.model.AlertsNotificationRecipient;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.EmailMode;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.EmailStatus;
import com.kingsrook.qbits.alerts.model.AlertsUserPreference;
import com.kingsrook.qqq.backend.core.actions.messaging.SendMessageAction;
import com.kingsrook.qqq.backend.core.alerts.NotificationCategory;
import com.kingsrook.qqq.backend.core.alerts.NotificationSeverity;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.messaging.Content;
import com.kingsrook.qqq.backend.core.model.actions.messaging.MultiParty;
import com.kingsrook.qqq.backend.core.model.actions.messaging.Party;
import com.kingsrook.qqq.backend.core.model.actions.messaging.SendMessageInput;
import com.kingsrook.qqq.backend.core.model.actions.messaging.email.EmailContentRole;
import com.kingsrook.qqq.backend.core.model.actions.messaging.email.EmailPartyRole;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Email for user notifications (docs/NOTIFICATIONS.md section 7), opt-in per
 ** user and category in My Notification Settings:
 ** - IMMEDIATE: one email per notification at or above the minimum severity;
 ** - DAILY_DIGEST: one email at the user's digest hour (in their time zone,
 **   from My Alert Settings) listing the day's unread notifications.
 ** Inert without a messaging provider and a From address. ALERT
 ** notifications are never emailed here (alert subscriptions already email
 ** them). SECURITY emails carry the title and link only. Runs as the system
 ** user from the delivery job.
 *******************************************************************************/
public class NotificationEmailer
{
   private static final QLogger LOG = QLogger.getLogger(NotificationEmailer.class);

   static final int SCAN = 500;

   private final AlertsRuntime runtime;



   /*******************************************************************************
    ** What one run did.
    *******************************************************************************/
   public record Result(int sent, int digests, int skipped, int failed)
   {
   }



   /*******************************************************************************
    ** Constructor
    *******************************************************************************/
   public NotificationEmailer(AlertsRuntime runtime)
   {
      this.runtime = runtime;
   }



   /*******************************************************************************
    ** Whether email can be sent at all.
    *******************************************************************************/
   public boolean isConfigured()
   {
      AlertsQBitConfig config = runtime.getConfig();
      return (StringUtils.hasContent(config.getMessagingProviderName()) && StringUtils.hasContent(config.getFromAddress()));
   }



   /*******************************************************************************
    ** Send what is due: immediate emails, and digests for users at their hour.
    *******************************************************************************/
   public Result run() throws QException
   {
      if(!isConfigured())
      {
         return (new Result(0, 0, 0, 0));
      }
      Instant now = runtime.now();
      return (runtime.asSystem(() ->
      {
         Map<String, Map<String, QRecord>> preferences = new HashMap<>();
         for(QRecord preference : AlertsDb.query(AlertsNotificationPreference.TABLE_NAME, new QQueryFilter()
            .withCriteria("emailMode", QCriteriaOperator.IN, List.of(EmailMode.IMMEDIATE.name(), EmailMode.DAILY_DIGEST.name()))))
         {
            preferences.computeIfAbsent(preference.getValueString("userId"), k -> new HashMap<>()).put(preference.getValueString("category"), preference);
         }
         if(preferences.isEmpty())
         {
            return (new Result(0, 0, 0, 0));
         }

         List<QRecord> recipients = AlertsDb.query(AlertsNotificationRecipient.TABLE_NAME, new QQueryFilter()
            .withCriteria("userId", QCriteriaOperator.IN, new ArrayList<>(preferences.keySet()))
            .withCriteria("emailStatus", QCriteriaOperator.IN, List.of(EmailStatus.NONE.name(), EmailStatus.PENDING.name()))
            .withCriteria("deliveredAt", QCriteriaOperator.GREATER_THAN_OR_EQUALS, now.minus(1, ChronoUnit.DAYS))
            .withLimit(SCAN));
         Map<Integer, QRecord> notifications = notifications(recipients, now);
         Map<String, QRecord>  settings      = userSettings(preferences.keySet());

         int                         sent    = 0;
         int                         skipped = 0;
         int                         failed  = 0;
         Map<String, List<QRecord[]>> digests = new LinkedHashMap<>();
         for(QRecord recipient : recipients)
         {
            QRecord notification = notifications.get(recipient.getValueInteger("notificationId"));
            String  userId       = recipient.getValueString("userId");
            QRecord preference   = notification == null ? null : preferences.get(userId).get(notification.getValueString("category"));
            if(notification == null || preference == null || NotificationCategory.ALERT.name().equals(notification.getValueString("category"))
               || !atLeast(notification.getValueString("severity"), preference.getValueString("emailMinSeverity")))
            {
               mark(recipient, EmailStatus.SKIPPED, null);
               skipped++;
            }
            else if(EmailMode.IMMEDIATE.name().equals(preference.getValueString("emailMode")))
            {
               boolean ok = send(address(settings.get(userId)), notification.getValueString("title"), List.of(notification));
               mark(recipient, ok ? EmailStatus.SENT : EmailStatus.FAILED, ok ? now : null);
               if(ok)
               {
                  sent++;
               }
               else
               {
                  failed++;
               }
            }
            else if(recipient.getValue("readAt") == null)
            {
               digests.computeIfAbsent(userId, k -> new ArrayList<>()).add(new QRecord[] {recipient, notification});
            }
         }

         int digestCount = 0;
         for(Map.Entry<String, List<QRecord[]>> digest : digests.entrySet())
         {
            QRecord setting = settings.get(digest.getKey());
            if(!isDigestHour(setting, now))
            {
               continue;
            }
            List<QRecord> items = digest.getValue().stream().map(pair -> pair[1]).toList();
            boolean       ok    = send(address(setting), items.size() == 1 ? "1 new notification" : items.size() + " new notifications", items);
            for(QRecord[] pair : digest.getValue())
            {
               mark(pair[0], ok ? EmailStatus.DIGESTED : EmailStatus.FAILED, ok ? now : null);
            }
            digestCount += ok ? 1 : 0;
            failed += ok ? 0 : 1;
         }
         return (new Result(sent, digestCount, skipped, failed));
      }));
   }



   /*******************************************************************************
    ** Whether it is the user's digest hour now (their time zone, default UTC).
    *******************************************************************************/
   static boolean isDigestHour(QRecord setting, Instant now)
   {
      Integer hour = setting == null ? null : setting.getValueInteger("digestHour");
      ZoneId  zone = ZoneOffset.UTC;
      try
      {
         if(setting != null && StringUtils.hasContent(setting.getValueString("timeZone")))
         {
            zone = ZoneId.of(setting.getValueString("timeZone"));
         }
      }
      catch(DateTimeException e)
      {
         zone = ZoneOffset.UTC;
      }
      return (now.atZone(zone).getHour() == (hour == null ? 8 : hour));
   }



   /*******************************************************************************
    ** Whether a severity is at least the minimum (default WARNING).
    *******************************************************************************/
   static boolean atLeast(String severity, String minimum)
   {
      return (NotificationService.severityOf(severity, NotificationSeverity.INFO).compareTo(NotificationService.severityOf(minimum, NotificationSeverity.WARNING)) >= 0);
   }



   /*******************************************************************************
    ** One email: the notifications' titles, bodies (not for SECURITY) and
    ** links. False when it could not be sent (or there is no address).
    *******************************************************************************/
   private boolean send(String address, String subject, List<QRecord> items)
   {
      if(!StringUtils.hasContent(address))
      {
         return (false);
      }
      StringBuilder text = new StringBuilder();
      StringBuilder html = new StringBuilder("<div style=\"font-family:sans-serif\">");
      for(QRecord item : items)
      {
         String title = item.getValueString("title");
         String body  = NotificationCategory.SECURITY.name().equals(item.getValueString("category")) ? null : item.getValueString("body");
         String link  = absolute(NotificationService.safeLink(item.getValueString("linkUrl")));
         text.append(title).append("\n");
         html.append("<h3 style=\"margin:16px 0 4px\">").append(escape(title)).append("</h3>");
         if(StringUtils.hasContent(body))
         {
            text.append(body).append("\n");
            html.append("<p style=\"white-space:pre-wrap;margin:0\">").append(escape(body)).append("</p>");
         }
         if(link != null)
         {
            text.append(link).append("\n");
            html.append("<p style=\"margin:4px 0\"><a href=\"").append(escape(link)).append("\">")
               .append(escape(StringUtils.hasContent(item.getValueString("linkLabel")) ? item.getValueString("linkLabel") : "Open")).append("</a></p>");
         }
         text.append("\n");
      }
      String settings = absolute("/app/" + AlertsNotificationPreference.TABLE_NAME);
      text.append("Change these emails in My Notification Settings: ").append(settings).append("\n");
      html.append("<p style=\"color:#666;font-size:12px;margin-top:24px\">Change these emails in <a href=\"").append(escape(settings)).append("\">My Notification Settings</a>.</p></div>");
      try
      {
         new SendMessageAction().execute(new SendMessageInput()
            .withMessagingProviderName(runtime.getConfig().getMessagingProviderName())
            .withFrom(new Party().withAddress(runtime.getConfig().getFromAddress()).withRole(EmailPartyRole.FROM))
            .withTo(new MultiParty().withParty(new Party().withAddress(address).withRole(EmailPartyRole.TO)))
            .withSubject(subject)
            .withContent(new Content().withContentRole(EmailContentRole.TEXT).withBody(text.toString()))
            .withContent(new Content().withContentRole(EmailContentRole.HTML).withBody(html.toString())));
         return (true);
      }
      catch(Exception e)
      {
         LOG.warn("Could not email a notification", e, logPair("subject", subject));
         return (false);
      }
   }



   /*******************************************************************************
    ** A link made absolute with the public base URL (relative app paths).
    *******************************************************************************/
   private String absolute(String link)
   {
      String base = runtime.getConfig().getPublicBaseUrl();
      if(link == null || !link.startsWith("/") || !StringUtils.hasContent(base))
      {
         return (link);
      }
      return (base.replaceAll("/+$", "") + link);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String escape(String value)
   {
      return (value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"));
   }



   /*******************************************************************************
    ** The live (SENT, published, not expired) notifications for the rows.
    *******************************************************************************/
   private static Map<Integer, QRecord> notifications(List<QRecord> recipients, Instant now) throws QException
   {
      Map<Integer, QRecord> live = new HashMap<>();
      List<Integer>         ids  = recipients.stream().map(recipient -> recipient.getValueInteger("notificationId")).distinct().toList();
      if(ids.isEmpty())
      {
         return (live);
      }
      for(QRecord notification : AlertsDb.query(AlertsNotification.TABLE_NAME, new QQueryFilter().withCriteria("id", QCriteriaOperator.IN, ids)
         .withCriteria("status", QCriteriaOperator.EQUALS, NotificationService.STATUS_SENT)))
      {
         Instant expireAt = notification.getValueInstant("expireAt");
         if(expireAt == null || expireAt.isAfter(now))
         {
            live.put(notification.getValueInteger("id"), notification);
         }
      }
      return (live);
   }



   /*******************************************************************************
    ** My Alert Settings rows (address, time zone, digest hour) by user.
    *******************************************************************************/
   private static Map<String, QRecord> userSettings(java.util.Set<String> userIds) throws QException
   {
      Map<String, QRecord> settings = new HashMap<>();
      for(QRecord setting : AlertsDb.query(AlertsUserPreference.TABLE_NAME, new QQueryFilter().withCriteria("userId", QCriteriaOperator.IN, new ArrayList<>(userIds))))
      {
         settings.put(setting.getValueString("userId"), setting);
      }
      return (settings);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String address(QRecord setting)
   {
      return (setting == null ? null : setting.getValueString("emailAddress"));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void mark(QRecord recipient, EmailStatus status, Instant at) throws QException
   {
      QRecord update = new QRecord().withValue("id", recipient.getValue("id")).withValue("emailStatus", status.name());
      if(at != null)
      {
         update.setValue("emailedAt", at);
      }
      AlertsDb.update(AlertsNotificationRecipient.TABLE_NAME, update);
   }
}
