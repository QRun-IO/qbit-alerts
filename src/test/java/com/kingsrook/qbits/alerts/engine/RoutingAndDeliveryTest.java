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
import java.util.Set;
import com.kingsrook.qbits.alerts.AlertsPreferences;
import com.kingsrook.qbits.alerts.AlertsQBitConfig;
import com.kingsrook.qbits.alerts.BaseAlertsTest;
import com.kingsrook.qbits.alerts.TestSteps;
import com.kingsrook.qbits.alerts.delivery.AlertChannels;
import com.kingsrook.qbits.alerts.delivery.DeliveryResult;
import com.kingsrook.qbits.alerts.delivery.DeliveryWorker;
import com.kingsrook.qbits.alerts.model.AlertsAlert;
import com.kingsrook.qbits.alerts.model.AlertsChannel;
import com.kingsrook.qbits.alerts.model.AlertsDelivery;
import com.kingsrook.qbits.alerts.model.AlertsMute;
import com.kingsrook.qbits.alerts.model.AlertsNotification;
import com.kingsrook.qbits.alerts.model.AlertsNotificationRecipient;
import com.kingsrook.qbits.alerts.model.AlertsSubscription;
import com.kingsrook.qbits.alerts.model.AlertsTeam;
import com.kingsrook.qbits.alerts.model.AlertsTeamMember;
import com.kingsrook.qbits.alerts.model.AlertsType;
import com.kingsrook.qbits.alerts.model.AlertsUserPreference;
import com.kingsrook.qqq.backend.core.alerts.AlertRecordRef;
import com.kingsrook.qqq.backend.core.alerts.AlertSeverity;
import com.kingsrook.qqq.backend.core.alerts.AlertSignal;
import com.kingsrook.qqq.backend.core.alerts.AlertsInstanceMetaData;
import com.kingsrook.qqq.backend.core.alerts.QAlertTypeMetaData;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Routing (scopes, teams, channels, the bootstrap rule, every suppression
 ** reason) and delivery (rendering, retries, dead, channel health, tests).
 *******************************************************************************/
class RoutingAndDeliveryTest extends BaseAlertsTest
{
   static final String TYPE   = "test.widget.broken";
   static final String SYSTEM = "test.system.down";



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   protected void customizeConfig(AlertsQBitConfig config)
   {
      config.withChannelType(new QCodeReference(TestSteps.RecordingChannel.class));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   protected void customizeInstance(QInstance qInstance)
   {
      AlertsInstanceMetaData.ofOrWithNew(qInstance)
         .withAlertType(new QAlertTypeMetaData().withName(TYPE).withLabel("Widget broken").withGroupName("widgets").withSubjectTableName(WIDGET).withDefaultSeverity(AlertSeverity.ERROR))
         .withAlertType(new QAlertTypeMetaData().withName(SYSTEM).withLabel("System down").withGroupName("infrastructure").withDefaultSeverity(AlertSeverity.CRITICAL));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @BeforeEach
   void reset()
   {
      TestSteps.SENT.clear();
      TestSteps.nextOutcome = DeliveryResult.Outcome.SENT;
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private QRecord subscribe(String subscriberType, String userId, Integer teamId, Integer channelId, String scopeType, String scopeValue, String scopeRecordId, String minSeverity) throws QException
   {
      return (runtime.asSystem(() -> AlertsDb.insert(AlertsSubscription.TABLE_NAME, new QRecord()
         .withValue("subscriberType", subscriberType).withValue("userId", userId).withValue("teamId", teamId).withValue("channelId", channelId)
         .withValue("scopeType", scopeType).withValue("scopeValue", scopeValue).withValue("scopeRecordId", scopeRecordId)
         .withValue("minSeverity", minSeverity).withValue("events", "RAISED,RESOLVED").withValue("deliveryMode", "IMMEDIATE").withValue("enabled", true))));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private QRecord chatChannel(String name) throws QException
   {
      return (runtime.asSystem(() -> AlertsDb.insert(AlertsChannel.TABLE_NAME, new QRecord().withValue("name", name).withValue("label", "Chat " + name)
         .withValue("channelType", "SLACK").withValue("enabled", true).withValue("detailLevel", "STANDARD").withValue("rateLimitPerHour", 60)
         .withValue("healthStatus", "HEALTHY").withValue("consecutiveFailures", 0))));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private List<QRecord> notifications() throws QException
   {
      return (all(AlertsDelivery.TABLE_NAME));
   }



   /*******************************************************************************
    ** First calls for one user race (the home widgets and the bell load
    ** together): only one bootstrap subscription is created.
    *******************************************************************************/
   @Test
   void testConcurrentFirstCallsBootstrapOnce() throws Exception
   {
      com.kingsrook.qqq.backend.core.context.CapturedContext context = QContext.capture();
      java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4);
      try
      {
         java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
         List<java.util.concurrent.Future<Object>> futures = new java.util.ArrayList<>();
         for(int i = 0; i < 4; i++)
         {
            futures.add(pool.submit(() ->
            {
               QContext.init(context);
               start.await();
               AlertsPreferences.ensure(runtime, QContext.getQSession());
               return (null);
            }));
         }
         start.countDown();
         for(java.util.concurrent.Future<Object> future : futures)
         {
            future.get();
         }
      }
      finally
      {
         pool.shutdownNow();
      }
      assertThat(all(AlertsSubscription.TABLE_NAME)).hasSize(1);
      assertThat(all(AlertsUserPreference.TABLE_NAME)).hasSize(1);
   }



   /*******************************************************************************
    ** Another replica raced and left a second bootstrap subscription: the next
    ** first call keeps the oldest and leaves a person's own subscriptions alone.
    *******************************************************************************/
   @Test
   void testDuplicateBootstrapSubscriptionsAreRemoved() throws Exception
   {
      for(String creator : List.of("alerts (bootstrap rule)", "alerts (bootstrap rule)", ADMIN))
      {
         runtime.asSystem(() -> AlertsDb.insert(AlertsSubscription.TABLE_NAME, new QRecord()
            .withValue("subscriberType", "USER")
            .withValue("userId", ADMIN)
            .withValue("scopeType", "ALL")
            .withValue("minSeverity", "ERROR")
            .withValue("events", "RAISED")
            .withValue("deliveryMode", "IMMEDIATE")
            .withValue("enabled", true)
            .withValue("createdByUserId", creator)));
      }
      List<QRecord> before = all(AlertsSubscription.TABLE_NAME);
      assertThat(before).hasSize(3);

      AlertsPreferences.ensure(runtime, QContext.getQSession());

      List<QRecord> after = all(AlertsSubscription.TABLE_NAME);
      assertThat(after).hasSize(2);
      assertThat(after).extracting(r -> r.getValueInteger("id")).containsExactlyInAnyOrder(before.get(0).getValueInteger("id"), before.get(2).getValueInteger("id"));
   }



   /*******************************************************************************
    ** No subscriptions: nobody is notified. The bootstrap rule gives admins an
    ** in-app subscription to ERROR and worse on first visit.
    *******************************************************************************/
   @Test
   void testDefaultsAndBootstrap() throws Exception
   {
      runtime.getEngine().raise(new AlertSignal(SYSTEM).withTitle("Down"));
      assertThat(notifications()).isEmpty();

      AlertsPreferences.ensure(runtime, QContext.getQSession());
      AlertsPreferences.ensure(runtime, QContext.getQSession());
      List<QRecord> subscriptions = all(AlertsSubscription.TABLE_NAME);
      assertThat(subscriptions).hasSize(1);
      assertThat(subscriptions.get(0).getValueString("minSeverity")).isEqualTo("ERROR");
      assertThat(all(AlertsUserPreference.TABLE_NAME)).hasSize(1);

      runtime.getEngine().raise(new AlertSignal(SYSTEM).withDedupKey("2").withTitle("Down again"));
      List<QRecord> sent = notifications();
      assertThat(sent).hasSize(1);
      assertThat(sent.get(0).getValueString("status")).isEqualTo("SENT");
      assertThat(sent.get(0).getValueString("recipientLabel")).isEqualTo("Name of admin-1 (in app)");
      assertThat(all(AlertsNotificationRecipient.TABLE_NAME)).hasSize(1);
      assertThat(all(AlertsNotification.TABLE_NAME)).singleElement().matches(n -> "ALERT".equals(n.getValueString("category")));

      ////////////////////////////////////////////////////////////////
      // a viewer gets settings but no bootstrap subscription       //
      ////////////////////////////////////////////////////////////////
      loginAs(VIEWER);
      AlertsPreferences.ensure(runtime, QContext.getQSession());
      assertThat(all(AlertsSubscription.TABLE_NAME)).hasSize(1);
      assertThat(all(AlertsUserPreference.TABLE_NAME)).hasSize(2);
   }



   /*******************************************************************************
    ** Scopes: type, group, table, record, attribute, monitor; min severity;
    ** event kinds; teams expand to members.
    *******************************************************************************/
   @Test
   void testScopesAndTeams() throws Exception
   {
      List<Integer> widgets = insertWidgets("BROKEN", "OK");
      subscribe("USER", "u-type", null, null, "ALERT_TYPE", TYPE, null, "WARNING");
      subscribe("USER", "u-group", null, null, "GROUP", "widgets", null, "WARNING");
      subscribe("USER", "u-table", null, null, "TABLE", WIDGET, null, "WARNING");
      subscribe("USER", "u-record", null, null, "RECORD", WIDGET, String.valueOf(widgets.get(0)), "WARNING");
      subscribe("USER", "u-other-record", null, null, "RECORD", WIDGET, String.valueOf(widgets.get(1)), "WARNING");
      subscribe("USER", "u-attr", null, null, "ATTRIBUTE", "site=main", null, "WARNING");
      subscribe("USER", "u-critical-only", null, null, "ALL", null, null, "CRITICAL");
      subscribe("USER", "u-monitor", null, null, "MONITOR", "999", null, "WARNING");

      Integer teamId = runtime.asSystem(() -> AlertsDb.insert(AlertsTeam.TABLE_NAME, new QRecord().withValue("name", "ops").withValue("label", "Ops")).getValueInteger("id"));
      runtime.asSystem(() -> AlertsDb.insert(AlertsTeamMember.TABLE_NAME, new QRecord().withValue("teamId", teamId).withValue("userId", "u-team-1")));
      runtime.asSystem(() -> AlertsDb.insert(AlertsTeamMember.TABLE_NAME, new QRecord().withValue("teamId", teamId).withValue("userId", "u-team-2")));
      subscribe("TEAM", null, teamId, null, "GROUP", "widgets", null, "ERROR");

      runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("w").withTitle("Broken").withRecordRef(AlertRecordRef.of(WIDGET, widgets.get(0))).withAttribute("site", "main"));

      List<String> recipients = notifications().stream().map(n -> n.getValueString("recipientUserId")).sorted().toList();
      assertThat(recipients).containsExactly("u-attr", "u-group", "u-record", "u-table", "u-team-1", "u-team-2", "u-type");
   }



   /*******************************************************************************
    ** Both host sources combine with explicit members and other subscriptions;
    ** duplicate users receive once and invisible users remain suppressed.
    *******************************************************************************/
   @Test
   void testHostTeamSources() throws Exception
   {
      List<String> calls = new java.util.ArrayList<>();
      config.withMaxAudienceSize(3).withAudienceResolver((source, value) ->
      {
         calls.add(source + ":" + value);
         return (Set.of(VIEWER, MANAGER, OUTSIDER));
      });
      for(String source : List.of("ROLE", "IDP_GROUP"))
      {
         Integer teamId = sourcedTeam(source);
         runtime.asSystem(() -> AlertsDb.insert(AlertsTeamMember.TABLE_NAME, new QRecord().withValue("teamId", teamId).withValue("userId", VIEWER)));
      }
      subscribe("USER", VIEWER, null, null, "ALL", null, null, "ERROR");
      runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("source-members"));

      assertThat(calls).containsExactly("ROLE:source-ROLE", "IDP_GROUP:source-IDP_GROUP");
      assertThat(notifications()).hasSize(3);
      assertThat(notifications()).filteredOn(n -> "SENT".equals(n.getValueString("status")))
         .extracting(n -> n.getValueString("recipientUserId")).containsExactlyInAnyOrder(VIEWER, MANAGER);
      assertThat(notifications()).filteredOn(n -> OUTSIDER.equals(n.getValueString("recipientUserId")))
         .singleElement().matches(n -> "NOT_VISIBLE".equals(n.getValueString("suppressedReason")));
   }



   /*******************************************************************************
    ** Unconfigured sources resolve to nobody; explicit members still work.
    *******************************************************************************/
   @Test
   void testMissingTeamResolver() throws Exception
   {
      sourcedTeam("ROLE");
      sourcedTeam("IDP_GROUP");
      runtime.getEngine().raise(new AlertSignal(SYSTEM).withDedupKey("no-resolver"));
      assertThat(notifications()).isEmpty();

      Integer manualTeam = sourcedTeam("MANUAL");
      runtime.asSystem(() -> AlertsDb.insert(AlertsTeamMember.TABLE_NAME, new QRecord().withValue("teamId", manualTeam).withValue("userId", VIEWER)));
      config.withAudienceResolver((source, value) ->
      {
         throw (new QException("Unavailable"));
      });
      runtime.getEngine().raise(new AlertSignal(SYSTEM).withDedupKey("failed-resolver"));
      assertThat(notifications()).singleElement().matches(n -> VIEWER.equals(n.getValueString("recipientUserId")) && "SENT".equals(n.getValueString("status")));
   }



   /*******************************************************************************
    ** Oversized resolver results fail closed without truncating the audience.
    *******************************************************************************/
   @Test
   void testTeamAudienceLimit() throws Exception
   {
      config.withMaxAudienceSize(1).withAudienceResolver((source, value) -> Set.of(VIEWER, MANAGER));
      sourcedTeam("ROLE");
      runtime.getEngine().raise(new AlertSignal(SYSTEM).withDedupKey("too-many"));
      assertThat(notifications()).isEmpty();
   }



   /*******************************************************************************
    ** Create a team subscription using a host-managed or manual source.
    *******************************************************************************/
   private Integer sourcedTeam(String source) throws QException
   {
      Integer teamId = runtime.asSystem(() -> AlertsDb.insert(AlertsTeam.TABLE_NAME, new QRecord().withValue("name", source).withValue("label", source)
         .withValue("memberSource", source).withValue("memberSourceValue", "source-" + source)).getValueInteger("id"));
      subscribe("TEAM", null, teamId, null, "ALL", null, null, "ERROR");
      return (teamId);
   }



   /*******************************************************************************
    ** RECORD mutes require both table and ID, including an alert whose subject
    ** is stored directly without an active record-reference row.
    *******************************************************************************/
   @Test
   void testRecordMuteMatching() throws Exception
   {
      runtime.asSystem(() -> AlertsDb.insert(AlertsMute.TABLE_NAME, new QRecord().withValue("name", "Record mute")
         .withValue("scopeType", "RECORD").withValue("scopeValue", WIDGET).withValue("scopeRecordId", "123")
         .withValue("startsAt", clock.instant()).withValue("endsAt", clock.instant().plusSeconds(60))
         .withValue("effect", "SILENCE_NOTIFICATIONS").withValue("enabled", true)));
      QRecord alert = runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("subject-only"));
      alert.setValue("subjectRecordId", "123");
      assertThat(recordMuted(alert)).isTrue();
      alert.setValue("subjectRecordId", "124");
      assertThat(recordMuted(alert)).isFalse();
      alert.setValue("subjectRecordId", "123");
      alert.setValue("subjectTableName", "another_table");
      assertThat(recordMuted(alert)).isFalse();
      alert.setValue("subjectTableName", WIDGET);
      clock.advance(Duration.ofMinutes(1));
      assertThat(recordMuted(alert)).isFalse();
   }



   /*******************************************************************************
    ** Match a mute as the router does under the system session.
    *******************************************************************************/
   private boolean recordMuted(QRecord alert) throws QException
   {
      return (runtime.asSystem(() -> new AlertRouter(runtime).isMuted(AlertsEngine.AlertTarget.of(alert), clock.instant(),
         com.kingsrook.qbits.alerts.model.AlertsPossibleValues.MuteEffect.SILENCE_NOTIFICATIONS)));
   }



   /*******************************************************************************
    ** Suppressions are logged with their reason.
    *******************************************************************************/
   @Test
   void testSuppressionReasons() throws Exception
   {
      QRecord channel = chatChannel("chat");
      subscribe("CHANNEL", null, null, channel.getValueInteger("id"), "ALL", null, null, "WARNING");

      //////////////////////////////////////////////////////
      // the type's notify switch off: TYPE_SILENT         //
      //////////////////////////////////////////////////////
      runtime.getTypeSync().syncIfNeeded();
      QRecord system = runtime.asSystem(() -> AlertsDb.first(AlertsType.TABLE_NAME, AlertsDb.eq("name", SYSTEM)));
      runtime.asSystem(() -> AlertsDb.update(AlertsType.TABLE_NAME, new QRecord().withValue("id", system.getValue("id")).withValue("notify", false)));
      runtime.getEngine().raise(new AlertSignal(SYSTEM).withDedupKey("silent"));
      assertThat(notifications().get(0).getValueString("suppressedReason")).isEqualTo("TYPE_SILENT");
      runtime.asSystem(() -> AlertsDb.update(AlertsType.TABLE_NAME, new QRecord().withValue("id", system.getValue("id")).withValue("notify", true)));

      //////////////////////////////////////
      // a silencing mute: MUTED          //
      //////////////////////////////////////
      Integer muteId = runtime.asSystem(() -> AlertsDb.insert(AlertsMute.TABLE_NAME, new QRecord().withValue("name", "quiet").withValue("scopeType", "ALERT_TYPE").withValue("scopeValue", SYSTEM)
         .withValue("startsAt", clock.instant().minusSeconds(1)).withValue("endsAt", clock.instant().plusSeconds(600)).withValue("effect", "SILENCE_NOTIFICATIONS").withValue("enabled", true)).getValueInteger("id"));
      QRecord muted = runtime.getEngine().raise(new AlertSignal(SYSTEM).withDedupKey("muted"));
      assertThat(lastReason()).isEqualTo("MUTED");
      assertThat(runtime.asSystem(() -> AlertsDb.byId(AlertsAlert.TABLE_NAME, muted.getValue("id"))).getValueBoolean("muted")).isTrue();
      runtime.asSystem(() -> AlertsDb.update(AlertsMute.TABLE_NAME, new QRecord().withValue("id", muteId).withValue("enabled", false)));

      ///////////////////////////////////////////////////////////////////
      // a channel subscription is not per person; it is sent (pending) //
      ///////////////////////////////////////////////////////////////////
      runtime.getEngine().raise(new AlertSignal(SYSTEM).withDedupKey("sent"));
      assertThat(lastStatus()).isEqualTo("PENDING");

      //////////////////////////////////////////////////////////
      // the same alert again within 10 minutes: DUPLICATE    //
      //////////////////////////////////////////////////////////
      runtime.getEngine().resolve(SYSTEM, "sent", com.kingsrook.qbits.alerts.model.AlertsPossibleValues.Resolution.PRODUCER, null, null);
      runtime.getEngine().raise(new AlertSignal(SYSTEM).withDedupKey("sent"));
      assertThat(lastReason()).isEqualTo("DUPLICATE");

      ////////////////////////////////////////
      // a disabled channel: CHANNEL_DISABLED //
      ////////////////////////////////////////
      runtime.asSystem(() -> AlertsDb.update(AlertsChannel.TABLE_NAME, new QRecord().withValue("id", channel.getValue("id")).withValue("enabled", false)));
      runtime.getEngine().raise(new AlertSignal(SYSTEM).withDedupKey("disabled"));
      assertThat(lastReason()).isEqualTo("CHANNEL_DISABLED");

      /////////////////////////////////////////
      // over the hourly limit: RATE_LIMITED //
      /////////////////////////////////////////
      runtime.asSystem(() -> AlertsDb.update(AlertsChannel.TABLE_NAME, new QRecord().withValue("id", channel.getValue("id")).withValue("enabled", true).withValue("rateLimitPerHour", 1)));
      runtime.getEngine().raise(new AlertSignal(SYSTEM).withDedupKey("limited"));
      assertThat(lastReason()).isEqualTo("RATE_LIMITED");
   }



   /*******************************************************************************
    ** Email to a person: suppressed without an address, paused, or when the
    ** person can't be shown to see an alert about a table; sent otherwise.
    *******************************************************************************/
   @Test
   void testUserEmailRules() throws Exception
   {
      subscribe("USER", "u1", null, null, "ALL", null, null, "WARNING");
      runtime.asSystem(() -> AlertsDb.insert(AlertsUserPreference.TABLE_NAME, new QRecord().withValue("userId", "u1").withValue("userLabel", "User One")
         .withValue("emailAddress", "user.one@example.com").withValue("emailEnabled", true).withValue("inAppEnabled", true)));

      runtime.getEngine().raise(new AlertSignal(SYSTEM).withDedupKey("a").withTitle("System down"));
      List<QRecord> rows = notifications();
      assertThat(rows).hasSize(2);
      assertThat(rows).anyMatch(r -> "PENDING".equals(r.getValueString("status")) && "u***@example.com".equals(r.getValueString("recipientLabel")));
      assertThat(rows).anyMatch(r -> "SENT".equals(r.getValueString("status")) && r.getValueString("recipientLabel").endsWith("(in app)"));

      /////////////////////////////////////////////////////////////////////////
      // an alert about a table: without a session for u1 (the mock auth    //
      // module builds one with no permissions) email is NOT_VISIBLE, in-app //
      // still goes (the inbox is filtered when it is read)                  //
      /////////////////////////////////////////////////////////////////////////
      runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("t").withTitle("Widget broken"));
      assertThat(notifications()).anyMatch(r -> "NOT_VISIBLE".equals(r.getValueString("suppressedReason")));

      QRecord preference = runtime.asSystem(() -> AlertsDb.first(AlertsUserPreference.TABLE_NAME, AlertsDb.eq("userId", "u1")));
      runtime.asSystem(() -> AlertsDb.update(AlertsUserPreference.TABLE_NAME, new QRecord().withValue("id", preference.getValue("id")).withValue("pausedUntil", clock.instant().plus(Duration.ofDays(1)))));
      runtime.getEngine().raise(new AlertSignal(SYSTEM).withDedupKey("paused"));
      assertThat(notifications()).anyMatch(r -> "USER_PAUSED".equals(r.getValueString("suppressedReason")));

      ///////////////////////////////////////////////////////////////////////
      // email without a messaging provider fails permanently, and says why //
      ///////////////////////////////////////////////////////////////////////
      runtime.getDeliveryWorker().deliverDue();
      QRecord email = notifications().stream().filter(r -> "u***@example.com".equals(r.getValueString("recipientLabel")) && r.getValueString("suppressedReason") == null).findFirst().orElseThrow();
      assertThat(email.getValueString("status")).isEqualTo("FAILED");
      assertThat(email.getValueString("lastError")).contains("messagingProviderName");
   }



   /*******************************************************************************
    ** With a host that can build sessions for users, visibility is checked
    ** through the record locks: an outsider (no read on the subject table)
    ** gets nothing, in-app included.
    *******************************************************************************/
   @Test
   void testKnownVisibility() throws Exception
   {
      subscribe("USER", "viewer-2", null, null, "ALL", null, null, "WARNING");
      subscribe("USER", "outsider-2", null, null, "ALL", null, null, "WARNING");
      runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("v").withTitle("Widget broken"));
      List<QRecord> rows = notifications();
      assertThat(rows).anyMatch(r -> "viewer-2".equals(r.getValueString("recipientUserId")) && "SENT".equals(r.getValueString("status")));
      assertThat(rows).anyMatch(r -> "outsider-2".equals(r.getValueString("recipientUserId")) && "NOT_VISIBLE".equals(r.getValueString("suppressedReason")));
      assertThat(where(AlertsNotificationRecipient.TABLE_NAME, AlertsDb.eq("userId", "outsider-2"))).isEmpty();
   }



   /*******************************************************************************
    ** A chat channel: rendered and sent; batched; retried with backoff to DEAD;
    ** the channel goes PROBATION then UNHEALTHY, raising an in-app alert; a
    ** success makes it HEALTHY and resolves that alert.
    *******************************************************************************/
   @Test
   void testDeliveryRetriesAndHealth() throws Exception
   {
      QRecord channel = chatChannel("chat");
      subscribe("CHANNEL", null, null, channel.getValueInteger("id"), "ALL", null, null, "WARNING");
      AlertsPreferences.ensure(runtime, QContext.getQSession());

      runtime.getEngine().raise(new AlertSignal(SYSTEM).withDedupKey("one").withTitle("Mail relay down for jane@example.com").withSummary("Retry with token=supersecretvalue123"));
      runtime.getEngine().raise(new AlertSignal(SYSTEM).withDedupKey("two").withTitle("Second"));
      assertThat(runtime.getDeliveryWorker().deliverDue()).isEqualTo(2);
      assertThat(TestSteps.SENT).hasSize(1);
      assertThat(TestSteps.SENT.get(0).subject()).isEqualTo("2 new alerts in Infrastructure");
      assertThat(TestSteps.SENT.get(0).text()).doesNotContain("jane@example.com").doesNotContain("supersecretvalue123").contains("[redacted]");
      assertThat(TestSteps.SENT.get(0).link()).isEqualTo("https://admin.example.com/app/alerts_alert");

      TestSteps.nextOutcome = DeliveryResult.Outcome.RETRY;
      runtime.getEngine().raise(new AlertSignal(SYSTEM).withDedupKey("three").withTitle("Third"));
      runtime.getDeliveryWorker().deliverDue();
      QRecord retrying = notifications().stream().filter(r -> "RETRYING".equals(r.getValueString("status"))).findFirst().orElseThrow();
      assertThat(retrying.getValueInteger("attempts")).isEqualTo(1);
      assertThat(retrying.getValueInstant("nextAttemptAt")).isEqualTo(clock.instant().plus(Duration.ofMinutes(1)));
      assertThat(runtime.asSystem(() -> AlertsDb.byId(AlertsChannel.TABLE_NAME, channel.getValue("id"))).getValueString("healthStatus")).isEqualTo("PROBATION");

      for(Duration wait : List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(1), Duration.ofHours(4)))
      {
         clock.advance(wait);
         runtime.getDeliveryWorker().deliverDue();
      }
      QRecord dead = runtime.asSystem(() -> AlertsDb.byId(AlertsDelivery.TABLE_NAME, retrying.getValue("id")));
      assertThat(dead.getValueString("status")).isEqualTo("DEAD");
      QRecord unhealthy = runtime.asSystem(() -> AlertsDb.byId(AlertsChannel.TABLE_NAME, channel.getValue("id")));
      assertThat(unhealthy.getValueString("healthStatus")).isEqualTo("UNHEALTHY");

      QRecord failing = runtime.asSystem(() -> AlertsDb.first(AlertsAlert.TABLE_NAME, AlertsDb.eq("alertTypeName", DeliveryWorker.CHANNEL_FAILING_TYPE)));
      assertThat(failing).isNotNull();
      assertThat(failing.getValueString("status")).isEqualTo("OPEN");
      assertThat(where(AlertsDelivery.TABLE_NAME, AlertsDb.eq("alertId", failing.getValue("id")))).allMatch(r -> r.getValue("recipientUserId") != null);

      /////////////////////////////////////////////////////
      // retry, and a success heals the channel          //
      /////////////////////////////////////////////////////
      TestSteps.nextOutcome = DeliveryResult.Outcome.SENT;
      assertThat(runtime.getDeliveryWorker().retry(List.of(dead.getValueInteger("id")))).isEqualTo(1);
      runtime.getDeliveryWorker().deliverDue();
      assertThat(runtime.asSystem(() -> AlertsDb.byId(AlertsChannel.TABLE_NAME, channel.getValue("id"))).getValueString("healthStatus")).isEqualTo("HEALTHY");
      assertThat(runtime.asSystem(() -> AlertsDb.byId(AlertsAlert.TABLE_NAME, failing.getValue("id"))).getValueString("status")).isEqualTo("RESOLVED");

      ///////////////////////////////////////////////
      // a permanent failure is FAILED at once     //
      ///////////////////////////////////////////////
      TestSteps.nextOutcome = DeliveryResult.Outcome.FAILED;
      runtime.getEngine().raise(new AlertSignal(SYSTEM).withDedupKey("four"));
      runtime.getDeliveryWorker().deliverDue();
      assertThat(notifications()).anyMatch(r -> "FAILED".equals(r.getValueString("status")) && r.getValueString("lastError").contains("404"));
   }



   /*******************************************************************************
    ** Test notifications go through the real pipeline.
    *******************************************************************************/
   @Test
   void testSendTest() throws Exception
   {
      QRecord channel = chatChannel("chat");
      QRecord result  = runtime.getDeliveryWorker().sendTest(channel.getValueInteger("id"), ADMIN);
      assertThat(result.getValueString("status")).isEqualTo("SENT");
      assertThat(result.getValueString("kind")).isEqualTo("TEST");
      assertThat(TestSteps.SENT.get(0).subject()).startsWith("TEST: ");

      QRecord inApp = runtime.asSystem(() -> runtime.getChannels().defaultChannel(com.kingsrook.qbits.alerts.model.AlertsPossibleValues.ChannelType.IN_APP));
      assertThat(inApp.getValueString("name")).isEqualTo(AlertChannels.DEFAULT_IN_APP);
      assertThat(runtime.getDeliveryWorker().sendTest(inApp.getValueInteger("id"), ADMIN).getValueString("status")).isEqualTo("SENT");
      runtime.getChannels().ensureDefaults();
      assertThat(runtime.getChannels().typeNames()).contains("IN_APP", "EMAIL", "EMAIL_LIST", "SLACK");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private String lastReason() throws QException
   {
      List<QRecord> rows = notifications();
      return (rows.get(rows.size() - 1).getValueString("suppressedReason"));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private String lastStatus() throws QException
   {
      List<QRecord> rows = notifications();
      return (rows.get(rows.size() - 1).getValueString("status"));
   }
}
