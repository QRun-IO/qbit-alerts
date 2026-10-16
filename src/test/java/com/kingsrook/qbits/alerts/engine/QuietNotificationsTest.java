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
import com.kingsrook.qbits.alerts.BaseAlertsTest;
import com.kingsrook.qbits.alerts.model.AlertsDelivery;
import com.kingsrook.qbits.alerts.model.AlertsNotification;
import com.kingsrook.qbits.alerts.model.AlertsNotificationRecipient;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.Resolution;
import com.kingsrook.qbits.alerts.model.AlertsSubscription;
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
 ** resolve notices on purpose), and a reopen reuses the same bell entry.
 *******************************************************************************/
class QuietNotificationsTest extends BaseAlertsTest
{
   static final String TYPE = "test.widget.broken";



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   protected void customizeInstance(QInstance qInstance)
   {
      AlertsInstanceMetaData.ofOrWithNew(qInstance)
         .withAlertType(new QAlertTypeMetaData().withName(TYPE).withLabel("Widget broken").withGroupName("widgets").withDefaultSeverity(AlertSeverity.ERROR));
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
