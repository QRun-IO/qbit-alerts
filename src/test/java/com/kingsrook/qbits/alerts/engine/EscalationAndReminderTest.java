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
import com.kingsrook.qbits.alerts.model.AlertsAlert;
import com.kingsrook.qbits.alerts.model.AlertsChannel;
import com.kingsrook.qbits.alerts.model.AlertsDelivery;
import com.kingsrook.qbits.alerts.model.AlertsEscalationPolicy;
import com.kingsrook.qbits.alerts.model.AlertsEscalationStep;
import com.kingsrook.qbits.alerts.model.AlertsEvent;
import com.kingsrook.qbits.alerts.model.AlertsGroup;
import com.kingsrook.qbits.alerts.model.AlertsMute;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.Resolution;
import com.kingsrook.qbits.alerts.model.AlertsSubscription;
import com.kingsrook.qbits.alerts.model.AlertsTeam;
import com.kingsrook.qbits.alerts.model.AlertsTeamMember;
import com.kingsrook.qbits.alerts.model.AlertsType;
import com.kingsrook.qbits.alerts.processes.AlertsScheduledSteps;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizerInterface;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizers;
import com.kingsrook.qqq.backend.core.alerts.AlertSeverity;
import com.kingsrook.qqq.backend.core.alerts.AlertSignal;
import com.kingsrook.qqq.backend.core.alerts.AlertsInstanceMetaData;
import com.kingsrook.qqq.backend.core.alerts.QAlertTypeMetaData;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Timed notifications against the real H2 tables and router, with a movable clock.
 *******************************************************************************/
class EscalationAndReminderTest extends BaseAlertsTest
{
   private static final String TYPE = "test.timed";



   /*******************************************************************************
    ** A normal declared alert type with a group.
    *******************************************************************************/
   @Override
   protected void customizeInstance(QInstance instance)
   {
      AlertsInstanceMetaData.ofOrWithNew(instance).withAlertType(new QAlertTypeMetaData()
         .withName(TYPE).withLabel("Timed alert").withGroupName("timed").withDefaultSeverity(AlertSeverity.ERROR));
   }



   /*******************************************************************************
    ** Raise and tune the reminder interval without initial notifications.
    *******************************************************************************/
   private QRecord alert(Integer interval, AlertSeverity severity) throws QException
   {
      QRecord alert = runtime.getEngine().raise(new AlertSignal(TYPE).withSeverity(severity));
      update(AlertsType.TABLE_NAME, new QRecord().withValue("id", alert.getValue("alertTypeId")).withValue("reminderIntervalMinutes", interval));
      return (alert);
   }



   /*******************************************************************************
    ** Store changes as the system, like the engine.
    *******************************************************************************/
   private void update(String table, QRecord record) throws QException
   {
      runtime.asSystem(() -> AlertsDb.update(table, record));
   }



   /*******************************************************************************
    ** Insert a test configuration row.
    *******************************************************************************/
   private QRecord insert(String table, QRecord record) throws QException
   {
      return (runtime.asSystem(() -> AlertsDb.insert(table, record)));
   }



   /*******************************************************************************
    ** One subscription with an explicit event filter.
    *******************************************************************************/
   private void subscribe(String user, String events) throws QException
   {
      insert(AlertsSubscription.TABLE_NAME, new QRecord().withValue("subscriberType", "USER").withValue("userId", user)
         .withValue("scopeType", "ALL").withValue("events", events).withValue("enabled", true));
   }



   /*******************************************************************************
    ** A policy attached to this alert's type.
    *******************************************************************************/
   private QRecord policy(QRecord alert, int repeatMinutes, int maxRepeats) throws QException
   {
      QRecord policy = insert(AlertsEscalationPolicy.TABLE_NAME, new QRecord().withValue("name", "On call")
         .withValue("enabled", true).withValue("repeatAfterMinutes", repeatMinutes).withValue("maxRepeats", maxRepeats));
      update(AlertsType.TABLE_NAME, new QRecord().withValue("id", alert.getValue("alertTypeId")).withValue("escalationPolicyId", policy.getValue("id")));
      return (policy);
   }



   /*******************************************************************************
    ** An explicit ordered target.
    *******************************************************************************/
   private QRecord step(QRecord policy, int order, int minutes, String targetType, String targetField, java.io.Serializable targetId) throws QException
   {
      return (insert(AlertsEscalationStep.TABLE_NAME, new QRecord().withValue("policyId", policy.getValue("id"))
         .withValue("stepOrder", order).withValue("afterMinutes", minutes).withValue("targetType", targetType)
         .withValue(targetField, targetId).withValue("onlyIfStatus", "OPEN")));
   }



   /*******************************************************************************
    ** Tick through the real hidden backend step.
    *******************************************************************************/
   private void tick() throws QException
   {
      new AlertsScheduledSteps.EscalateAndRemind().run(new RunBackendStepInput(), new RunBackendStepOutput());
   }



   /*******************************************************************************
    ** Read a refreshed alert.
    *******************************************************************************/
   private QRecord fresh(QRecord alert) throws QException
   {
      return (runtime.asSystem(() -> AlertsDb.byId(AlertsAlert.TABLE_NAME, alert.getValue("id"))));
   }



   /*******************************************************************************
    ** Due boundary, event filter, NOTIFIED timeline and same-minute idempotency.
    ** A one-minute interval also proves reminders bypass the ten-minute duplicate window.
    *******************************************************************************/
   @Test
   void testReminderDueAndNotDue() throws Exception
   {
      QRecord alert = alert(1, AlertSeverity.ERROR);
      subscribe("viewer-reminders", "REMINDER");
      subscribe("viewer-raised", "RAISED");
      clock.advance(Duration.ofSeconds(59));
      tick();
      assertThat(all(AlertsDelivery.TABLE_NAME)).isEmpty();
      clock.advance(Duration.ofSeconds(1));
      tick();
      tick();
      assertThat(all(AlertsDelivery.TABLE_NAME)).singleElement().satisfies(row ->
      {
         assertThat(row.getValueString("kind")).isEqualTo("REMINDER");
         assertThat(row.getValueString("recipientUserId")).isEqualTo("viewer-reminders");
         assertThat(row.getValueString("status")).isEqualTo("SENT");
      });
      assertThat(fresh(alert).getValueInstant("lastNotifiedAt")).isEqualTo(clock.instant());
      assertThat(all(AlertsEvent.TABLE_NAME)).extracting(row -> row.getValueString("eventType")).containsExactly("RAISED", "NOTIFIED");
      clock.advance(Duration.ofMinutes(1));
      tick();
      assertThat(all(AlertsDelivery.TABLE_NAME)).hasSize(2);
   }



   /*******************************************************************************
    ** The newer of last raise and last successful notification sets the due time.
    *******************************************************************************/
   @Test
   void testReminderUsesLatestRaiseOrNotification() throws Exception
   {
      QRecord alert = alert(10, AlertSeverity.ERROR);
      subscribe("viewer-reminders", "REMINDER");
      clock.advance(Duration.ofMinutes(8));
      runtime.getEngine().raise(new AlertSignal(TYPE));
      clock.advance(Duration.ofMinutes(2));
      tick();
      assertThat(all(AlertsDelivery.TABLE_NAME)).isEmpty();
      clock.advance(Duration.ofMinutes(8));
      tick();
      clock.advance(Duration.ofMinutes(9));
      tick();
      assertThat(all(AlertsDelivery.TABLE_NAME)).hasSize(1);
      clock.advance(Duration.ofMinutes(1));
      tick();
      assertThat(all(AlertsDelivery.TABLE_NAME)).hasSize(2);
      assertThat(fresh(alert).getValueInstant("lastNotifiedAt")).isEqualTo(clock.instant());
   }



   /*******************************************************************************
    ** CRITICAL has a 60-minute default; explicit zero still disables reminders.
    *******************************************************************************/
   @Test
   void testCriticalDefaultAndExplicitOff() throws Exception
   {
      QRecord alert = alert(null, AlertSeverity.CRITICAL);
      subscribe("viewer", "REMINDER");
      clock.advance(Duration.ofMinutes(59));
      tick();
      assertThat(all(AlertsDelivery.TABLE_NAME)).isEmpty();
      clock.advance(Duration.ofMinutes(1));
      tick();
      assertThat(all(AlertsDelivery.TABLE_NAME)).hasSize(1);
      update(AlertsType.TABLE_NAME, new QRecord().withValue("id", alert.getValue("alertTypeId")).withValue("reminderIntervalMinutes", 0));
      clock.advance(Duration.ofHours(2));
      tick();
      assertThat(all(AlertsDelivery.TABLE_NAME)).hasSize(1);
   }



   /*******************************************************************************
    ** Null is off for non-critical alerts.
    *******************************************************************************/
   @Test
   void testReminderDefaultOff() throws Exception
   {
      alert(null, AlertSeverity.ERROR);
      subscribe("viewer", "REMINDER");
      clock.advance(Duration.ofDays(1));
      tick();
      assertThat(all(AlertsDelivery.TABLE_NAME)).isEmpty();
   }



   /*******************************************************************************
    ** Every stopping condition blocks both kinds of timed notification.
    *******************************************************************************/
   @ParameterizedTest
   @ValueSource(strings = {"ACKNOWLEDGED", "SNOOZED", "RESOLVED", "muted", "disabled", "silent", "flapping", "SILENCE_NOTIFICATIONS", "SUPPRESS_ALERTS"})
   void testStops(String condition) throws Exception
   {
      QRecord alert = alert(1, AlertSeverity.ERROR);
      QRecord policy = policy(alert, 1, 2);
      step(policy, 1, 1, "USER", "userId", "viewer");
      subscribe("viewer", "REMINDER");
      switch(condition)
      {
         case "ACKNOWLEDGED" -> runtime.getEngine().acknowledge(alert.getValueInteger("id"), ADMIN);
         case "SNOOZED" -> runtime.getEngine().snooze(alert.getValueInteger("id"), clock.instant().plusSeconds(3600), ADMIN);
         case "RESOLVED" -> runtime.getEngine().resolveById(alert.getValueInteger("id"), Resolution.MANUAL, ADMIN, "Done");
         case "disabled", "silent" -> update(AlertsType.TABLE_NAME, new QRecord().withValue("id", alert.getValue("alertTypeId"))
            .withValue("disabled".equals(condition) ? "enabled" : "notify", false));
         case "muted", "flapping" -> update(AlertsAlert.TABLE_NAME, new QRecord().withValue("id", alert.getValue("id")).withValue(condition, true));
         default -> insert(AlertsMute.TABLE_NAME, new QRecord().withValue("name", "Maintenance").withValue("scopeType", "ALL")
            .withValue("enabled", true).withValue("effect", condition).withValue("startsAt", clock.instant()).withValue("endsAt", clock.instant().plusSeconds(3600)));
      }
      clock.advance(Duration.ofMinutes(2));
      tick();
      assertThat(all(AlertsDelivery.TABLE_NAME)).isEmpty();
      assertThat(fresh(alert).getValueInteger("escalationLevel")).isZero();
   }



   /*******************************************************************************
    ** Sequential steps and capped repeats, with the same target inside ten minutes.
    *******************************************************************************/
   @Test
   void testStepsRepeatsAndIdempotency() throws Exception
   {
      QRecord alert = alert(0, AlertSeverity.ERROR);
      QRecord policy = policy(alert, 2, 2);
      step(policy, 1, 1, "USER", "userId", "viewer");
      step(policy, 2, 3, "USER", "userId", "viewer");
      tick();
      assertThat(fresh(alert).getValueInteger("escalationLevel")).isZero();
      clock.advance(Duration.ofMinutes(1));
      tick();
      tick();
      assertThat(fresh(alert).getValueInteger("escalationLevel")).isEqualTo(1);
      clock.advance(Duration.ofMinutes(1));
      tick();
      assertThat(fresh(alert).getValueInteger("escalationLevel")).isEqualTo(1);
      clock.advance(Duration.ofMinutes(1));
      tick();
      assertThat(fresh(alert).getValueInteger("escalationLevel")).isEqualTo(2);
      clock.advance(Duration.ofMinutes(1));
      tick();
      assertThat(all(AlertsDelivery.TABLE_NAME)).hasSize(2);
      clock.advance(Duration.ofMinutes(1));
      tick();
      tick();
      clock.advance(Duration.ofMinutes(2));
      tick();
      clock.advance(Duration.ofDays(1));
      tick();
      assertThat(all(AlertsDelivery.TABLE_NAME)).hasSize(4).allSatisfy(row ->
      {
         assertThat(row.getValueString("status")).isEqualTo("SENT");
         assertThat(row.getValueString("kind")).isEqualTo("ESCALATED");
      });
      List<QRecord> events = where(AlertsEvent.TABLE_NAME, AlertsDb.eq("eventType", "ESCALATED"));
      assertThat(events).extracting(row -> row.getValueString("fromValue")).containsExactly("0", "1", "2", "2");
      assertThat(events).extracting(row -> row.getValueString("toValue")).containsExactly("1", "2", "2", "2");
   }



   /*******************************************************************************
    ** Acknowledging after step one stops the next step and reminders.
    *******************************************************************************/
   @Test
   void testAcknowledgeAfterFirstStep() throws Exception
   {
      QRecord alert = alert(1, AlertSeverity.ERROR);
      QRecord policy = policy(alert, 1, 5);
      step(policy, 1, 0, "USER", "userId", "viewer");
      step(policy, 2, 1, "USER", "userId", "viewer");
      subscribe("viewer", "REMINDER");
      tick();
      runtime.getEngine().acknowledge(alert.getValueInteger("id"), ADMIN);
      clock.advance(Duration.ofHours(2));
      tick();
      assertThat(all(AlertsDelivery.TABLE_NAME)).hasSize(1);
      assertThat(fresh(alert).getValueInteger("escalationLevel")).isEqualTo(1);
   }



   /*******************************************************************************
    ** Team expansion uses members; channel targets enqueue an external delivery.
    *******************************************************************************/
   @Test
   void testTeamAndChannelTargets() throws Exception
   {
      QRecord alert = alert(0, AlertSeverity.ERROR);
      QRecord policy = policy(alert, 0, 0);
      QRecord team = insert(AlertsTeam.TABLE_NAME, new QRecord().withValue("name", "oncall").withValue("label", "On call"));
      for(String user : List.of("viewer-one", "viewer-two"))
      {
         insert(AlertsTeamMember.TABLE_NAME, new QRecord().withValue("teamId", team.getValue("id")).withValue("userId", user));
      }
      QRecord channel = insert(AlertsChannel.TABLE_NAME, new QRecord().withValue("name", "chat").withValue("label", "Chat")
         .withValue("channelType", "SLACK").withValue("enabled", true));
      step(policy, 1, 0, "TEAM", "teamId", team.getValueInteger("id"));
      step(policy, 2, 1, "CHANNEL", "channelId", channel.getValueInteger("id"));
      tick();
      assertThat(all(AlertsDelivery.TABLE_NAME)).extracting(row -> row.getValueString("recipientUserId")).containsExactly("viewer-one", "viewer-two");
      clock.advance(Duration.ofMinutes(1));
      tick();
      assertThat(where(AlertsDelivery.TABLE_NAME, AlertsDb.eq("channelId", channel.getValue("id")))).singleElement().satisfies(row ->
      {
         assertThat(row.getValueString("kind")).isEqualTo("ESCALATED");
         assertThat(row.getValueString("status")).isEqualTo("PENDING");
      });
   }



   /*******************************************************************************
    ** A group policy applies only when a type has no override; disabled overrides stop it.
    *******************************************************************************/
   @Test
   void testGroupFallbackAndDisabledPolicy() throws Exception
   {
      QRecord alert = alert(0, AlertSeverity.ERROR);
      QRecord policy = policy(alert, 0, 0);
      step(policy, 1, 0, "USER", "userId", "viewer");
      update(AlertsGroup.TABLE_NAME, new QRecord().withValue("id", alert.getValue("groupId")).withValue("escalationPolicyId", policy.getValue("id")));
      QRecord disabled = insert(AlertsEscalationPolicy.TABLE_NAME, new QRecord().withValue("name", "Disabled").withValue("enabled", false));
      update(AlertsType.TABLE_NAME, new QRecord().withValue("id", alert.getValue("alertTypeId")).withValue("escalationPolicyId", disabled.getValue("id")));
      tick();
      assertThat(all(AlertsDelivery.TABLE_NAME)).isEmpty();
      update(AlertsType.TABLE_NAME, new QRecord().withValue("id", alert.getValue("alertTypeId")).withValue("escalationPolicyId", null));
      clock.advance(Duration.ofMinutes(1));
      tick();
      assertThat(all(AlertsDelivery.TABLE_NAME)).hasSize(1);
   }



   /*******************************************************************************
    ** Repeated raises do not postpone escalation; reopening starts a fresh cycle.
    *******************************************************************************/
   @Test
   void testReopenRestartsStepsAndRepeatBudget() throws Exception
   {
      QRecord alert = alert(0, AlertSeverity.ERROR);
      QRecord policy = policy(alert, 1, 1);
      step(policy, 1, 5, "USER", "userId", "viewer");
      clock.advance(Duration.ofMinutes(4));
      runtime.getEngine().raise(new AlertSignal(TYPE));
      clock.advance(Duration.ofMinutes(1));
      tick();
      clock.advance(Duration.ofMinutes(1));
      tick();
      runtime.getEngine().resolveById(alert.getValueInteger("id"), Resolution.MANUAL, ADMIN, "Done");
      clock.advance(Duration.ofMinutes(1));
      runtime.getEngine().reopenById(alert.getValueInteger("id"), ADMIN, "Back");
      assertThat(fresh(alert).getValueInteger("escalationLevel")).isZero();
      tick();
      clock.advance(Duration.ofMinutes(4));
      tick();
      assertThat(all(AlertsDelivery.TABLE_NAME)).hasSize(2);
      clock.advance(Duration.ofMinutes(1));
      tick();
      clock.advance(Duration.ofMinutes(1));
      tick();
      assertThat(all(AlertsDelivery.TABLE_NAME)).hasSize(4);
   }



   /*******************************************************************************
    ** An incompatible status never escalates, even though the alert is open.
    *******************************************************************************/
   @Test
   void testOnlyIfStatus() throws Exception
   {
      QRecord alert = alert(0, AlertSeverity.ERROR);
      QRecord policy = policy(alert, 0, 0);
      QRecord step = step(policy, 1, 0, "USER", "userId", "viewer");
      update(AlertsEscalationStep.TABLE_NAME, new QRecord().withValue("id", step.getValue("id")).withValue("onlyIfStatus", "ACKNOWLEDGED"));
      tick();
      assertThat(all(AlertsDelivery.TABLE_NAME)).isEmpty();
   }



   /*******************************************************************************
    ** Routing failures roll back the event, level, deliveries and minute checkpoint.
    *******************************************************************************/
   @Test
   void testRollbackAndRetry() throws Exception
   {
      QRecord alert = alert(0, AlertSeverity.ERROR);
      QRecord policy = policy(alert, 0, 0);
      step(policy, 1, 0, "USER", "userId", "viewer");
      QCodeReference original = qInstance.getTable(AlertsDelivery.TABLE_NAME).getCustomizers().put(TableCustomizers.PRE_INSERT_RECORD.getRole(), new QCodeReference(RejectDelivery.class));
      assertThatThrownBy(this::tick).isInstanceOf(QException.class);
      assertThat(where(AlertsEvent.TABLE_NAME, AlertsDb.eq("eventType", "ESCALATED"))).isEmpty();
      assertThat(fresh(alert).getValueInteger("escalationLevel")).isZero();
      assertThat(fresh(alert).getValueInstant("timedNotificationCheckedAt")).isNull();
      qInstance.getTable(AlertsDelivery.TABLE_NAME).getCustomizers().put(TableCustomizers.PRE_INSERT_RECORD.getRole(), original);
      tick();
      assertThat(all(AlertsDelivery.TABLE_NAME)).hasSize(1);
   }



   /*******************************************************************************
    ** Suppressed attempts do not repeat within a minute or claim a successful send.
    *******************************************************************************/
   @Test
   void testSuppressedReminderCheckpoint() throws Exception
   {
      QRecord alert = alert(1, AlertSeverity.ERROR);
      QRecord channel = insert(AlertsChannel.TABLE_NAME, new QRecord().withValue("name", "disabled-chat").withValue("label", "Chat")
         .withValue("channelType", "SLACK").withValue("enabled", false));
      insert(AlertsSubscription.TABLE_NAME, new QRecord().withValue("subscriberType", "CHANNEL").withValue("channelId", channel.getValue("id"))
         .withValue("scopeType", "ALL").withValue("events", "REMINDER").withValue("enabled", true));
      clock.advance(Duration.ofMinutes(1));
      tick();
      tick();
      assertThat(all(AlertsDelivery.TABLE_NAME)).singleElement().satisfies(row ->
      {
         assertThat(row.getValueString("status")).isEqualTo("SUPPRESSED");
         assertThat(row.getValueString("suppressedReason")).isEqualTo("CHANNEL_DISABLED");
      });
      assertThat(fresh(alert).getValueInstant("lastNotifiedAt")).isNull();
      assertThat(where(AlertsEvent.TABLE_NAME, AlertsDb.eq("eventType", "NOTIFIED"))).isEmpty();
   }



   /*******************************************************************************
    ** The scan crosses page boundaries without missing alerts or retaining all rows.
    *******************************************************************************/
   @Test
   void testAlertPages() throws Exception
   {
      QRecord first = alert(0, AlertSeverity.ERROR);
      for(int i = 1; i <= 100; i++)
      {
         runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("page-" + i));
      }
      tick();
      assertThat(all(AlertsAlert.TABLE_NAME)).hasSize(101).allSatisfy(row ->
         assertThat(row.getValueInstant("timedNotificationCheckedAt")).isEqualTo(clock.instant()));
      assertThat(fresh(first).getValueInteger("escalationLevel")).isZero();
   }



   /*******************************************************************************
    ** A deliberate storage failure for transaction verification.
    *******************************************************************************/
   public static class RejectDelivery implements TableCustomizerInterface
   {
      /***************************************************************************
       ** Reject before a delivery can be stored.
       ***************************************************************************/
      @Override
      public List<QRecord> preInsert(InsertInput input, List<QRecord> records, boolean isPreview) throws QException
      {
         throw (new QException("Deliberate test failure"));
      }
   }
}
