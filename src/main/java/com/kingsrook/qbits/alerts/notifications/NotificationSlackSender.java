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


import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.alerts.AlertsRuntime;
import com.kingsrook.qbits.alerts.engine.AlertRouter;
import com.kingsrook.qbits.alerts.engine.AlertTiming;
import com.kingsrook.qbits.alerts.engine.AlertsDb;
import com.kingsrook.qbits.alerts.model.AlertsNotificationPreference;
import com.kingsrook.qbits.alerts.model.AlertsNotificationRecipient;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.EmailMode;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.SlackStatus;
import com.kingsrook.qqq.backend.core.actions.messaging.SendMessageAction;
import com.kingsrook.qqq.backend.core.alerts.AlertSeverity;
import com.kingsrook.qqq.backend.core.alerts.NotificationCategory;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QUserFacingException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.messaging.Content;
import com.kingsrook.qqq.backend.core.model.actions.messaging.ContentRole;
import com.kingsrook.qqq.backend.core.model.actions.messaging.Party;
import com.kingsrook.qqq.backend.core.model.actions.messaging.SendMessageInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Slack direct messages for user notifications (docs/NOTIFICATIONS.md section
 ** 7), opt-in per user and category in My Notification Settings, alongside
 ** the bell and email:
 ** - IMMEDIATE: one DM per notification at or above slackMinSeverity;
 ** - DAILY_DIGEST: one DM per user at their digest hour listing their unread
 **   notifications.
 ** Sent only through core SendMessageAction (provider slackProviderName,
 ** address "user:<id>", the recipient as owner, so the DM travels through that
 ** user's own Slack connection); qbit-slack is never imported. Slack is a push
 ** channel: pause and quiet hours hold it (CRITICAL bypasses quiet hours),
 ** never the bell. ALERT notifications are not sent here. SECURITY DMs carry
 ** the title and link only. A user without a Slack connection (the provider
 ** throws a QUserFacingException) is SKIPPED with no retry; other failures are
 ** FAILED. Never fails the notification itself. Runs as the system user from
 ** the delivery job.
 *******************************************************************************/
public class NotificationSlackSender
{
   private static final QLogger LOG = QLogger.getLogger(NotificationSlackSender.class);

   private final AlertsRuntime runtime;



   /*******************************************************************************
    ** What one run did.
    *******************************************************************************/
   public record Result(int sent, int digests, int held, int skipped, int failed)
   {
   }



   /*******************************************************************************
    ** Constructor
    *******************************************************************************/
   public NotificationSlackSender(AlertsRuntime runtime)
   {
      this.runtime = runtime;
   }



   /*******************************************************************************
    ** Send what is due: immediate DMs, and digest DMs for users at their hour.
    *******************************************************************************/
   public Result run() throws QException
   {
      Instant now = runtime.now();
      return (runtime.asSystem(() ->
      {
         Map<String, Map<String, QRecord>> preferences = new HashMap<>();
         for(QRecord preference : AlertsDb.query(AlertsNotificationPreference.TABLE_NAME, new QQueryFilter()
            .withCriteria("slackMode", QCriteriaOperator.IN, List.of(EmailMode.IMMEDIATE.name(), EmailMode.DAILY_DIGEST.name()))))
         {
            preferences.computeIfAbsent(preference.getValueString("userId"), k -> new HashMap<>()).put(preference.getValueString("category"), preference);
         }
         if(preferences.isEmpty())
         {
            return (new Result(0, 0, 0, 0, 0));
         }

         List<QRecord> recipients = AlertsDb.query(AlertsNotificationRecipient.TABLE_NAME, new QQueryFilter()
            .withCriteria("userId", QCriteriaOperator.IN, new ArrayList<>(preferences.keySet()))
            .withCriteria("slackStatus", QCriteriaOperator.IN, List.of(SlackStatus.NONE.name(), SlackStatus.PENDING.name()))
            .withCriteria("deliveredAt", QCriteriaOperator.GREATER_THAN_OR_EQUALS, now.minus(1, ChronoUnit.DAYS))
            .withLimit(NotificationEmailer.SCAN));
         Map<Integer, QRecord> notifications = NotificationEmailer.notifications(recipients, now);
         Map<String, QRecord>  settings      = NotificationEmailer.userSettings(preferences.keySet());
         boolean               available     = isAvailable();

         int                          sent    = 0;
         int                          held    = 0;
         int                          skipped = 0;
         int                          failed  = 0;
         Map<String, List<QRecord[]>> digests = new LinkedHashMap<>();
         for(QRecord recipient : recipients)
         {
            QRecord notification = notifications.get(recipient.getValueInteger("notificationId"));
            String  userId       = recipient.getValueString("userId");
            QRecord preference   = notification == null ? null : preferences.get(userId).get(notification.getValueString("category"));
            if(notification == null || preference == null || NotificationCategory.ALERT.name().equals(notification.getValueString("category"))
               || !NotificationEmailer.atLeast(notification.getValueString("severity"), preference.getValueString("slackMinSeverity")) || !available)
            {
               mark(recipient, SlackStatus.SKIPPED);
               skipped++;
            }
            else if(EmailMode.IMMEDIATE.name().equals(preference.getValueString("slackMode")))
            {
               if(isHeld(settings.get(userId), notification, now))
               {
                  held++;
                  continue;
               }
               SlackStatus status = sendExternal(userId, notification.getValueString("title"), List.of(notification), notification.getValueInteger("id"));
               mark(recipient, status);
               sent += status == SlackStatus.SENT ? 1 : 0;
               skipped += status == SlackStatus.SKIPPED ? 1 : 0;
               failed += status == SlackStatus.FAILED ? 1 : 0;
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
            if(!NotificationEmailer.isDigestHour(setting, now))
            {
               continue;
            }
            List<QRecord> items = digest.getValue().stream().map(pair -> pair[1]).toList();
            if(items.stream().allMatch(item -> isHeld(setting, item, now)))
            {
               held++;
               continue;
            }
            SlackStatus status = sendExternal(digest.getKey(), items.size() == 1 ? "1 new notification" : items.size() + " new notifications", items, null);
            for(QRecord[] pair : digest.getValue())
            {
               mark(pair[0], status == SlackStatus.SENT ? SlackStatus.DIGESTED : status);
            }
            digestCount += status == SlackStatus.SENT ? 1 : 0;
            failed += status == SlackStatus.FAILED ? 1 : 0;
         }
         return (new Result(sent, digestCount, held, skipped, failed));
      }));
   }



   /*******************************************************************************
    ** Whether the Slack messaging provider is registered in this instance.
    ** RC.6 core has no SendMessageAction.isAvailable, so ask the instance.
    *******************************************************************************/
   private boolean isAvailable()
   {
      String name = runtime.getConfig().getSlackProviderName();
      return (StringUtils.hasContent(name) && QContext.getQInstance().getMessagingProvider(name) != null);
   }



   /*******************************************************************************
    ** Whether a push is held for now: the user paused notifications, or it is
    ** their quiet hours (CRITICAL bypasses). The bell is never held.
    *******************************************************************************/
   private static boolean isHeld(QRecord setting, QRecord notification, Instant now)
   {
      return (AlertRouter.isPaused(setting, now)
         || AlertTiming.isQuiet(setting, AlertSeverity.fromLabel(notification.getValueString("severity"), AlertSeverity.WARNING), now));
   }



   /*******************************************************************************
    ** The external notification channel step: one Slack DM to the user (the
    ** recipient is the owner of the connection that sends it) with the
    ** notification titles, bodies (not for SECURITY) and links. SENT; SKIPPED
    ** when the provider says the user can't be reached (no Slack connection),
    ** with no retry; FAILED for anything else.
    **
    ** STAND-IN(core=QRun-IO/qqq#1194, removeBy=4.1.0-RC.8): RC.6 SendMessageInput has no
    ** idempotency key (notify:<notificationId>:<userId>:slack), so the
    ** recipient row's slackStatus is what prevents a resend.
    *******************************************************************************/
   private SlackStatus sendExternal(String userId, String subject, List<QRecord> items, Integer notificationId)
   {
      StringBuilder text = new StringBuilder();
      for(QRecord item : items)
      {
         String body = NotificationCategory.SECURITY.name().equals(item.getValueString("category")) ? null : item.getValueString("body");
         String link = NotificationEmailer.absolute(runtime, NotificationService.safeLink(item.getValueString("linkUrl")));
         text.append(items.size() > 1 ? "- " : "").append(item.getValueString("title")).append("\n");
         if(StringUtils.hasContent(body))
         {
            text.append(body).append("\n");
         }
         if(link != null)
         {
            text.append(link).append("\n");
         }
      }
      String key = notificationId == null ? null : "notify:" + notificationId + ":" + userId + ":slack";
      try
      {
         new SendMessageAction().execute(new SendMessageInput()
            .withMessagingProviderName(runtime.getConfig().getSlackProviderName())
            .withTo(new Party().withAddress("user:" + userId))
            .withSubject(subject)
            .withContentList(List.of(new Content().withContentRole(ContentRole.Default.DEFAULT).withBody(text.toString().stripTrailing()))));
         return (SlackStatus.SENT);
      }
      catch(QUserFacingException e)
      {
         LOG.info("Slack could not reach a user; skipped", logPair("userId", userId), logPair("key", key), logPair("reason", e.getMessage()));
         return (SlackStatus.SKIPPED);
      }
      catch(Exception e)
      {
         LOG.warn("Could not send a Slack notification", e, logPair("userId", userId), logPair("key", key));
         return (SlackStatus.FAILED);
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void mark(QRecord recipient, SlackStatus status) throws QException
   {
      AlertsDb.update(AlertsNotificationRecipient.TABLE_NAME, new QRecord().withValue("id", recipient.getValue("id")).withValue("slackStatus", status.name()));
   }
}
