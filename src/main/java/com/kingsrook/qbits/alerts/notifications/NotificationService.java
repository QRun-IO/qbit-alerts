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


import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import com.kingsrook.qbits.alerts.AlertsRuntime;
import com.kingsrook.qbits.alerts.delivery.AlertMessage;
import com.kingsrook.qbits.alerts.engine.AlertsDb;
import com.kingsrook.qbits.alerts.model.AlertsAlert;
import com.kingsrook.qbits.alerts.model.AlertsNotification;
import com.kingsrook.qbits.alerts.model.AlertsNotificationPreference;
import com.kingsrook.qbits.alerts.model.AlertsNotificationRecipient;
import com.kingsrook.qqq.backend.core.actions.tables.DeleteAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.alerts.AlertLink;
import com.kingsrook.qqq.backend.core.alerts.NotificationAudience;
import com.kingsrook.qqq.backend.core.alerts.NotificationCategory;
import com.kingsrook.qqq.backend.core.alerts.NotificationSeverity;
import com.kingsrook.qqq.backend.core.alerts.NotificationSignal;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QUserFacingException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** User notifications (docs/NOTIFICATIONS.md): writes notifications from
 ** producer signals and alerts, resolves audiences into one recipient row per
 ** user, retracts, sends scheduled ones when they are due, and purges old
 ** ones. Everything runs as the system user; what each person sees is
 ** filtered by their own recipient rows (owner record lock).
 *******************************************************************************/
public class NotificationService
{
   private static final QLogger LOG = QLogger.getLogger(NotificationService.class);

   public static final String STATUS_DRAFT     = "DRAFT";
   public static final String STATUS_SCHEDULED = "SCHEDULED";
   public static final String STATUS_SENT      = "SENT";
   public static final String STATUS_RETRACTED = "RETRACTED";

   static final int BATCH = 500;

   private final AlertsRuntime          runtime;
   private final AlertsAudienceResolver resolver;



   /*******************************************************************************
    ** What a send did.
    *******************************************************************************/
   public record SendResult(int added, int total, List<String> warnings)
   {
   }



   /*******************************************************************************
    ** Constructor
    *******************************************************************************/
   public NotificationService(AlertsRuntime runtime)
   {
      this.runtime = runtime;
      this.resolver = new AlertsAudienceResolver(runtime);
   }



   /*******************************************************************************
    ** A producer's notification: written, then sent now (or scheduled for its
    ** publishAt). A signal whose sourceKey and audience match a live
    ** notification updates it and brings it back to unread for its readers.
    ** Returns the notification id.
    *******************************************************************************/
   public Integer notify(NotificationSignal signal) throws QException
   {
      if(signal == null || !StringUtils.hasContent(signal.getTitle()) || signal.getAudience() == null)
      {
         throw (new QUserFacingException("A notification needs a title and an audience."));
      }
      Instant now = runtime.now();
      return (runtime.asSystem(() ->
      {
         QRecord values   = fromSignal(signal);
         QRecord existing = null;
         if(StringUtils.hasContent(signal.getSourceKey()))
         {
            existing = AlertsDb.first(AlertsNotification.TABLE_NAME, AlertsDb.eq("sourceKey", signal.getSourceKey())
               .withCriteria("audienceType", QCriteriaOperator.EQUALS, signal.getAudience().getType().name())
               .withCriteria("status", QCriteriaOperator.IN, List.of(STATUS_SENT, STATUS_SCHEDULED))
               .withCriteria(audienceValueCriteria(signal.getAudience())));
         }

         boolean scheduled = signal.getPublishAt() != null && signal.getPublishAt().isAfter(now);
         Integer id;
         if(existing != null)
         {
            id = existing.getValueInteger("id");
            AlertsDb.update(AlertsNotification.TABLE_NAME, values.withValue("id", id));
            if(STATUS_SENT.equals(existing.getValueString("status")))
            {
               markAllUnread(id, now);
            }
         }
         else
         {
            values.setValue("status", scheduled ? STATUS_SCHEDULED : STATUS_DRAFT);
            id = AlertsDb.insert(AlertsNotification.TABLE_NAME, values).getValueInteger("id");
         }
         if(!scheduled)
         {
            send(id);
         }
         return (id);
      }));
   }



   /*******************************************************************************
    ** Send (or re-send) a notification: resolve its audience and add a
    ** recipient row for each user who doesn't have one (users who muted the
    ** category in-app are skipped, except for SECURITY). Marks it SENT.
    *******************************************************************************/
   public SendResult send(Integer notificationId) throws QException
   {
      Instant now = runtime.now();
      return (runtime.asSystem(() ->
      {
         QRecord notification = AlertsDb.byId(AlertsNotification.TABLE_NAME, notificationId);
         if(notification == null)
         {
            throw (new QUserFacingException("The notification was not found."));
         }
         if(STATUS_RETRACTED.equals(notification.getValueString("status")))
         {
            throw (new QUserFacingException("A retracted notification can't be sent again; copy it instead."));
         }

         AlertsAudienceResolver.Resolved resolved = resolver.resolve(audienceOf(notification));
         Set<String>                     existing = recipientUserIds(notificationId);
         Set<String>                     muted    = mutedUsers(notification.getValueString("category"));

         List<QRecord> inserts = new ArrayList<>();
         for(String userId : resolved.userIds())
         {
            if(!existing.contains(userId) && !muted.contains(userId))
            {
               inserts.add(newRecipient(notificationId, userId, now));
            }
         }
         insertRecipients(inserts);

         QRecord update = new QRecord().withValue("id", notificationId)
            .withValue("status", STATUS_SENT)
            .withValue("recipientCount", existing.size() + inserts.size());
         if(notification.getValue("sentAt") == null)
         {
            update.setValue("sentAt", now);
         }
         if(notification.getValue("publishAt") == null)
         {
            update.setValue("publishAt", now);
         }
         AlertsDb.update(AlertsNotification.TABLE_NAME, update);
         LOG.info("Sent a notification", logPair("notificationId", notificationId), logPair("added", inserts.size()), logPair("total", existing.size() + inserts.size()));
         return (new SendResult(inserts.size(), existing.size() + inserts.size(), resolved.warnings()));
      }));
   }



   /*******************************************************************************
    ** Who a notification would reach now (Preview audience).
    *******************************************************************************/
   public AlertsAudienceResolver.Resolved preview(QRecord notification) throws QException
   {
      return (runtime.asSystem(() -> resolver.resolve(audienceOf(notification))));
   }



   /*******************************************************************************
    ** Schedule a draft for its publishAt (which must be in the future).
    *******************************************************************************/
   public void schedule(Integer notificationId) throws QException
   {
      Instant now = runtime.now();
      runtime.asSystem(() ->
      {
         QRecord notification = AlertsDb.byId(AlertsNotification.TABLE_NAME, notificationId);
         Instant publishAt    = notification == null ? null : notification.getValueInstant("publishAt");
         if(publishAt == null || !publishAt.isAfter(now))
         {
            throw (new QUserFacingException("Set Publish At to a future time first."));
         }
         AlertsDb.update(AlertsNotification.TABLE_NAME, new QRecord().withValue("id", notificationId).withValue("status", STATUS_SCHEDULED));
         return (null);
      });
   }



   /*******************************************************************************
    ** Retract a notification: it disappears from every bell. Recipient rows
    ** are kept (for the record).
    *******************************************************************************/
   public void retract(Integer notificationId) throws QException
   {
      Instant now = runtime.now();
      runtime.asSystem(() -> AlertsDb.update(AlertsNotification.TABLE_NAME, new QRecord().withValue("id", notificationId)
         .withValue("status", STATUS_RETRACTED).withValue("retractedAt", now)));
   }



   /*******************************************************************************
    ** Send every SCHEDULED notification whose publishAt has passed. Returns
    ** how many were sent.
    *******************************************************************************/
   public int sendDue() throws QException
   {
      Instant now = runtime.now();
      List<QRecord> due = runtime.asSystem(() -> AlertsDb.query(AlertsNotification.TABLE_NAME, AlertsDb.eq("status", STATUS_SCHEDULED)
         .withCriteria("publishAt", QCriteriaOperator.LESS_THAN_OR_EQUALS, now).withLimit(50)));
      int sent = 0;
      for(QRecord notification : due)
      {
         try
         {
            send(notification.getValueInteger("id"));
            sent++;
         }
         catch(Exception e)
         {
            LOG.warn("Could not send a scheduled notification", e, logPair("notificationId", notification.getValue("id")));
         }
      }
      return (sent);
   }



   /*******************************************************************************
    ** Delete notifications (and their recipients) a week past their expireAt,
    ** and SENT or RETRACTED ones older than notificationRetentionDays. At most
    ** 500 per call. Returns how many were deleted.
    *******************************************************************************/
   public int purge() throws QException
   {
      Instant now      = runtime.now();
      Instant oldest   = now.minus(runtime.getConfig().getNotificationRetentionDays(), ChronoUnit.DAYS);
      return (runtime.asSystem(() ->
      {
         Set<Serializable> ids = new HashSet<>();
         for(QRecord row : AlertsDb.query(AlertsNotification.TABLE_NAME, new QQueryFilter()
            .withCriteria("expireAt", QCriteriaOperator.LESS_THAN, now.minus(7, ChronoUnit.DAYS)).withLimit(BATCH)))
         {
            ids.add(row.getValueInteger("id"));
         }
         for(QRecord row : AlertsDb.query(AlertsNotification.TABLE_NAME, new QQueryFilter()
            .withCriteria("status", QCriteriaOperator.IN, List.of(STATUS_SENT, STATUS_RETRACTED))
            .withCriteria("createDate", QCriteriaOperator.LESS_THAN, oldest).withLimit(BATCH)))
         {
            ids.add(row.getValueInteger("id"));
         }
         deleteWithRecipients(new ArrayList<>(ids));
         return (ids.size());
      }));
   }



   /*******************************************************************************
    ** Delete notifications and their recipient rows (as the system user).
    *******************************************************************************/
   public void deleteWithRecipients(List<Serializable> notificationIds) throws QException
   {
      if(CollectionUtils.nullSafeIsEmpty(notificationIds))
      {
         return;
      }
      runtime.asSystem(() ->
      {
         new DeleteAction().execute(new DeleteInput(AlertsNotificationRecipient.TABLE_NAME)
            .withQueryFilter(new QQueryFilter(new QFilterCriteria("notificationId", QCriteriaOperator.IN, notificationIds))));
         new DeleteAction().execute(new DeleteInput(AlertsNotification.TABLE_NAME).withPrimaryKeys(notificationIds));
         return (null);
      });
   }



   /*******************************************************************************
    ** Put an alert in a user's bell (the in-app channel): one ALERT
    ** notification per alert (sourceKey alert:id), kept in step with the
    ** alert's title and severity, and a recipient row for the user that comes
    ** back to unread on every delivery. Call as the system user.
    *******************************************************************************/
   public void deliverAlert(String userId, QRecord alert, Instant now) throws QException
   {
      Integer alertId   = alert.getValueInteger("id");
      String  sourceKey = "alert:" + alertId;
      QRecord values    = new QRecord()
         .withValue("title", StringUtils.safeTruncate(alert.getValueString("title"), 200))
         .withValue("body", alert.getValueString("summary"))
         .withValue("severity", severityOf(alert.getValueString("severity"), NotificationSeverity.WARNING).name())
         .withValue("category", NotificationCategory.ALERT.name())
         .withValue("sourceName", StringUtils.hasContent(alert.getValueString("sourceName")) ? alert.getValueString("sourceName") : "alerts")
         .withValue("sourceKey", sourceKey)
         .withValue("sourceTableName", AlertsAlert.TABLE_NAME)
         .withValue("sourceRecordId", String.valueOf(alertId))
         .withValue("linkUrl", "/app/" + AlertsAlert.TABLE_NAME + "/" + alertId)
         .withValue("linkLabel", "Open alert");

      QRecord notification = AlertsDb.first(AlertsNotification.TABLE_NAME, AlertsDb.eq("sourceKey", sourceKey)
         .withCriteria("category", QCriteriaOperator.EQUALS, NotificationCategory.ALERT.name()));
      Integer notificationId;
      if(notification == null)
      {
         notificationId = AlertsDb.insert(AlertsNotification.TABLE_NAME, values
            .withValue("audienceType", NotificationAudience.Type.USERS.name())
            .withValue("status", STATUS_SENT)
            .withValue("publishAt", now)
            .withValue("sentAt", now)
            .withValue("recipientCount", 0)).getValueInteger("id");
      }
      else
      {
         notificationId = notification.getValueInteger("id");
         AlertsDb.update(AlertsNotification.TABLE_NAME, values.withValue("id", notificationId).withValue("status", STATUS_SENT));
      }

      QRecord recipient = AlertsDb.first(AlertsNotificationRecipient.TABLE_NAME, AlertsDb.eq("notificationId", notificationId)
         .withCriteria("userId", QCriteriaOperator.EQUALS, userId));
      if(recipient == null)
      {
         AlertsDb.insert(AlertsNotificationRecipient.TABLE_NAME, newRecipient(notificationId, userId, now));
         AlertsDb.update(AlertsNotification.TABLE_NAME, new QRecord().withValue("id", notificationId)
            .withValue("recipientCount", AlertsDb.count(AlertsNotificationRecipient.TABLE_NAME, AlertsDb.eq("notificationId", notificationId))));
      }
      else
      {
         AlertsDb.update(AlertsNotificationRecipient.TABLE_NAME, new QRecord().withValue("id", recipient.getValue("id"))
            .withValue("deliveredAt", now).withValue("readAt", null).withValue("dismissedAt", null));
      }
   }



   /*******************************************************************************
    ** One bell notification for a claimed digest, with all its alert links.
    ** ALERT category keeps the general notification emailer from emailing it.
    *******************************************************************************/
   public void deliverDigest(String userId, AlertMessage message, Instant now) throws QException
   {
      QRecord existing = AlertsDb.first(AlertsNotification.TABLE_NAME, AlertsDb.eq("sourceKey", message.idempotencyKey()));
      if(existing != null)
      {
         return;
      }
      QRecord notification = AlertsDb.insert(AlertsNotification.TABLE_NAME, new QRecord()
         .withValue("title", message.subject()).withValue("body", message.text()).withValue("severity", message.severity())
         .withValue("category", NotificationCategory.ALERT.name()).withValue("status", STATUS_SENT)
         .withValue("sourceName", "alerts").withValue("sourceKey", message.idempotencyKey())
         .withValue("linkUrl", "/app/" + AlertsAlert.TABLE_NAME).withValue("linkLabel", "Open alerts")
         .withValue("audienceType", NotificationAudience.Type.USERS.name()).withValue("audienceValue", userId)
         .withValue("publishAt", now).withValue("sentAt", now).withValue("recipientCount", 1));
      AlertsDb.insert(AlertsNotificationRecipient.TABLE_NAME, newRecipient(notification.getValueInteger("id"), userId, now));
   }



   /*******************************************************************************
    ** Recount a notification's readers (after people mark it read or unread).
    *******************************************************************************/
   public void recountRead(Set<Integer> notificationIds) throws QException
   {
      runtime.asSystem(() ->
      {
         for(Integer id : notificationIds)
         {
            int read = AlertsDb.count(AlertsNotificationRecipient.TABLE_NAME, AlertsDb.eq("notificationId", id).withCriteria("readAt", QCriteriaOperator.IS_NOT_BLANK));
            AlertsDb.update(AlertsNotification.TABLE_NAME, new QRecord().withValue("id", id).withValue("readCount", read));
         }
         return (null);
      });
   }



   /*******************************************************************************
    ** A notification's audience, from its stored fields (null when unknown).
    *******************************************************************************/
   public static NotificationAudience audienceOf(QRecord notification)
   {
      String   type   = notification.getValueString("audienceType");
      String   value  = notification.getValueString("audienceValue");
      String[] values = value == null ? new String[0] : Arrays.stream(value.split("[,\n]"))
         .map(String::trim).filter(v -> !v.isEmpty()).distinct().toArray(String[]::new);
      if(type == null)
      {
         return (null);
      }
      return (switch(type.trim().toUpperCase(Locale.ROOT))
      {
         case "ALL_USERS" -> NotificationAudience.allUsers();
         case "ROLES" -> NotificationAudience.roles(values);
         case "PERMISSION" -> NotificationAudience.permission(values.length == 0 ? null : values[0]);
         case "USERS" -> NotificationAudience.users(values);
         default -> null;
      });
   }



   /*******************************************************************************
    ** Read a stored severity without changing legacy case and fallback behavior.
    *******************************************************************************/
   public static NotificationSeverity severityOf(String name, NotificationSeverity fallback)
   {
      if(name != null)
      {
         for(NotificationSeverity severity : NotificationSeverity.values())
         {
            if(severity.name().equalsIgnoreCase(name.trim()))
            {
               return (severity);
            }
         }
      }
      return (fallback);
   }



   /*******************************************************************************
    ** A link as a URL the UI can follow: a record or table path, an app path,
    ** or an https URL (anything else is dropped).
    *******************************************************************************/
   public static String linkUrl(AlertLink link)
   {
      if(link == null)
      {
         return (null);
      }
      if(link.getTableName() != null && link.getRecordId() != null)
      {
         return ("/app/" + link.getTableName() + "/" + link.getRecordId());
      }
      if(link.getTableName() != null)
      {
         return ("/app/" + link.getTableName());
      }
      return (safeLink(link.getPath() != null ? link.getPath() : link.getUrl()));
   }



   /*******************************************************************************
    ** The link if it is a relative app path (one leading slash) or an https URL
    ** with a host, else null. Backslashes, whitespace and control characters
    ** are refused anywhere (browsers treat "/\evil.com" as "//evil.com").
    *******************************************************************************/
   public static String safeLink(String url)
   {
      if(!StringUtils.hasContent(url))
      {
         return (null);
      }
      String trimmed = url.trim();
      for(char c : trimmed.toCharArray())
      {
         if(c == '\\' || Character.isWhitespace(c) || Character.isISOControl(c))
         {
            return (null);
         }
      }
      if(trimmed.startsWith("/"))
      {
         return (trimmed.length() > 1 && trimmed.charAt(1) == '/' ? null : trimmed);
      }
      try
      {
         java.net.URI uri = new java.net.URI(trimmed);
         return ("https".equalsIgnoreCase(uri.getScheme()) && StringUtils.hasContent(uri.getHost()) ? trimmed : null);
      }
      catch(java.net.URISyntaxException e)
      {
         return (null);
      }
   }



   /*******************************************************************************
    ** The notification fields from a signal.
    *******************************************************************************/
   private QRecord fromSignal(NotificationSignal signal)
   {
      return (new QRecord()
         .withValue("title", StringUtils.safeTruncate(signal.getTitle(), 200))
         .withValue("body", signal.getBody())
         .withValue("severity", (signal.getSeverity() == null ? NotificationSeverity.INFO : signal.getSeverity()).name())
         .withValue("category", (signal.getCategory() == null ? NotificationCategory.SYSTEM : signal.getCategory()).name())
         .withValue("sourceName", StringUtils.safeTruncate(signal.getSourceName(), 100))
         .withValue("sourceKey", StringUtils.safeTruncate(signal.getSourceKey(), 200))
         .withValue("sourceTableName", signal.getSourceTableName())
         .withValue("sourceRecordId", signal.getSourceRecordId())
         .withValue("linkUrl", linkUrl(signal.getLink()))
         .withValue("linkLabel", signal.getLink() == null ? null : signal.getLink().getLabel())
         .withValue("audienceType", signal.getAudience().getType().name())
         .withValue("audienceValue", audienceValue(signal.getAudience()))
         .withValue("publishAt", signal.getPublishAt())
         .withValue("expireAt", signal.getExpireAt())
         .withValue("sticky", Boolean.TRUE.equals(signal.getSticky())));
   }



   /*******************************************************************************
    ** Match the stored audience value (null when there are no values).
    *******************************************************************************/
   private static QFilterCriteria audienceValueCriteria(NotificationAudience audience)
   {
      String value = audienceValue(audience);
      return (value == null ? new QFilterCriteria("audienceValue", QCriteriaOperator.IS_BLANK) : new QFilterCriteria("audienceValue", QCriteriaOperator.EQUALS, value));
   }



   /*******************************************************************************
    ** Preserve the qbit's stored audience format and dedup keys with core types.
    *******************************************************************************/
   private static String audienceValue(NotificationAudience audience)
   {
      List<String> values = audience.getValues().stream().map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
      return (values.isEmpty() ? null : String.join(",", values));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static QRecord newRecipient(Integer notificationId, String userId, Instant now)
   {
      return (new QRecord().withValue("notificationId", notificationId).withValue("userId", userId).withValue("deliveredAt", now).withValue("emailStatus", "NONE").withValue("slackStatus", "NONE"));
   }



   /*******************************************************************************
    ** The users who already have this notification.
    *******************************************************************************/
   private Set<String> recipientUserIds(Integer notificationId) throws QException
   {
      Set<String> userIds = new HashSet<>();
      for(QRecord recipient : AlertsDb.query(AlertsNotificationRecipient.TABLE_NAME, AlertsDb.eq("notificationId", notificationId)))
      {
         userIds.add(recipient.getValueString("userId"));
      }
      return (userIds);
   }



   /*******************************************************************************
    ** Users who muted a category in-app (never for SECURITY).
    *******************************************************************************/
   private Set<String> mutedUsers(String category) throws QException
   {
      Set<String> userIds = new HashSet<>();
      if(category == null || NotificationCategory.SECURITY.name().equals(category))
      {
         return (userIds);
      }
      for(QRecord preference : AlertsDb.query(AlertsNotificationPreference.TABLE_NAME, AlertsDb.eq("category", category).withCriteria("inAppMuted", QCriteriaOperator.EQUALS, true)))
      {
         userIds.add(preference.getValueString("userId"));
      }
      return (userIds);
   }



   /*******************************************************************************
    ** Insert recipient rows in batches.
    *******************************************************************************/
   private void insertRecipients(List<QRecord> recipients) throws QException
   {
      for(int start = 0; start < recipients.size(); start += BATCH)
      {
         List<QRecord> batch = recipients.subList(start, Math.min(recipients.size(), start + BATCH));
         for(QRecord inserted : new InsertAction().execute(new InsertInput(AlertsNotificationRecipient.TABLE_NAME).withRecords(new ArrayList<>(batch))).getRecords())
         {
            if(CollectionUtils.nullSafeHasContents(inserted.getErrors()))
            {
               LOG.warn("Could not add a notification recipient", logPair("error", inserted.getErrors().get(0).getMessage()));
            }
         }
      }
   }



   /*******************************************************************************
    ** Bring a notification back to unread (and undismissed) for everyone who
    ** has it (a producer repeated it).
    *******************************************************************************/
   private void markAllUnread(Integer notificationId, Instant now) throws QException
   {
      List<QRecord> updates = new ArrayList<>();
      for(QRecord recipient : AlertsDb.query(AlertsNotificationRecipient.TABLE_NAME, AlertsDb.eq("notificationId", notificationId)))
      {
         updates.add(new QRecord().withValue("id", recipient.getValue("id")).withValue("deliveredAt", now).withValue("readAt", null).withValue("dismissedAt", null));
      }
      for(int start = 0; start < updates.size(); start += BATCH)
      {
         new UpdateAction().execute(new UpdateInput(AlertsNotificationRecipient.TABLE_NAME).withRecords(new ArrayList<>(updates.subList(start, Math.min(updates.size(), start + BATCH)))));
      }
      if(!updates.isEmpty())
      {
         AlertsDb.update(AlertsNotification.TABLE_NAME, new QRecord().withValue("id", notificationId).withValue("readCount", 0));
      }
   }
}
