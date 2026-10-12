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

package com.kingsrook.qbits.alerts.delivery;


import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.alerts.AlertsRuntime;
import com.kingsrook.qbits.alerts.engine.AlertsDb;
import com.kingsrook.qbits.alerts.model.AlertsAlert;
import com.kingsrook.qbits.alerts.model.AlertsChannel;
import com.kingsrook.qbits.alerts.model.AlertsGroup;
import com.kingsrook.qbits.alerts.model.AlertsNotification;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.ChannelType;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.HealthStatus;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.NotificationKind;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.NotificationStatus;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.Resolution;
import com.kingsrook.qbits.alerts.model.AlertsType;
import com.kingsrook.qbits.alerts.model.AlertsUserPreference;
import com.kingsrook.qbits.alerts.spi.AlertSeverity;
import com.kingsrook.qbits.alerts.spi.AlertSignal;
import com.kingsrook.qbits.alerts.spi.DetailLevel;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QUserFacingException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Delivers PENDING and due RETRYING notifications (design section 5.5).
 ** Notifications for the same channel, recipient and kind that are due
 ** together go out as one message ("3 new alerts in CDN"). Retries back off
 ** 1 min, 5 min, 15 min, 1 h, 4 h, then the row is DEAD; a permanent failure
 ** is FAILED at once. Channel health follows the qbit-webhooks model: a
 ** failure puts a channel on PROBATION, three in a row make it UNHEALTHY and
 ** raise alerts.channel.failing (routed in-app only, so a broken channel can
 ** never loop), and a success makes it HEALTHY again.
 **
 ** Claims: a row is marked SENDING with a 2-minute lease before it is sent.
 ** Two replicas that read the same row in the same instant could both send it
 ** (at-least-once); run the delivery process on one scheduler node to avoid
 ** that.
 *******************************************************************************/
public class DeliveryWorker
{
   private static final QLogger LOG = QLogger.getLogger(DeliveryWorker.class);

   public static final String CHANNEL_FAILING_TYPE = "alerts.channel.failing";

   static final List<Duration> BACKOFF = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(1), Duration.ofHours(4));

   private static final int BATCH_LIMIT = 200;
   private static final int UNHEALTHY_AFTER = 3;

   private final AlertsRuntime runtime;



   /*******************************************************************************
    ** Constructor
    *******************************************************************************/
   public DeliveryWorker(AlertsRuntime runtime)
   {
      this.runtime = runtime;
   }



   /*******************************************************************************
    ** Send everything due now. Returns how many notifications were attempted.
    *******************************************************************************/
   public int deliverDue() throws QException
   {
      return (runtime.asSystem(() ->
      {
         Instant now = runtime.now();
         List<QRecord> due = AlertsDb.query(AlertsNotification.TABLE_NAME, new QQueryFilter()
            .withCriteria("status", QCriteriaOperator.IN, List.of(NotificationStatus.PENDING.name(), NotificationStatus.RETRYING.name()))
            .withCriteria("nextAttemptAt", QCriteriaOperator.LESS_THAN_OR_EQUALS, now)
            .withOrderBy(new QFilterOrderBy("id"))
            .withLimit(BATCH_LIMIT));

         Map<String, List<QRecord>> groups = new LinkedHashMap<>();
         for(QRecord row : due)
         {
            Instant claimedUntil = row.getValueInstant("claimedUntil");
            if(claimedUntil != null && claimedUntil.isAfter(now) && NotificationStatus.SENDING.name().equals(row.getValueString("status")))
            {
               continue;
            }
            AlertsDb.update(AlertsNotification.TABLE_NAME, new QRecord().withValue("id", row.getValue("id")).withValue("status", NotificationStatus.SENDING.name())
               .withValue("claimedUntil", now.plus(Duration.ofMinutes(2))));
            groups.computeIfAbsent(row.getValue("channelId") + "|" + row.getValueString("recipientUserId") + "|" + row.getValueString("kind"), key -> new ArrayList<>()).add(row);
         }

         int attempted = 0;
         for(List<QRecord> group : groups.values())
         {
            attempted += sendGroup(group, now);
         }
         return (attempted);
      }));
   }



   /*******************************************************************************
    ** Queue failed or dead notifications again ("Retry delivery").
    *******************************************************************************/
   public int retry(List<Serializable> notificationIds) throws QException
   {
      return (runtime.asSystem(() ->
      {
         int count = 0;
         for(Serializable id : notificationIds)
         {
            QRecord row = AlertsDb.byId(AlertsNotification.TABLE_NAME, id);
            if(row != null && List.of(NotificationStatus.FAILED.name(), NotificationStatus.DEAD.name()).contains(row.getValueString("status")))
            {
               AlertsDb.update(AlertsNotification.TABLE_NAME, new QRecord().withValue("id", id).withValue("status", NotificationStatus.PENDING.name())
                  .withValue("nextAttemptAt", runtime.now()).withValue("attempts", 0).withValue("claimedUntil", null).withValue("lastError", null));
               count++;
            }
         }
         return (count);
      }));
   }



   /*******************************************************************************
    ** Send a TEST message through a channel now, through the real pipeline (a
    ** logged notification row). Personal channels (in-app, email) go to the
    ** person running the test. Returns the notification row.
    *******************************************************************************/
   public QRecord sendTest(Integer channelId, String actorUserId) throws QException
   {
      return (runtime.asSystem(() ->
      {
         QRecord channel = AlertsDb.byId(AlertsChannel.TABLE_NAME, channelId);
         if(channel == null)
         {
            throw (new QUserFacingException("The channel was not found."));
         }
         String  channelType = channel.getValueString("channelType");
         boolean personal    = ChannelType.IN_APP.name().equals(channelType) || ChannelType.EMAIL.name().equals(channelType);
         Instant now         = runtime.now();
         QRecord row = AlertsDb.insert(AlertsNotification.TABLE_NAME, new QRecord()
            .withValue("channelId", channelId)
            .withValue("recipientUserId", personal ? actorUserId : null)
            .withValue("recipientLabel", personal ? actorUserId : channel.getValueString("label"))
            .withValue("kind", NotificationKind.TEST.name())
            .withValue("status", NotificationStatus.SENDING.name())
            .withValue("attempts", 0)
            .withValue("nextAttemptAt", now)
            .withValue("claimedUntil", now.plus(Duration.ofMinutes(2))));
         sendGroup(List.of(row), now);
         return (AlertsDb.byId(AlertsNotification.TABLE_NAME, row.getValue("id")));
      }));
   }



   /*******************************************************************************
    ** Render and send one group (same channel, recipient and kind).
    *******************************************************************************/
   private int sendGroup(List<QRecord> rows, Instant now) throws QException
   {
      QRecord               first   = rows.get(0);
      QRecord               channel = AlertsDb.byId(AlertsChannel.TABLE_NAME, first.getValue("channelId"));
      AlertChannelInterface impl    = channel == null ? null : runtime.getChannels().forType(channel.getValueString("channelType"));
      if(impl == null)
      {
         for(QRecord row : rows)
         {
            finish(row, DeliveryResult.failed(channel == null ? "The channel no longer exists." : "No implementation for channel type " + channel.getValueString("channelType") + "."), now);
         }
         return (rows.size());
      }

      NotificationKind kind  = NotificationKind.valueOf(first.getValueString("kind"));
      List<AlertMessageRenderer.Item> items = new ArrayList<>();
      Map<Integer, QRecord> types  = new HashMap<>();
      Map<Integer, QRecord> groups = new HashMap<>();
      for(QRecord row : rows)
      {
         QRecord alert = row.getValue("alertId") == null ? testAlert(now) : AlertsDb.byId(AlertsAlert.TABLE_NAME, row.getValue("alertId"));
         if(alert == null)
         {
            continue;
         }
         QRecord type  = alert.getValue("alertTypeId") == null ? null : types.computeIfAbsent(alert.getValueInteger("alertTypeId"), id -> quietly(AlertsType.TABLE_NAME, id));
         QRecord group = alert.getValue("groupId") == null ? null : groups.computeIfAbsent(alert.getValueInteger("groupId"), id -> quietly(AlertsGroup.TABLE_NAME, id));
         DetailLevel level = DetailLevel.lowerOf(DetailLevel.valueOf(channel.getValueString("detailLevel") == null ? "MINIMAL" : channel.getValueString("detailLevel")),
            type == null || type.getValueString("externalDetailCeiling") == null ? DetailLevel.STANDARD : DetailLevel.valueOf(type.getValueString("externalDetailCeiling")));
         items.add(new AlertMessageRenderer.Item(alert, type == null ? "Alerts" : type.getValueString("label"), group == null ? null : group.getValueString("label"), level));
      }
      if(items.isEmpty())
      {
         for(QRecord row : rows)
         {
            finish(row, DeliveryResult.failed("The alert no longer exists."), now);
         }
         return (rows.size());
      }

      AlertMessage message = AlertMessageRenderer.render(items, kind, runtime.getConfig().getPublicBaseUrl(), now);
      String       address = null;
      if(ChannelType.EMAIL.name().equals(channel.getValueString("channelType")) && first.getValueString("recipientUserId") != null)
      {
         QRecord preference = AlertsDb.first(AlertsUserPreference.TABLE_NAME, AlertsDb.eq("userId", first.getValueString("recipientUserId")));
         address = preference == null ? null : preference.getValueString("emailAddress");
         if(!StringUtils.hasContent(address))
         {
            for(QRecord row : rows)
            {
               finish(row, DeliveryResult.failed("The recipient has no email address in My Alert Settings."), now);
            }
            return (rows.size());
         }
      }

      DeliveryResult result;
      try
      {
         result = impl.deliver(message, channel, address, runtime.getChannels().getSecrets());
      }
      catch(Exception e)
      {
         result = DeliveryResult.retry(AlertRedactor.redact(e.getClass().getSimpleName() + ": " + e.getMessage()));
      }

      for(QRecord row : rows)
      {
         finish(row, result, now);
      }
      updateChannelHealth(channel, result, now);
      return (rows.size());
   }



   /*******************************************************************************
    ** Record one row's outcome.
    *******************************************************************************/
   private void finish(QRecord row, DeliveryResult result, Instant now) throws QException
   {
      int     attempts = (row.getValueInteger("attempts") == null ? 0 : row.getValueInteger("attempts")) + 1;
      QRecord update   = new QRecord().withValue("id", row.getValue("id")).withValue("attempts", attempts).withValue("claimedUntil", null);
      switch(result.outcome())
      {
         case SENT -> update.withValue("status", NotificationStatus.SENT.name()).withValue("sentAt", now).withValue("providerMessageId", result.providerMessageId()).withValue("lastError", null);
         case FAILED -> update.withValue("status", NotificationStatus.FAILED.name()).withValue("lastError", truncate(result.error()));
         case RETRY ->
         {
            if(attempts > BACKOFF.size())
            {
               update.withValue("status", NotificationStatus.DEAD.name()).withValue("lastError", truncate(result.error()));
            }
            else
            {
               update.withValue("status", NotificationStatus.RETRYING.name()).withValue("nextAttemptAt", now.plus(BACKOFF.get(attempts - 1))).withValue("lastError", truncate(result.error()));
            }
         }
         default -> throw (new IllegalStateException("Unexpected outcome " + result.outcome()));
      }
      AlertsDb.update(AlertsNotification.TABLE_NAME, update);
   }



   /*******************************************************************************
    ** HEALTHY on success; PROBATION, then UNHEALTHY (and an in-app alert) on
    ** consecutive failures.
    *******************************************************************************/
   private void updateChannelHealth(QRecord channel, DeliveryResult result, Instant now) throws QException
   {
      Integer channelId = channel.getValueInteger("id");
      if(result.outcome() == DeliveryResult.Outcome.SENT)
      {
         boolean wasUnhealthy = !HealthStatus.HEALTHY.name().equals(channel.getValueString("healthStatus"));
         AlertsDb.update(AlertsChannel.TABLE_NAME, new QRecord().withValue("id", channelId).withValue("healthStatus", HealthStatus.HEALTHY.name())
            .withValue("consecutiveFailures", 0).withValue("lastSuccessAt", now));
         if(wasUnhealthy)
         {
            runtime.getEngine().resolve(CHANNEL_FAILING_TYPE, "channel:" + channelId, Resolution.PRODUCER, null, "A later delivery succeeded");
         }
         return;
      }

      int    failures = (channel.getValueInteger("consecutiveFailures") == null ? 0 : channel.getValueInteger("consecutiveFailures")) + 1;
      String health   = failures >= UNHEALTHY_AFTER ? HealthStatus.UNHEALTHY.name() : HealthStatus.PROBATION.name();
      AlertsDb.update(AlertsChannel.TABLE_NAME, new QRecord().withValue("id", channelId).withValue("healthStatus", health)
         .withValue("consecutiveFailures", failures).withValue("lastFailureAt", now).withValue("lastError", truncate(result.error())));
      if(failures == UNHEALTHY_AFTER)
      {
         LOG.warn("Alerts channel is failing", logPair("channel", channel.getValueString("name")), logPair("failures", failures));
         runtime.getEngine().raise(new AlertSignal(CHANNEL_FAILING_TYPE)
            .withDedupKey("channel:" + channelId)
            .withSeverity(AlertSeverity.ERROR)
            .withTitle("Alert channel \"" + channel.getValueString("label") + "\" is failing")
            .withSummary(failures + " deliveries in a row failed.")
            .withDetails(result.error())
            .withRecordRef(com.kingsrook.qbits.alerts.spi.AlertRecordRef.of(AlertsChannel.TABLE_NAME, channelId)));
      }
   }



   /*******************************************************************************
    ** The stand-in alert a TEST message describes.
    *******************************************************************************/
   private static QRecord testAlert(Instant now)
   {
      return (new QRecord().withValue("title", "Test notification").withValue("severity", AlertSeverity.INFO.name()).withValue("summary", "If you can read this, the channel works.")
         .withValue("firstRaisedAt", now));
   }



   /*******************************************************************************
    ** A row by id, or null (no exception), for caches.
    *******************************************************************************/
   private static QRecord quietly(String tableName, Integer id)
   {
      try
      {
         return (AlertsDb.byId(tableName, id));
      }
      catch(QException e)
      {
         return (null);
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String truncate(String text)
   {
      return (text == null || text.length() <= 1900 ? text : text.substring(0, 1900) + "…");
   }
}
