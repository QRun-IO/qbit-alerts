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

package com.kingsrook.qbits.alerts.engine;


import java.time.Duration;
import java.util.List;
import com.kingsrook.qbits.alerts.AlertsQBitConfig;
import com.kingsrook.qbits.alerts.BaseAlertsTest;
import com.kingsrook.qbits.alerts.model.AlertsAlert;
import com.kingsrook.qbits.alerts.model.AlertsDelivery;
import com.kingsrook.qbits.alerts.model.AlertsNotification;
import com.kingsrook.qbits.alerts.model.AlertsNotificationRecipient;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.Resolution;
import com.kingsrook.qbits.alerts.model.AlertsSubscription;
import com.kingsrook.qbits.alerts.model.AlertsType;
import com.kingsrook.qbits.alerts.notifications.NotificationService;
import com.kingsrook.qqq.backend.core.actions.tables.DeleteAction;
import com.kingsrook.qqq.backend.core.alerts.AlertSeverity;
import com.kingsrook.qqq.backend.core.alerts.AlertSignal;
import com.kingsrook.qqq.backend.core.alerts.AlertsInstanceMetaData;
import com.kingsrook.qqq.backend.core.alerts.QAlertTypeMetaData;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Notifications people should see or act on: a resolve updates the bell
 ** entry without marking it unread (unless the subscription asked for
 ** resolve notices on purpose), a reopen reuses the same bell entry, and
 ** health types reopen within six hours instead of 30 minutes.
 *******************************************************************************/
class QuietNotificationsTest extends BaseAlertsTest
{
   static final String TYPE   = "test.widget.broken";
   static final String HEALTH = "health.database.degraded";



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   protected void customizeInstance(QInstance qInstance)
   {
      AlertsInstanceMetaData.ofOrWithNew(qInstance)
         .withAlertType(new QAlertTypeMetaData().withName(TYPE).withLabel("Widget broken").withGroupName("widgets").withDefaultSeverity(AlertSeverity.ERROR))
         .withAlertType(new QAlertTypeMetaData().withName(HEALTH).withLabel("Database degraded").withGroupName("health").withDefaultSeverity(AlertSeverity.ERROR));
   }



   /*******************************************************************************
    ** A resolve keeps the bell entry read, marks it resolved, and adds nothing.
    *******************************************************************************/
   @Test
   void testResolveDoesNotMarkTheBellUnread() throws Exception
   {
      subscribe(ADMIN, AlertRouter.DEFAULT_SUBSCRIPTION_EVENTS);
      QRecord alert = runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("q1").withTitle("Widget q1 is broken"));
      Integer id    = alert.getValueInteger("id");

      QRecord recipient = recipient(ADMIN);
      assertThat(recipient.getValue("readAt")).isNull();
      markRead(recipient);

      clock.advance(Duration.ofMinutes(5));
      runtime.getEngine().resolve(TYPE, "q1", Resolution.PRODUCER, null, "fixed");

      assertThat(all(AlertsNotification.TABLE_NAME)).singleElement()
         .matches(n -> (NotificationService.RESOLVED_PREFIX + "Widget q1 is broken").equals(n.getValueString("title")));
      assertThat(all(AlertsNotificationRecipient.TABLE_NAME)).hasSize(1);
      assertThat(recipient(ADMIN).getValue("readAt")).isNotNull();

      ////////////////////////////////////////////////////////////////////
      // a reopen is news again: same bell entry, unread, title restored //
      // (past the router's 10-minute duplicate window)                  //
      ////////////////////////////////////////////////////////////////////
      clock.advance(Duration.ofMinutes(5));
      ageDeliveries();
      QRecord reopened = runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("q1").withTitle("Widget q1 is broken"));
      assertThat(reopened.getValueInteger("id")).isEqualTo(id);
      assertThat(all(AlertsNotification.TABLE_NAME)).singleElement().matches(n -> "Widget q1 is broken".equals(n.getValueString("title")));
      assertThat(all(AlertsNotificationRecipient.TABLE_NAME)).hasSize(1);
      assertThat(recipient(ADMIN).getValue("readAt")).isNull();
   }



   /*******************************************************************************
    ** An unread entry stays unread through a resolve, and a resolve never adds
    ** an entry for someone whose bell did not have the alert.
    *******************************************************************************/
   @Test
   void testResolveLeavesUnreadAndAddsNothing() throws Exception
   {
      subscribe(ADMIN, "");
      runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("q2").withTitle("Widget q2 is broken"));
      runtime.asSystem(() -> AlertsDb.insert(AlertsSubscription.TABLE_NAME, new QRecord().withValue("subscriberType", "USER").withValue("userId", MANAGER)
         .withValue("scopeType", "ALL").withValue("minSeverity", "WARNING").withValue("events", AlertRouter.DEFAULT_SUBSCRIPTION_EVENTS)
         .withValue("deliveryMode", "IMMEDIATE").withValue("enabled", true)));

      runtime.getEngine().resolve(TYPE, "q2", Resolution.PRODUCER, null, null);
      assertThat(recipient(ADMIN).getValue("readAt")).isNull();
      assertThat(all(AlertsNotificationRecipient.TABLE_NAME)).extracting(r -> r.getValueString("userId")).containsExactly(ADMIN);
   }



   /*******************************************************************************
    ** A subscription that chose resolve notices on purpose keeps the old
    ** behavior: the resolve brings the entry back to unread.
    *******************************************************************************/
   @Test
   void testExplicitResolveSubscriptionMarksUnread() throws Exception
   {
      subscribe(ADMIN, "RAISED,RESOLVED");
      runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("q3").withTitle("Widget q3 is broken"));
      markRead(recipient(ADMIN));

      runtime.getEngine().resolve(TYPE, "q3", Resolution.PRODUCER, null, null);
      assertThat(recipient(ADMIN).getValue("readAt")).isNull();
      assertThat(all(AlertsNotification.TABLE_NAME)).singleElement().matches(n -> "Widget q3 is broken".equals(n.getValueString("title")));
   }



   /*******************************************************************************
    ** A reopen inside the duplicate window is not a new notice, but the bell
    ** entry no longer reads "Resolved".
    *******************************************************************************/
   @Test
   void testQuickReopenRestoresTheTitle() throws Exception
   {
      subscribe(ADMIN, AlertRouter.DEFAULT_SUBSCRIPTION_EVENTS);
      runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("q4").withTitle("Widget q4 is broken"));
      markRead(recipient(ADMIN));
      runtime.getEngine().resolve(TYPE, "q4", Resolution.PRODUCER, null, null);
      runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("q4").withTitle("Widget q4 is broken"));

      assertThat(all(AlertsNotification.TABLE_NAME)).singleElement().matches(n -> "Widget q4 is broken".equals(n.getValueString("title")));
      assertThat(recipient(ADMIN).getValue("readAt")).isNotNull();
   }



   /*******************************************************************************
    ** A health blip hours after the last one reopens the same alert and bell
    ** entry instead of adding a second "Database is degraded".
    *******************************************************************************/
   @Test
   void testHealthBlipReopensWithinSixHours() throws Exception
   {
      subscribe(ADMIN, AlertRouter.DEFAULT_SUBSCRIPTION_EVENTS);
      QRecord first = runtime.getEngine().raise(new AlertSignal(HEALTH).withDedupKey("db1").withTitle("Database db1 is degraded"));
      runtime.getEngine().resolve(HEALTH, "db1", Resolution.PRODUCER, null, null);
      markRead(recipient(ADMIN));

      clock.advance(Duration.ofHours(2));
      ageDeliveries();
      QRecord again = runtime.getEngine().raise(new AlertSignal(HEALTH).withDedupKey("db1").withTitle("Database db1 is degraded"));
      assertThat(again.getValue("id")).isEqualTo(first.getValue("id"));
      assertThat(again.getValueInteger("reopenCount")).isEqualTo(1);
      assertThat(all(AlertsAlert.TABLE_NAME)).hasSize(1);
      assertThat(all(AlertsNotification.TABLE_NAME)).singleElement().matches(n -> "Database db1 is degraded".equals(n.getValueString("title")));
      assertThat(recipient(ADMIN).getValue("readAt")).isNull();

      ////////////////////////////////////////////////////////
      // past six hours it is a new problem: a new alert   //
      ////////////////////////////////////////////////////////
      runtime.getEngine().resolve(HEALTH, "db1", Resolution.PRODUCER, null, null);
      clock.advance(Duration.ofHours(7));
      QRecord fresh = runtime.getEngine().raise(new AlertSignal(HEALTH).withDedupKey("db1").withTitle("Database db1 is degraded"));
      assertThat(fresh.getValue("id")).isNotEqualTo(first.getValue("id"));

      //////////////////////////////////////////////////////////////
      // other types keep the 30-minute window                    //
      //////////////////////////////////////////////////////////////
      QRecord widget = runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("w9"));
      runtime.getEngine().resolve(TYPE, "w9", Resolution.PRODUCER, null, null);
      clock.advance(Duration.ofHours(2));
      assertThat(runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("w9")).getValue("id")).isNotEqualTo(widget.getValue("id"));
   }



   /*******************************************************************************
    ** A type's own reopen window wins over the default for its name.
    *******************************************************************************/
   @Test
   void testPerTypeReopenWindow() throws Exception
   {
      QRecord widget = runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("w8"));
      runtime.asSystem(() -> AlertsDb.update(AlertsType.TABLE_NAME, new QRecord().withValue("id", widget.getValue("alertTypeId")).withValue("reopenWindowMinutes", 240)));
      runtime.getEngine().resolve(TYPE, "w8", Resolution.PRODUCER, null, null);
      clock.advance(Duration.ofHours(3));
      assertThat(runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("w8")).getValue("id")).isEqualTo(widget.getValue("id"));

      QRecord health = runtime.getEngine().raise(new AlertSignal(HEALTH).withDedupKey("db2"));
      runtime.asSystem(() -> AlertsDb.update(AlertsType.TABLE_NAME, new QRecord().withValue("id", health.getValue("alertTypeId")).withValue("reopenWindowMinutes", 10)));
      runtime.getEngine().resolve(HEALTH, "db2", Resolution.PRODUCER, null, null);
      clock.advance(Duration.ofMinutes(20));
      assertThat(runtime.getEngine().raise(new AlertSignal(HEALTH).withDedupKey("db2")).getValue("id")).isNotEqualTo(health.getValue("id"));
   }



   /*******************************************************************************
    ** Default windows by name prefix: the longest match wins.
    *******************************************************************************/
   @Test
   void testReopenWindowByTypePrefix()
   {
      AlertsQBitConfig defaults = new AlertsQBitConfig();
      assertThat(defaults.reopenWindowMinutesFor("health.database.degraded")).isEqualTo(360);
      assertThat(defaults.reopenWindowMinutesFor("test.widget.broken")).isEqualTo(30);
      assertThat(defaults.reopenWindowMinutesFor(null)).isEqualTo(30);

      AlertsQBitConfig custom = new AlertsQBitConfig().withReopenWindowMinutes(15).withReopenWindowMinutesForTypePrefix("health.database.", 60);
      assertThat(custom.reopenWindowMinutesFor("health.database.degraded")).isEqualTo(60);
      assertThat(custom.reopenWindowMinutesFor("health.queue.slow")).isEqualTo(360);
      assertThat(custom.reopenWindowMinutesFor("other")).isEqualTo(15);
      assertThat(new AlertsQBitConfig().withReopenWindowMinutesByTypePrefix(null).reopenWindowMinutesFor("health.x")).isEqualTo(30);

      List<String> errors = new java.util.ArrayList<>();
      new AlertsQBitConfig().withBackendName(BACKEND_NAME).withReopenWindowMinutesForTypePrefix("health.", 0).validate(qInstance, errors);
      assertThat(errors).anyMatch(e -> e.contains("reopen windows by type prefix"));
   }



   /*******************************************************************************
    ** Which event lists count as an explicit choice of resolve notices.
    *******************************************************************************/
   @Test
   void testExplicitlyWantsResolved()
   {
      assertThat(AlertRouter.explicitlyWantsResolved(null)).isFalse();
      assertThat(AlertRouter.explicitlyWantsResolved("")).isFalse();
      assertThat(AlertRouter.explicitlyWantsResolved(AlertRouter.DEFAULT_SUBSCRIPTION_EVENTS)).isFalse();
      assertThat(AlertRouter.explicitlyWantsResolved("resolved, reminder ,ESCALATED,raised")).isFalse();
      assertThat(AlertRouter.explicitlyWantsResolved("RAISED,ESCALATED")).isFalse();
      assertThat(AlertRouter.explicitlyWantsResolved("RAISED,RESOLVED")).isTrue();
      assertThat(AlertRouter.explicitlyWantsResolved("RESOLVED")).isTrue();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private void subscribe(String userId, String events) throws QException
   {
      runtime.asSystem(() -> AlertsDb.insert(AlertsSubscription.TABLE_NAME, new QRecord().withValue("subscriberType", "USER").withValue("userId", userId)
         .withValue("scopeType", "ALL").withValue("minSeverity", "WARNING").withValue("events", events)
         .withValue("deliveryMode", "IMMEDIATE").withValue("enabled", true)));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private QRecord recipient(String userId) throws QException
   {
      List<QRecord> rows = where(AlertsNotificationRecipient.TABLE_NAME, AlertsDb.eq("userId", userId));
      assertThat(rows).hasSize(1);
      return (rows.get(0));
   }



   /*******************************************************************************
    ** Move past the router's duplicate window. Its check reads createDate,
    ** which is wall-clock time, so the earlier deliveries are cleared instead.
    *******************************************************************************/
   private void ageDeliveries() throws QException
   {
      runtime.asSystem(() -> new DeleteAction().execute(new DeleteInput(AlertsDelivery.TABLE_NAME)
         .withQueryFilter(new QQueryFilter(new QFilterCriteria("id", QCriteriaOperator.IS_NOT_BLANK)))));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private void markRead(QRecord recipient) throws QException
   {
      runtime.asSystem(() -> AlertsDb.update(AlertsNotificationRecipient.TABLE_NAME, new QRecord().withValue("id", recipient.getValue("id")).withValue("readAt", clock.instant())));
   }
}
