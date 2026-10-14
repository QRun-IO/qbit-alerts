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


import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.alerts.AlertsRuntime;
import com.kingsrook.qbits.alerts.engine.AlertTiming;
import com.kingsrook.qbits.alerts.engine.AlertsDb;
import com.kingsrook.qbits.alerts.model.AlertsAlert;
import com.kingsrook.qbits.alerts.model.AlertsChannel;
import com.kingsrook.qbits.alerts.model.AlertsDelivery;
import com.kingsrook.qbits.alerts.model.AlertsEvent;
import com.kingsrook.qbits.alerts.model.AlertsUserPreference;
import com.kingsrook.qqq.backend.core.alerts.AlertSeverity;
import com.kingsrook.qqq.backend.core.alerts.DetailLevel;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Sends completed subscription windows. A unique, durable delivery-log claim
 ** prevents concurrent schedulers or restarts from sending a window twice.
 ** An interrupted send is failed after its lease; uncertain sends are never
 ** automatically repeated. Messages and database reads have explicit bounds.
 *******************************************************************************/
public class DigestWorker
{
   static final int MAX_ITEMS = 500;
   private final AlertsRuntime runtime;



   /*******************************************************************************
    ** Constructor.
    *******************************************************************************/
   public DigestWorker(AlertsRuntime runtime)
   {
      this.runtime = runtime;
   }



   /*******************************************************************************
    ** Send up to 100 due windows. Returns the number of messages sent.
    *******************************************************************************/
   public int sendDue() throws QException
   {
      return (runtime.asSystem(() ->
      {
         Instant now = runtime.now();
         List<QRecord> due = AlertsDb.query(AlertsDelivery.TABLE_NAME, AlertsDb.eq("kind", "DIGEST")
            .withCriteria("status", QCriteriaOperator.EQUALS, "PENDING")
            .withCriteria("alertId", QCriteriaOperator.IS_NOT_BLANK)
            .withCriteria("nextAttemptAt", QCriteriaOperator.LESS_THAN_OR_EQUALS, now)
            .withOrderBy(new QFilterOrderBy("id")).withLimit(100));
         Set<String> seen = new HashSet<>();
         int sent = 0;
         for(QRecord first : due)
         {
            String claimKey = first.getValueString("digestKey") + ":channel:" + first.getValue("channelId");
            if(seen.add(claimKey))
            {
               sent += sendWindow(first, claimKey, now);
            }
         }
         return (sent);
      }));
   }



   /*******************************************************************************
    ** Claim before the side effect, then record the result on every item.
    *******************************************************************************/
   private int sendWindow(QRecord first, String claimKey, Instant now) throws QException
   {
      QRecord claim = AlertsDb.first(AlertsDelivery.TABLE_NAME, AlertsDb.eq("digestClaimKey", claimKey));
      if(claim != null)
      {
         if("SENDING".equals(claim.getValueString("status")))
         {
            if(claim.getValueInstant("claimedUntil") != null && claim.getValueInstant("claimedUntil").isAfter(now))
            {
               return (0);
            }
            claim = finish(claim, DeliveryResult.failed("Digest send was interrupted; delivery is uncertain. It will not be sent again automatically."), now);
         }
         if(!List.of("SENT", "FAILED", "DEAD", "SUPPRESSED").contains(claim.getValueString("status")))
         {
            claim = finish(claim, DeliveryResult.failed("A digest claim cannot be retried because its original delivery may already have succeeded."), now);
         }
         finishItems(first, claim);
         return (0);
      }
      QRecord last = AlertsDb.first(AlertsDelivery.TABLE_NAME, items(first).withOrderBy(new QFilterOrderBy("id", false)));
      if(last == null)
      {
         return (0);
      }
      Integer lastItemId = last.getValueInteger("id");
      try
      {
         claim = AlertsDb.insert(AlertsDelivery.TABLE_NAME, new QRecord()
            .withValue("kind", "DIGEST").withValue("status", "SENDING").withValue("attempts", 1)
            .withValue("digestKey", first.getValue("digestKey")).withValue("digestClaimKey", claimKey)
            .withValue("channelId", first.getValue("channelId")).withValue("recipientUserId", first.getValue("recipientUserId"))
            .withValue("recipientLabel", first.getValue("recipientLabel")).withValue("digestLastItemId", lastItemId).withValue("claimedUntil", now.plus(Duration.ofMinutes(10))));
      }
      catch(QException e)
      {
         if(AlertsDb.first(AlertsDelivery.TABLE_NAME, AlertsDb.eq("digestClaimKey", claimKey)) != null)
         {
            return (0);
         }
         throw (e);
      }

      DeliveryResult result;
      try
      {
         List<QRecord> rows = AlertsDb.query(AlertsDelivery.TABLE_NAME, items(first).withCriteria("id", QCriteriaOperator.LESS_THAN_OR_EQUALS, lastItemId).withLimit(MAX_ITEMS + 1));
         result = rows.size() > MAX_ITEMS ? DeliveryResult.failed("Digest exceeds the 500 item message limit. Narrow the subscription or use hourly digests.")
            : deliver(first, rows, claimKey, now);
      }
      catch(Exception e)
      {
         result = DeliveryResult.failed(AlertRedactor.redact(e.getClass().getSimpleName() + ": " + e.getMessage()));
      }
      claim = finish(claim, result, now);
      finishItems(first, claim);
      return ("SENT".equals(claim.getValueString("status")) ? 1 : 0);
   }



   /*******************************************************************************
    ** Render each distinct alert once. Event rows preserve raised/resolved
    ** sections even when the alert has subsequently changed state.
    *******************************************************************************/
   private DeliveryResult deliver(QRecord first, List<QRecord> rows, String claimKey, Instant now) throws QException
   {
      QRecord channel = AlertsDb.byId(AlertsChannel.TABLE_NAME, first.getValue("channelId"));
      if(channel == null || Boolean.FALSE.equals(channel.getValueBoolean("enabled")))
      {
         return (DeliveryResult.failed("The digest channel is missing or disabled."));
      }
      String userId = first.getValueString("recipientUserId");
      QRecord preference = userId == null ? null : AlertsDb.first(AlertsUserPreference.TABLE_NAME, AlertsDb.eq("userId", userId));
      boolean inApp = "IN_APP".equals(channel.getValueString("channelType"));
      Map<Integer, QRecord> alerts = new LinkedHashMap<>();
      for(QRecord row : rows)
      {
         Integer id = row.getValueInteger("alertId");
         QRecord alert = alerts.get(id);
         if(alert == null)
         {
            alert = AlertsDb.byId(AlertsAlert.TABLE_NAME, id);
            if(alert == null)
            {
               AlertsDb.update(AlertsDelivery.TABLE_NAME, new QRecord().withValue("id", row.getValue("id"))
                  .withValue("status", "FAILED").withValue("lastError", "The alert no longer exists."));
               continue;
            }
            String reason = null;
            if(userId != null && !runtime.getRouter().canReceiveDigest(userId, alert))
            {
               reason = "NOT_VISIBLE";
            }
            else if(!inApp && preference != null && preference.getValueInstant("pausedUntil") != null && preference.getValueInstant("pausedUntil").isAfter(now))
            {
               reason = "USER_PAUSED";
            }
            else if(!inApp && AlertTiming.isQuiet(preference, AlertSeverity.fromLabel(alert.getValueString("severity"), AlertSeverity.WARNING), now))
            {
               reason = "QUIET_HOURS";
            }
            if(reason != null)
            {
               AlertsDb.update(AlertsDelivery.TABLE_NAME, new QRecord().withValue("id", row.getValue("id"))
                  .withValue("status", "SUPPRESSED").withValue("suppressedReason", reason));
               continue;
            }
            alerts.put(id, alert);
         }
         QRecord event = AlertsDb.byId(AlertsEvent.TABLE_NAME, row.getValue("alertEventId"));
         String kind = event == null ? "" : event.getValueString("eventType");
         if(List.of("RAISED", "REOPENED").contains(kind))
         {
            alert.setValue("digestNew", true);
         }
         if("RESOLVED".equals(kind))
         {
            alert.setValue("digestResolved", true);
         }
      }
      if(alerts.isEmpty())
      {
         return (DeliveryResult.failed("No alerts remain in this digest window."));
      }
      List<AlertMessageRenderer.Item> rendered = new ArrayList<>();
      for(QRecord alert : alerts.values())
      {
         rendered.add(new AlertMessageRenderer.Item(alert, alert.getValueString("alertTypeName"), null, DetailLevel.MINIMAL));
      }
      AlertMessage message = AlertMessageRenderer.renderDigest(rendered, runtime.getConfig().getPublicBaseUrl(), now, claimKey);
      if(inApp)
      {
         runtime.getNotifications().deliverDigest(userId, message, now);
         return (DeliveryResult.sent(null));
      }
      AlertChannelInterface implementation = runtime.getChannels().forType(channel.getValueString("channelType"));
      if(implementation == null)
      {
         return (DeliveryResult.failed("No implementation for the digest channel."));
      }
      String address = preference == null ? null : preference.getValueString("emailAddress");
      return (implementation.deliver(message, channel, address, runtime.getChannels().getSecrets()));
   }



   /*******************************************************************************
    ** Pending source items, excluding the claim row.
    *******************************************************************************/
   private QQueryFilter items(QRecord first)
   {
      return (AlertsDb.eq("digestKey", first.getValue("digestKey"))
         .withCriteria("channelId", QCriteriaOperator.EQUALS, first.getValue("channelId"))
         .withCriteria("status", QCriteriaOperator.EQUALS, "PENDING")
         .withCriteria("alertId", QCriteriaOperator.IS_NOT_BLANK));
   }



   /*******************************************************************************
    ** A digest has one attempt; retryable channel errors are recorded as FAILED.
    *******************************************************************************/
   private QRecord finish(QRecord claim, DeliveryResult result, Instant now) throws QException
   {
      return (AlertsDb.update(AlertsDelivery.TABLE_NAME, new QRecord().withValue("id", claim.getValue("id"))
         .withValue("digestLastItemId", claim.getValue("digestLastItemId"))
         .withValue("status", result.outcome() == DeliveryResult.Outcome.SENT ? "SENT" : "FAILED")
         .withValue("sentAt", result.outcome() == DeliveryResult.Outcome.SENT ? now : null).withValue("claimedUntil", null)
         .withValue("providerMessageId", result.providerMessageId())
         .withValue("lastError", StringUtils.safeTruncate(AlertRedactor.redact(result.error()), 1900))));
   }



   /*******************************************************************************
    ** Page through the entire window, including overflow and late source rows.
    *******************************************************************************/
   private void finishItems(QRecord first, QRecord claim) throws QException
   {
      List<QRecord> page;
      do
      {
         page = AlertsDb.query(AlertsDelivery.TABLE_NAME, items(first).withLimit(200));
         for(QRecord row : page)
         {
            boolean late = claim.getValueInteger("digestLastItemId") != null && row.getValueInteger("id") > claim.getValueInteger("digestLastItemId");
            AlertsDb.update(AlertsDelivery.TABLE_NAME, new QRecord().withValue("id", row.getValue("id"))
               .withValue("status", late ? "SUPPRESSED" : claim.getValue("status")).withValue("attempts", late ? 0 : 1)
               .withValue("suppressedReason", late ? "DUPLICATE" : null).withValue("sentAt", late ? null : claim.getValue("sentAt"))
               .withValue("lastError", late ? "The digest window was already claimed." : claim.getValue("lastError"))
               .withValue("providerMessageId", late ? null : claim.getValue("providerMessageId")));
         }
      }
      while(!page.isEmpty());
   }
}
