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


import java.util.List;
import com.kingsrook.qbits.alerts.AlertsQBitConfig;
import com.kingsrook.qbits.alerts.BaseAlertsTest;
import com.kingsrook.qbits.alerts.TestSteps;
import com.kingsrook.qbits.alerts.delivery.DeliveryResult;
import com.kingsrook.qbits.alerts.model.AlertsChannel;
import com.kingsrook.qbits.alerts.model.AlertsDelivery;
import com.kingsrook.qbits.alerts.model.AlertsGroup;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.Resolution;
import com.kingsrook.qbits.alerts.model.AlertsSubscription;
import com.kingsrook.qbits.alerts.model.AlertsTeam;
import com.kingsrook.qbits.alerts.model.AlertsTeamMember;
import com.kingsrook.qbits.alerts.model.AlertsUserPreference;
import com.kingsrook.qqq.backend.core.alerts.AlertSeverity;
import com.kingsrook.qqq.backend.core.alerts.AlertSignal;
import com.kingsrook.qqq.backend.core.alerts.AlertsInstanceMetaData;
import com.kingsrook.qqq.backend.core.alerts.QAlertTypeMetaData;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Two settings that used to be stored but never read, now enforced by the
 ** router: a group's owner team (the fallback audience for an alert nobody
 ** subscribed to) and a user's default channels.
 *******************************************************************************/
class AlertsWiringTest extends BaseAlertsTest
{
   static final String TYPE = "test.system.down";



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
         .withAlertType(new QAlertTypeMetaData().withName(TYPE).withLabel("System down").withGroupName("infrastructure").withDefaultSeverity(AlertSeverity.CRITICAL));
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
    ** A team with these members; returns its id.
    *******************************************************************************/
   private Integer team(String name, String... members) throws QException
   {
      Integer teamId = runtime.asSystem(() -> AlertsDb.insert(AlertsTeam.TABLE_NAME, new QRecord().withValue("name", name).withValue("label", name)).getValueInteger("id"));
      for(String member : members)
      {
         runtime.asSystem(() -> AlertsDb.insert(AlertsTeamMember.TABLE_NAME, new QRecord().withValue("teamId", teamId).withValue("userId", member)));
      }
      return (teamId);
   }



   /*******************************************************************************
    ** The group row for the test type (synced on first use).
    *******************************************************************************/
   private QRecord group(String name) throws QException
   {
      runtime.getTypeSync().syncIfNeeded();
      return (runtime.asSystem(() -> AlertsDb.first(AlertsGroup.TABLE_NAME, AlertsDb.eq("name", name))));
   }



   /*******************************************************************************
    ** The users who were sent an alert, sorted.
    *******************************************************************************/
   private List<String> recipients() throws QException
   {
      return (all(AlertsDelivery.TABLE_NAME).stream().map(n -> n.getValueString("recipientUserId")).distinct().sorted().toList());
   }



   /*******************************************************************************
    ** A user subscription to everything at WARNING and worse.
    *******************************************************************************/
   private void subscribe(String userId) throws QException
   {
      runtime.asSystem(() -> AlertsDb.insert(AlertsSubscription.TABLE_NAME, new QRecord().withValue("subscriberType", "USER").withValue("userId", userId)
         .withValue("scopeType", "ALL").withValue("minSeverity", "WARNING").withValue("events", "RAISED").withValue("deliveryMode", "IMMEDIATE").withValue("enabled", true)));
   }



   /*******************************************************************************
    ** An alert nobody subscribed to goes to its group's owner team; once a
    ** subscription matches, the owner team is not added.
    *******************************************************************************/
   @Test
   void ownerTeamIsTheFallbackAudience() throws Exception
   {
      Integer teamId = team("infra", "owner-1", "owner-2");
      QRecord group  = group("infrastructure");
      runtime.asSystem(() -> AlertsDb.update(AlertsGroup.TABLE_NAME, new QRecord().withValue("id", group.getValue("id")).withValue("ownerTeamId", teamId)));

      runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("a").withTitle("Down"));
      assertThat(recipients()).containsExactly("owner-1", "owner-2");
      assertThat(all(AlertsDelivery.TABLE_NAME)).allMatch(n -> "SENT".equals(n.getValueString("status")));

      List<java.io.Serializable> before = all(AlertsDelivery.TABLE_NAME).stream().map(n -> n.getValue("id")).toList();
      subscribe("subscriber");
      runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("b").withTitle("Down again"));
      List<QRecord> second = all(AlertsDelivery.TABLE_NAME).stream().filter(n -> !before.contains(n.getValue("id"))).toList();
      assertThat(second).extracting(n -> n.getValueString("recipientUserId")).containsExactly("subscriber");
   }



   /*******************************************************************************
    ** The nearest parent group with an owner team is used; a group with no
    ** owner anywhere leaves the alert unrouted, as before.
    *******************************************************************************/
   @Test
   void ownerTeamComesFromTheNearestParentGroup() throws Exception
   {
      QRecord group = group("infrastructure");
      runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("none").withTitle("Down"));
      assertThat(recipients()).as("no owner team anywhere: nobody is notified").isEmpty();

      Integer teamId = team("platform", "parent-owner");
      Integer parent = runtime.asSystem(() -> AlertsDb.insert(AlertsGroup.TABLE_NAME, new QRecord().withValue("name", "platform").withValue("label", "Platform")
         .withValue("ownerTeamId", teamId)).getValueInteger("id"));
      runtime.asSystem(() -> AlertsDb.update(AlertsGroup.TABLE_NAME, new QRecord().withValue("id", group.getValue("id")).withValue("parentGroupId", parent)));

      runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("parent").withTitle("Down"));
      assertThat(recipients()).containsExactly("parent-owner");
   }



   /*******************************************************************************
    ** The owner fallback is for alerts that matter: an INFO alert is not sent
    ** to the owner team.
    *******************************************************************************/
   @Test
   void ownerTeamIsNotToldAboutInfo() throws Exception
   {
      Integer teamId = team("infra", "owner-1");
      QRecord group  = group("infrastructure");
      runtime.asSystem(() -> AlertsDb.update(AlertsGroup.TABLE_NAME, new QRecord().withValue("id", group.getValue("id")).withValue("ownerTeamId", teamId)));

      runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("info").withSeverity(AlertSeverity.INFO).withTitle("FYI"));
      assertThat(recipients()).isEmpty();
   }



   /*******************************************************************************
    ** A user's default channels replace the built-in email choice, but the bell
    ** stays unless the user turned it off.
    *******************************************************************************/
   @Test
   void defaultChannelsDecideWhereAUserIsReached() throws Exception
   {
      QRecord chat = runtime.asSystem(() -> AlertsDb.insert(AlertsChannel.TABLE_NAME, new QRecord().withValue("name", "chat").withValue("label", "Chat")
         .withValue("channelType", "SLACK").withValue("enabled", true).withValue("detailLevel", "STANDARD").withValue("rateLimitPerHour", 60)
         .withValue("healthStatus", "HEALTHY").withValue("consecutiveFailures", 0)));
      String chatId = String.valueOf(chat.getValue("id"));
      subscribe("u1");

      runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("plain").withTitle("Down"));
      List<QRecord> plain = all(AlertsDelivery.TABLE_NAME);
      assertThat(plain).singleElement().matches(n -> !chatId.equals(String.valueOf(n.getValue("channelId"))), "went to the built-in in-app channel");

      QRecord preference = runtime.asSystem(() -> AlertsDb.insert(AlertsUserPreference.TABLE_NAME, new QRecord().withValue("userId", "u1").withValue("userLabel", "One")
         .withValue("inAppEnabled", true).withValue("emailEnabled", false).withValue("defaultChannelIds", "not-a-number, " + chatId + ", 99999")));
      runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("chosen").withTitle("Down again"));
      List<QRecord> chosen = all(AlertsDelivery.TABLE_NAME).stream().filter(n -> !n.getValue("id").equals(plain.get(0).getValue("id"))).toList();
      assertThat(chosen).hasSize(2);
      assertThat(chosen).anyMatch(n -> chatId.equals(String.valueOf(n.getValue("channelId"))));
      assertThat(chosen).anyMatch(n -> !chatId.equals(String.valueOf(n.getValue("channelId"))));

      runtime.asSystem(() -> AlertsDb.update(AlertsUserPreference.TABLE_NAME, new QRecord().withValue("id", preference.getValue("id")).withValue("inAppEnabled", false)));
      List<Object> before = all(AlertsDelivery.TABLE_NAME).stream().map(n -> (Object) n.getValue("id")).toList();
      runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("no-bell").withTitle("Down a third time"));
      List<QRecord> third = all(AlertsDelivery.TABLE_NAME).stream().filter(n -> !before.contains(n.getValue("id"))).toList();
      assertThat(third).singleElement().matches(n -> chatId.equals(String.valueOf(n.getValue("channelId"))), "only the chosen channel when the bell is off");
   }



   /*******************************************************************************
    ** A subscription that covers the alert by scope and severity, but only for
    ** some events, still counts as ownership: RESOLVED does not fall through to
    ** the owner team.
    *******************************************************************************/
   @Test
   void aSubscriptionWithFewerEventsStillCoversTheAlert() throws Exception
   {
      Integer teamId = team("infra", "owner-1");
      QRecord group  = group("infrastructure");
      runtime.asSystem(() -> AlertsDb.update(AlertsGroup.TABLE_NAME, new QRecord().withValue("id", group.getValue("id")).withValue("ownerTeamId", teamId)));
      subscribe("subscriber");

      runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("a").withTitle("Down"));
      runtime.getEngine().resolve(TYPE, "a", Resolution.PRODUCER, null, "fixed");
      assertThat(recipients()).containsExactly("subscriber");
   }



   /*******************************************************************************
    ** Follow-ups (resolved, reminders) go to the owner team only when it was
    ** told about the raise.
    *******************************************************************************/
   @Test
   void ownerTeamGetsFollowUpsOnlyAfterTheRaise() throws Exception
   {
      Integer teamId = team("infra", "owner-1");
      QRecord group  = group("infrastructure");

      /////////////////////////////////////////////////////////////////
      // raised with no owner team: nobody is told, so nobody is told //
      // about the resolution even though a team is named afterwards  //
      /////////////////////////////////////////////////////////////////
      runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("early").withTitle("Down"));
      runtime.asSystem(() -> AlertsDb.update(AlertsGroup.TABLE_NAME, new QRecord().withValue("id", group.getValue("id")).withValue("ownerTeamId", teamId)));
      runtime.getEngine().resolve(TYPE, "early", Resolution.PRODUCER, null, "fixed");
      assertThat(recipients()).isEmpty();

      runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("late").withTitle("Down"));
      assertThat(recipients()).containsExactly("owner-1");
      runtime.getEngine().resolve(TYPE, "late", Resolution.PRODUCER, null, "fixed");
      assertThat(all(AlertsDelivery.TABLE_NAME).stream().filter(n -> "RESOLVED".equals(n.getValueString("kind")))).hasSize(1);
   }



   /*******************************************************************************
    ** A failing type sync (a unique-key race between replicas, say) never breaks
    ** the bell; the next request retries.
    *******************************************************************************/
   @Test
   void aFailingSyncDoesNotBreakTheBell() throws Exception
   {
      try(java.sql.Connection connection = com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager.getConnection((com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSBackendMetaData) qInstance.getBackend(BACKEND_NAME));
         java.sql.Statement statement = connection.createStatement())
      {
         statement.execute("ALTER TABLE alerts_type RENAME TO alerts_type_gone");
         try
         {
            assertThat(new com.kingsrook.qbits.alerts.api.AlertsApi(runtime).requireViewer()).isEqualTo(ADMIN);
         }
         finally
         {
            statement.execute("ALTER TABLE alerts_type_gone RENAME TO alerts_type");
         }
      }
      assertThat(new com.kingsrook.qbits.alerts.api.AlertsApi(runtime).requireViewer()).isEqualTo(ADMIN);
      assertThat(all("alerts_type")).isNotEmpty();
   }
}
