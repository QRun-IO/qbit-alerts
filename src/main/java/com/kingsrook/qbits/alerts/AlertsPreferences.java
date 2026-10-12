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

package com.kingsrook.qbits.alerts;


import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.kingsrook.qbits.alerts.engine.AlertsDb;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.ChannelType;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.DeliveryMode;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.ScopeType;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.SubscriberType;
import com.kingsrook.qbits.alerts.model.AlertsSubscription;
import com.kingsrook.qbits.alerts.model.AlertsUserPreference;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.session.QSession;


/*******************************************************************************
 ** First visit set-up for a user (the home, the bell or the record panel):
 ** - My Alert Settings is created with defaults (in-app on; email on once
 **   they add an address);
 ** - the bootstrap rule (design 5.4): an alerts.admin holder with no
 **   subscriptions gets one in-app subscription to everything at the
 **   configured minimum severity (ERROR by default). With no subscriptions
 **   otherwise, a fresh install notifies nobody.
 *******************************************************************************/
public final class AlertsPreferences
{
   private static final Set<String> ENSURED = ConcurrentHashMap.newKeySet();



   /*******************************************************************************
    ** Set-up only.
    *******************************************************************************/
   private AlertsPreferences()
   {
   }



   /*******************************************************************************
    ** Make sure the session's user has settings (and, for admins, the
    ** bootstrap subscription). Cheap after the first call per user.
    *******************************************************************************/
   public static void ensure(AlertsRuntime runtime, QSession session) throws QException
   {
      String userId = AlertsSecurity.userId(session);
      if(userId == null || !session.hasPermission(AlertsQBitConfig.PERMISSION_VIEW) || ENSURED.contains(userId))
      {
         return;
      }

      boolean admin    = session.hasPermission(AlertsQBitConfig.PERMISSION_ADMIN);
      String  fullName = session.getUser() == null ? null : session.getUser().getFullName();
      runtime.asSystem(() ->
      {
         if(AlertsDb.first(AlertsUserPreference.TABLE_NAME, AlertsDb.eq("userId", userId)) == null)
         {
            AlertsDb.insert(AlertsUserPreference.TABLE_NAME, new QRecord()
               .withValue("userId", userId)
               .withValue("userLabel", fullName == null ? userId : fullName)
               .withValue("inAppEnabled", true)
               .withValue("emailEnabled", true)
               .withValue("quietBypassSeverity", "CRITICAL")
               .withValue("digestHour", 8));
         }

         if(admin && runtime.getConfig().getBootstrapMinSeverity() != null && AlertsDb.first(AlertsSubscription.TABLE_NAME, AlertsDb.eq("userId", userId)) == null)
         {
            QRecord inApp = runtime.getChannels().defaultChannel(ChannelType.IN_APP);
            AlertsDb.insert(AlertsSubscription.TABLE_NAME, new QRecord()
               .withValue("subscriberType", SubscriberType.USER.name())
               .withValue("userId", userId)
               .withValue("scopeType", ScopeType.ALL.name())
               .withValue("minSeverity", runtime.getConfig().getBootstrapMinSeverity().name())
               .withValue("events", "RAISED,ESCALATED,REMINDER,RESOLVED")
               .withValue("deliveryMode", DeliveryMode.IMMEDIATE.name())
               .withValue("channelOverrideIds", String.valueOf(inApp.getValue("id")))
               .withValue("enabled", true)
               .withValue("createdByUserId", "alerts (bootstrap rule)"));
         }
         return (null);
      });
      ENSURED.add(userId);
   }



   /*******************************************************************************
    ** Forget who was set up (tests).
    *******************************************************************************/
   public static void reset()
   {
      ENSURED.clear();
   }
}
