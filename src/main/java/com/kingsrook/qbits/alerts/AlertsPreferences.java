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


import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.kingsrook.qbits.alerts.engine.AlertRouter;
import com.kingsrook.qbits.alerts.engine.AlertsDb;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.ChannelType;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.DeliveryMode;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.ScopeType;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.SubscriberType;
import com.kingsrook.qbits.alerts.model.AlertsSubscription;
import com.kingsrook.qbits.alerts.model.AlertsUserPreference;
import com.kingsrook.qqq.backend.core.actions.tables.DeleteAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
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
   /** marks the bootstrap rule's subscription (createdByUserId). */
   static final String BOOTSTRAP_CREATOR = "alerts (bootstrap rule)";

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

      ////////////////////////////////////////////////////////////////////////////
      // A page load fires the home widgets and the bell together, so first     //
      // calls for one user race. Serialize them in this JVM; across replicas,  //
      // the clean-up below keeps one bootstrap subscription (the lowest id).   //
      ////////////////////////////////////////////////////////////////////////////
      synchronized(AlertsPreferences.class)
      {
         if(ENSURED.contains(userId))
         {
            return;
         }
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
                  .withValue("events", AlertRouter.DEFAULT_SUBSCRIPTION_EVENTS)
                  .withValue("deliveryMode", DeliveryMode.IMMEDIATE.name())
                  .withValue("channelOverrideIds", String.valueOf(inApp.getValue("id")))
                  .withValue("enabled", true)
                  .withValue("createdByUserId", BOOTSTRAP_CREATOR));
            }

            removeDuplicateBootstrapSubscriptions(userId);
            return (null);
         });
         ENSURED.add(userId);
      }
   }



   /*******************************************************************************
    ** Keep only the first bootstrap subscription of a user. Two replicas can
    ** each see no subscription and insert one; every later first call (and the
    ** losers' own calls) delete the extra copies, so the user is not notified
    ** twice per alert. Subscriptions a person made are never touched.
    *******************************************************************************/
   static void removeDuplicateBootstrapSubscriptions(String userId) throws QException
   {
      List<QRecord> bootstrap = AlertsDb.query(AlertsSubscription.TABLE_NAME, AlertsDb.eq("userId", userId)
         .withCriteria("createdByUserId", QCriteriaOperator.EQUALS, BOOTSTRAP_CREATOR)
         .withOrderBy(new QFilterOrderBy("id")));
      if(bootstrap.size() < 2)
      {
         return;
      }
      List<Serializable> extra = bootstrap.subList(1, bootstrap.size()).stream().map(r -> (Serializable) r.getValue("id")).toList();
      new DeleteAction().execute(new DeleteInput(AlertsSubscription.TABLE_NAME).withPrimaryKeys(extra).withTransaction(QContext.getQBackendTransaction()));
   }



   /*******************************************************************************
    ** Forget who was set up (tests).
    *******************************************************************************/
   public static void reset()
   {
      ENSURED.clear();
   }
}
