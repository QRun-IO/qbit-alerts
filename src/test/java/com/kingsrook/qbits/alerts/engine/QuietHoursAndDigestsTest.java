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
import java.time.Instant;
import java.util.List;
import com.kingsrook.qbits.alerts.AlertsQBitConfig;
import com.kingsrook.qbits.alerts.BaseAlertsTest;
import com.kingsrook.qbits.alerts.TestSteps;
import com.kingsrook.qbits.alerts.delivery.DeliveryResult;
import com.kingsrook.qbits.alerts.delivery.DigestWorker;
import com.kingsrook.qbits.alerts.metadata.AlertsProcessesMetaDataProducer;
import com.kingsrook.qbits.alerts.model.AlertsChannel;
import com.kingsrook.qbits.alerts.model.AlertsDelivery;
import com.kingsrook.qbits.alerts.model.AlertsNotification;
import com.kingsrook.qbits.alerts.model.AlertsNotificationRecipient;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.ChannelType;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.EventType;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.Resolution;
import com.kingsrook.qbits.alerts.model.AlertsSubscription;
import com.kingsrook.qbits.alerts.model.AlertsTeam;
import com.kingsrook.qbits.alerts.model.AlertsTeamMember;
import com.kingsrook.qbits.alerts.model.AlertsUserPreference;
import com.kingsrook.qqq.backend.core.actions.processes.RunProcessAction;
import com.kingsrook.qqq.backend.core.alerts.AlertSeverity;
import com.kingsrook.qqq.backend.core.alerts.AlertSignal;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** User-local routing and durable subscription digest delivery on H2.
 *******************************************************************************/
class QuietHoursAndDigestsTest extends BaseAlertsTest
{
   private static final String TYPE = "test.digest";
   private QRecord preference;



   /*******************************************************************************
    ** Use a recording channel through the real channel registry.
    *******************************************************************************/
   @Override
   protected void customizeConfig(AlertsQBitConfig config)
   {
      config.withChannelType(new QCodeReference(TestSteps.RecordingChannel.class));
      config.withChannelType(new QCodeReference(RecordingEmailChannel.class));
   }



   /*******************************************************************************
    ** One user's settings, and a clean transport recorder.
    *******************************************************************************/
   @BeforeEach
   void prepare() throws Exception
   {
      TestSteps.SENT.clear();
      TestSteps.nextOutcome = DeliveryResult.Outcome.SENT;
      preference = runtime.asSystem(() -> AlertsDb.insert(AlertsUserPreference.TABLE_NAME, new QRecord()
         .withValue("userId", ADMIN).withValue("inAppEnabled", true).withValue("emailEnabled", false).withValue("timeZone", "UTC")));
   }



   /*******************************************************************************
    ** Personal subscriptions use the standard in-app default or an override.
    *******************************************************************************/
   private QRecord subscribe(String mode, Integer channelId) throws QException
   {
      return (runtime.asSystem(() -> AlertsDb.insert(AlertsSubscription.TABLE_NAME, new QRecord()
         .withValue("subscriberType", "USER").withValue("userId", ADMIN).withValue("scopeType", "ALL")
         .withValue("minSeverity", "WARNING").withValue("events", "RAISED,RESOLVED").withValue("enabled", true)
         .withValue("deliveryMode", mode).withValue("channelOverrideIds", channelId == null ? null : channelId.toString()))));
   }



   /*******************************************************************************
    ** Update settings through the table actions.
    *******************************************************************************/
   private void settings(QRecord values) throws QException
   {
      runtime.asSystem(() -> AlertsDb.update(AlertsUserPreference.TABLE_NAME, values.withValue("id", preference.getValue("id"))));
   }



   /*******************************************************************************
    ** Raise a distinct alert at the test clock.
    *******************************************************************************/
   private QRecord raise(String key, AlertSeverity severity) throws QException
   {
      return (runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey(key).withTitle(key).withSeverity(severity)));
   }



   /*******************************************************************************
    ** Source delivery items, excluding digest claim rows.
    *******************************************************************************/
   private List<QRecord> items() throws QException
   {
      return (where(AlertsDelivery.TABLE_NAME, AlertsDb.eq("recipientUserId", ADMIN).withCriteria("alertId", QCriteriaOperator.IS_NOT_BLANK)));
   }



   /*******************************************************************************
    ** Inclusive start, exclusive end, and the default critical bypass.
    *******************************************************************************/
   @Test
   void testQuietBoundariesAndBypass() throws Exception
   {
      settings(new QRecord().withValue("inAppEnabled", false).withValue("emailEnabled", true).withValue("emailAddress", "admin@example.com"));
      subscribe("IMMEDIATE", null);
      settings(new QRecord().withValue("quietHoursStart", "12:00").withValue("quietHoursEnd", "14:00"));
      raise("at start", AlertSeverity.WARNING);
      raise("critical", AlertSeverity.CRITICAL);
      clock.set(Instant.parse("2026-10-12T13:59:59Z"));
      raise("inside", AlertSeverity.ERROR);
      clock.set(Instant.parse("2026-10-12T14:00:00Z"));
      raise("at end", AlertSeverity.ERROR);
      assertThat(items()).extracting(r -> r.getValueString("suppressedReason")).containsExactly("QUIET_HOURS", null, "QUIET_HOURS", null);
      assertThat(all(AlertsNotificationRecipient.TABLE_NAME)).isEmpty();
   }



   /*******************************************************************************
    ** Overnight days belong to the start day, in the user's time zone.
    *******************************************************************************/
   @Test
   void testOvernightDaysAndTimeZone() throws Exception
   {
      settings(new QRecord().withValue("inAppEnabled", false).withValue("emailEnabled", true).withValue("emailAddress", "admin@example.com"));
      subscribe("IMMEDIATE", null);
      settings(new QRecord().withValue("timeZone", "America/Chicago").withValue("quietHoursStart", "22:00")
         .withValue("quietHoursEnd", "07:00").withValue("quietDays", "MON"));
      clock.set(Instant.parse("2026-10-13T03:00:00Z"));
      raise("Monday night", AlertSeverity.ERROR);
      clock.set(Instant.parse("2026-10-13T11:59:59Z"));
      raise("Tuesday morning", AlertSeverity.ERROR);
      clock.set(Instant.parse("2026-10-14T03:00:00Z"));
      raise("Tuesday night", AlertSeverity.ERROR);
      assertThat(items()).extracting(r -> r.getValueString("suppressedReason")).containsExactly("QUIET_HOURS", "QUIET_HOURS", null);
   }



   /*******************************************************************************
    ** Pause precedes quiet hours for push channels, including critical alerts.
    ** A channel subscription is independent of personal settings.
    *******************************************************************************/
   @Test
   void testPauseAndChannelExemption() throws Exception
   {
      settings(new QRecord().withValue("inAppEnabled", false).withValue("emailEnabled", true).withValue("emailAddress", "admin@example.com"));
      subscribe("IMMEDIATE", null);
      QRecord channel = channel();
      runtime.asSystem(() -> AlertsDb.insert(AlertsSubscription.TABLE_NAME, new QRecord()
         .withValue("subscriberType", "CHANNEL").withValue("channelId", channel.getValue("id")).withValue("scopeType", "ALL")
         .withValue("deliveryMode", "IMMEDIATE").withValue("enabled", true)));
      settings(new QRecord().withValue("pausedUntil", clock.instant().plusSeconds(60)).withValue("quietHoursStart", "11:00").withValue("quietHoursEnd", "13:00"));
      raise("paused", AlertSeverity.CRITICAL);
      assertThat(items()).singleElement().matches(r -> "USER_PAUSED".equals(r.getValueString("suppressedReason")));
      assertThat(runtime.getDeliveryWorker().deliverDue()).isEqualTo(1);
      assertThat(TestSteps.SENT).hasSize(1);
      clock.advance(Duration.ofSeconds(60));
      raise("pause expired", AlertSeverity.CRITICAL);
      assertThat(items().get(1).getValueString("status")).isEqualTo("PENDING");
      raise("quiet user", AlertSeverity.ERROR);
      assertThat(items().get(2).getValueString("suppressedReason")).isEqualTo("QUIET_HOURS");
      assertThat(runtime.getDeliveryWorker().deliverDue()).isEqualTo(3);
   }



   /*******************************************************************************
    ** Expanded manual team members have the same personal quiet windows.
    *******************************************************************************/
   @Test
   void testTeamQuietHours() throws Exception
   {
      settings(new QRecord().withValue("inAppEnabled", false).withValue("emailEnabled", true).withValue("emailAddress", "admin@example.com"));
      QRecord team = runtime.asSystem(() -> AlertsDb.insert(AlertsTeam.TABLE_NAME, new QRecord().withValue("name", "ops").withValue("label", "Ops")));
      runtime.asSystem(() -> AlertsDb.insert(AlertsTeamMember.TABLE_NAME, new QRecord().withValue("teamId", team.getValue("id")).withValue("userId", ADMIN)));
      runtime.asSystem(() -> AlertsDb.insert(AlertsSubscription.TABLE_NAME, new QRecord().withValue("subscriberType", "TEAM")
         .withValue("teamId", team.getValue("id")).withValue("scopeType", "ALL").withValue("enabled", true)));
      settings(new QRecord().withValue("quietHoursStart", "11:00").withValue("quietHoursEnd", "13:00").withValue("quietDays", "1,3"));
      raise("quiet team", AlertSeverity.ERROR);
      assertThat(items()).singleElement().matches(r -> "QUIET_HOURS".equals(r.getValueString("suppressedReason")));
   }



   /*******************************************************************************
    ** Invalid settings are safe; an explicit lower bypass is respected.
    *******************************************************************************/
   @Test
   void testTimingDefaults() throws Exception
   {
      QRecord settings = new QRecord().withValue("timeZone", "bad/zone").withValue("quietHoursStart", "11:00").withValue("quietHoursEnd", "13:00");
      assertThat(AlertTiming.isQuiet(settings, AlertSeverity.ERROR, clock.instant())).isTrue();
      settings.setValue("quietBypassSeverity", "ERROR");
      assertThat(AlertTiming.isQuiet(settings, AlertSeverity.ERROR, clock.instant())).isFalse();
      settings.setValue("quietHoursStart", "oops");
      assertThat(AlertTiming.isQuiet(settings, AlertSeverity.WARNING, clock.instant())).isFalse();
      settings.setValue("quietHoursStart", "13:00");
      assertThat(AlertTiming.isQuiet(settings, AlertSeverity.WARNING, clock.instant())).isFalse();
      assertThat(AlertTiming.isQuiet(null, AlertSeverity.WARNING, clock.instant())).isFalse();
   }



   /*******************************************************************************
    ** Hourly items never reach the immediate worker. One message has severity
    ** order, new and resolved sections, links, and no resend after restart.
    *******************************************************************************/
   @Test
   void testHourlyGroupingAndDurableRerun() throws Exception
   {
      QRecord channel = channel();
      subscribe("DIGEST_HOURLY", channel.getValueInteger("id"));
      QRecord first = raise("First", AlertSeverity.WARNING);
      QRecord second = raise("Second", AlertSeverity.CRITICAL);
      runtime.getEngine().resolve(TYPE, "First", Resolution.PRODUCER, null, "Done");
      assertThat(items()).hasSize(3).allSatisfy(r ->
      {
         assertThat(r.getValueString("kind")).isEqualTo("DIGEST");
         assertThat(r.getValueString("status")).isEqualTo("PENDING");
         assertThat(r.getValueString("digestKey")).isEqualTo("alerts:digest:" + ADMIN + ":2026-10-12T12:00:00Z:DIGEST_HOURLY");
      });
      assertThat(runtime.getDeliveryWorker().deliverDue()).isZero();
      assertThat(new DigestWorker(runtime).sendDue()).isZero();
      clock.advance(Duration.ofHours(1));
      assertThat(new DigestWorker(runtime).sendDue()).isEqualTo(1);
      assertThat(TestSteps.SENT).singleElement().satisfies(message ->
      {
         assertThat(message.alertIds()).containsExactly(second.getValueInteger("id"), first.getValueInteger("id"));
         assertThat(message.text()).contains("Open alerts by severity", "New since the last digest", "Resolved since the last digest", "/app/alerts_alert/" + first.getValue("id"));
         assertThat(message.kind()).isEqualTo("DIGEST");
      });
      assertThat(items()).allSatisfy(r -> assertThat(r.getValueString("status")).isEqualTo("SENT"));
      assertThat(new DigestWorker(runtime).sendDue()).isZero();
      clock.advance(Duration.ofHours(1));
      assertThat(new DigestWorker(runtime).sendDue()).isZero();
      assertThat(TestSteps.SENT).hasSize(1);
   }



   /*******************************************************************************
    ** Daily delivery follows the recipient's local hour and groups in-app
    ** alerts into one notification. The next day's window remains separate.
    *******************************************************************************/
   @Test
   void testDailyInAppAndScheduledProcess() throws Exception
   {
      subscribe("DIGEST_DAILY", null);
      settings(new QRecord().withValue("timeZone", "America/Chicago").withValue("digestHour", 8));
      raise("One", AlertSeverity.ERROR);
      raise("Two", AlertSeverity.WARNING);
      assertThat(all(AlertsNotificationRecipient.TABLE_NAME)).isEmpty();
      clock.set(Instant.parse("2026-10-12T12:59:59Z"));
      assertThat(new DigestWorker(runtime).sendDue()).isZero();
      clock.set(Instant.parse("2026-10-12T13:00:00Z"));
      assertThat(new RunProcessAction().execute(new RunProcessInput().withProcessName(AlertsProcessesMetaDataProducer.SEND_DIGESTS)).getValues().get("count")).isEqualTo(1);
      assertThat(all(AlertsNotificationRecipient.TABLE_NAME)).hasSize(1);
      assertThat(all(AlertsNotification.TABLE_NAME)).singleElement().matches(r -> r.getValueString("body").contains("One") && r.getValueString("body").contains("Two"));
      raise("Tomorrow", AlertSeverity.ERROR);
      assertThat(new DigestWorker(runtime).sendDue()).isZero();
      clock.advance(Duration.ofDays(1));
      assertThat(new DigestWorker(runtime).sendDue()).isEqualTo(1);
      assertThat(all(AlertsNotificationRecipient.TABLE_NAME)).hasSize(2);
   }



   /*******************************************************************************
    ** A late scheduler catches up; daily windows preserve local time over DST.
    *******************************************************************************/
   @Test
   void testDaylightSavingBoundariesAndCatchup() throws Exception
   {
      QRecord setting = new QRecord().withValue("timeZone", "America/Chicago").withValue("digestHour", 8);
      Instant end = AlertTiming.digestEnd("DIGEST_DAILY", setting, Instant.parse("2026-10-31T14:00:00Z"));
      assertThat(end).isEqualTo(Instant.parse("2026-11-01T14:00:00Z"));
      assertThat(Duration.between(AlertTiming.digestStart("DIGEST_DAILY", setting, end), end)).isEqualTo(Duration.ofHours(25));
      end = AlertTiming.digestEnd("DIGEST_DAILY", setting, Instant.parse("2026-03-07T15:00:00Z"));
      assertThat(Duration.between(AlertTiming.digestStart("DIGEST_DAILY", setting, end), end)).isEqualTo(Duration.ofHours(23));
      subscribe("DIGEST_DAILY", null);
      raise("Delayed", AlertSeverity.ERROR);
      clock.advance(Duration.ofDays(3));
      assertThat(new DigestWorker(runtime).sendDue()).isEqualTo(1);
      assertThat(new DigestWorker(runtime).sendDue()).isZero();
   }



   /*******************************************************************************
    ** Empty digests do not send, and transport failures fail all source rows.
    *******************************************************************************/
   @Test
   void testEmptyAndFailedDigest() throws Exception
   {
      assertThat(new DigestWorker(runtime).sendDue()).isZero();
      QRecord channel = channel();
      subscribe("DIGEST_HOURLY", channel.getValueInteger("id"));
      raise("Failed one", AlertSeverity.ERROR);
      raise("Failed two", AlertSeverity.ERROR);
      TestSteps.nextOutcome = DeliveryResult.Outcome.RETRY;
      clock.advance(Duration.ofHours(1));
      assertThat(new DigestWorker(runtime).sendDue()).isZero();
      assertThat(items()).hasSize(2).allSatisfy(r ->
      {
         assertThat(r.getValueString("status")).isEqualTo("FAILED");
         assertThat(r.getValueString("lastError")).contains("503");
      });
      assertThat(new DigestWorker(runtime).sendDue()).isZero();
      assertThat(TestSteps.SENT).hasSize(1);
   }



   /*******************************************************************************
    ** The same source event is queued only once; resolve is a distinct event.
    *******************************************************************************/
   @Test
   void testDigestEventDeduplication() throws Exception
   {
      subscribe("DIGEST_HOURLY", null);
      QRecord alert = raise("Duplicate", AlertSeverity.ERROR);
      Integer eventId = items().get(0).getValueInteger("alertEventId");
      runtime.asSystem(() ->
      {
         runtime.getRouter().route(alert, EventType.RAISED, eventId);
         return (null);
      });
      assertThat(items()).hasSize(2);
      assertThat(items().get(1).getValueString("suppressedReason")).isEqualTo("DUPLICATE");
   }



   /*******************************************************************************
    ** An expired durable claim is failed, never resent after a crash.
    *******************************************************************************/
   @Test
   void testInterruptedClaim() throws Exception
   {
      QRecord channel = channel();
      subscribe("DIGEST_HOURLY", channel.getValueInteger("id"));
      raise("Interrupted", AlertSeverity.ERROR);
      QRecord item = items().get(0);
      runtime.asSystem(() -> AlertsDb.insert(AlertsDelivery.TABLE_NAME, new QRecord().withValue("kind", "DIGEST").withValue("status", "SENDING")
         .withValue("digestClaimKey", item.getValueString("digestKey") + ":channel:" + channel.getValue("id"))
         .withValue("claimedUntil", clock.instant().plus(Duration.ofHours(2)))));
      clock.advance(Duration.ofHours(1));
      assertThat(new DigestWorker(runtime).sendDue()).isZero();
      assertThat(items().get(0).getValueString("status")).isEqualTo("PENDING");
      clock.advance(Duration.ofHours(2));
      assertThat(new DigestWorker(runtime).sendDue()).isZero();
      assertThat(items().get(0).getValueString("lastError")).contains("interrupted");
      assertThat(TestSteps.SENT).isEmpty();
   }



   /*******************************************************************************
    ** Settings edited after fan-out are honored at digest dispatch too.
    *******************************************************************************/
   @Test
   void testPauseAndQuietAtDispatch() throws Exception
   {
      settings(new QRecord().withValue("inAppEnabled", false).withValue("emailEnabled", true).withValue("emailAddress", "admin@example.com"));
      subscribe("DIGEST_HOURLY", null);
      raise("Paused while waiting", AlertSeverity.ERROR);
      settings(new QRecord().withValue("pausedUntil", clock.instant().plus(Duration.ofHours(2))));
      clock.advance(Duration.ofHours(1));
      assertThat(new DigestWorker(runtime).sendDue()).isZero();
      assertThat(items().get(0).getValueString("suppressedReason")).isEqualTo("USER_PAUSED");
      settings(new QRecord().withValue("pausedUntil", null));
      raise("Quiet while waiting", AlertSeverity.WARNING);
      raise("Critical breaks through", AlertSeverity.CRITICAL);
      settings(new QRecord().withValue("quietHoursStart", "14:00").withValue("quietHoursEnd", "16:00"));
      clock.advance(Duration.ofHours(1));
      assertThat(new DigestWorker(runtime).sendDue()).isEqualTo(1);
      assertThat(items().get(1).getValueString("suppressedReason")).isEqualTo("QUIET_HOURS");
      assertThat(items().get(2).getValueString("status")).isEqualTo("SENT");
      assertThat(TestSteps.SENT).singleElement().matches(message -> !message.text().contains("Quiet while waiting"));
   }



   /*******************************************************************************
    ** Scheduler metadata is hidden and repeats hourly on the chosen scheduler.
    *******************************************************************************/
   @Test
   void testScheduleAndMissingHour() throws Exception
   {
      var process = AlertsProcessesMetaDataProducer.buildProcesses(new AlertsQBitConfig().withSchedulerName("jobs")).stream()
         .filter(p -> AlertsProcessesMetaDataProducer.SEND_DIGESTS.equals(p.getName())).findFirst().orElseThrow();
      assertThat(process.getIsHidden()).isTrue();
      assertThat(process.getSchedule().getSchedulerName()).isEqualTo("jobs");
      assertThat(process.getSchedule().getRepeatSeconds()).isEqualTo(3600);
      QRecord setting = new QRecord().withValue("timeZone", "America/Chicago").withValue("digestHour", 2);
      Instant end = AlertTiming.digestEnd("DIGEST_DAILY", setting, Instant.parse("2026-03-07T15:00:00Z"));
      assertThat(end).isEqualTo(Instant.parse("2026-03-08T08:00:00Z"));
      assertThat(AlertTiming.digestStart("DIGEST_DAILY", setting, end)).isEqualTo(Instant.parse("2026-03-07T08:00:00Z"));
      assertThat(AlertTiming.digestEnd("DIGEST_DAILY", null, clock.instant())).isEqualTo(Instant.parse("2026-10-13T08:00:00Z"));
   }



   /*******************************************************************************
    ** Hourly and daily windows with identical starts must not mix future items.
    *******************************************************************************/
   @Test
   void testDifferentCadencesAndRecipients() throws Exception
   {
      QRecord channel = channel();
      QRecord subscription = subscribe("DIGEST_HOURLY", channel.getValueInteger("id"));
      settings(new QRecord().withValue("digestHour", 12));
      raise("Hourly", AlertSeverity.ERROR);
      runtime.asSystem(() -> AlertsDb.update(AlertsSubscription.TABLE_NAME, new QRecord().withValue("id", subscription.getValue("id")).withValue("deliveryMode", "DIGEST_DAILY")));
      raise("Daily", AlertSeverity.ERROR);
      assertThat(items()).extracting(r -> r.getValueString("digestKey")).doesNotHaveDuplicates();
      clock.advance(Duration.ofHours(1));
      assertThat(new DigestWorker(runtime).sendDue()).isEqualTo(1);
      assertThat(TestSteps.SENT.get(0).text()).contains("Hourly").doesNotContain("Daily");
      assertThat(items().get(1).getValueString("status")).isEqualTo("PENDING");
      clock.advance(Duration.ofDays(1));
      assertThat(new DigestWorker(runtime).sendDue()).isEqualTo(1);
      assertThat(TestSteps.SENT.get(1).text()).contains("Daily").doesNotContain("Hourly");
   }



   /*******************************************************************************
    ** Oversized windows fail visibly instead of sending a partial digest or
    ** allocating an unbounded message. Every source item records the error.
    *******************************************************************************/
   @Test
   void testBoundedDigest() throws Exception
   {
      subscribe("DIGEST_HOURLY", null);
      raise("Many", AlertSeverity.ERROR);
      QRecord first = items().get(0);
      runtime.asSystem(() ->
      {
         for(int i = 0; i < 500; i++)
         {
            AlertsDb.insert(AlertsDelivery.TABLE_NAME, new QRecord().withValue("alertId", first.getValue("alertId"))
               .withValue("channelId", first.getValue("channelId")).withValue("recipientUserId", ADMIN)
               .withValue("kind", "DIGEST").withValue("status", "PENDING").withValue("digestKey", first.getValue("digestKey"))
               .withValue("nextAttemptAt", first.getValue("nextAttemptAt")));
         }
         return (null);
      });
      clock.advance(Duration.ofHours(1));
      assertThat(new DigestWorker(runtime).sendDue()).isZero();
      assertThat(items()).hasSize(501).allSatisfy(r ->
      {
         assertThat(r.getValueString("status")).isEqualTo("FAILED");
         assertThat(r.getValueString("lastError")).contains("500 item");
      });
      assertThat(all(AlertsNotificationRecipient.TABLE_NAME)).isEmpty();
   }



   /*******************************************************************************
    ** Persisted claims freeze item membership and reject duplicate claims.
    ** Requeueing a claim cannot turn a terminal result into an endless loop.
    *******************************************************************************/
   @Test
   void testLateItemsAndRetriedClaim() throws Exception
   {
      QRecord channel = channel();
      subscribe("DIGEST_HOURLY", channel.getValueInteger("id"));
      raise("Claimed", AlertSeverity.ERROR);
      QRecord first = items().get(0);
      clock.advance(Duration.ofHours(1));
      assertThat(new DigestWorker(runtime).sendDue()).isEqualTo(1);
      QRecord claim = where(AlertsDelivery.TABLE_NAME, AlertsDb.eq("digestKey", first.getValue("digestKey"))
         .withCriteria("digestClaimKey", QCriteriaOperator.IS_NOT_BLANK)).get(0);
      assertThat(claim.getValueInteger("digestLastItemId")).isEqualTo(first.getValueInteger("id"));
      runtime.asSystem(() -> AlertsDb.insert(AlertsDelivery.TABLE_NAME, new QRecord().withValue("alertId", first.getValue("alertId"))
         .withValue("channelId", first.getValue("channelId")).withValue("recipientUserId", ADMIN).withValue("kind", "DIGEST")
         .withValue("status", "PENDING").withValue("digestKey", first.getValue("digestKey")).withValue("nextAttemptAt", first.getValue("nextAttemptAt"))));
      assertThat(new DigestWorker(runtime).sendDue()).isZero();
      assertThat(items().get(1).getValueString("status")).isEqualTo("SUPPRESSED");
      assertThat(items().get(1).getValueString("suppressedReason")).isEqualTo("DUPLICATE");
      assertThat(items().get(1).getValueInstant("sentAt")).isNull();
      assertThatThrownBy(() -> runtime.asSystem(() -> AlertsDb.insert(AlertsDelivery.TABLE_NAME,
         new QRecord().withValue("kind", "DIGEST").withValue("status", "SENDING").withValue("digestClaimKey", claim.getValue("digestClaimKey")))))
         .isInstanceOf(QException.class);
      runtime.asSystem(() -> AlertsDb.update(AlertsDelivery.TABLE_NAME, new QRecord().withValue("id", claim.getValue("id")).withValue("status", "PENDING")));
      runtime.asSystem(() -> AlertsDb.update(AlertsDelivery.TABLE_NAME, new QRecord().withValue("id", first.getValue("id")).withValue("status", "PENDING")));
      assertThat(new DigestWorker(runtime).sendDue()).isZero();
      assertThat(items().get(0).getValueString("status")).isEqualTo("FAILED");
      assertThat(TestSteps.SENT).hasSize(1);
   }



   /*******************************************************************************
    ** Channel subscriptions honor their cadence without personal quiet/pause
    ** settings. With no user time zone, daily channels use 08:00 UTC.
    *******************************************************************************/
   @Test
   void testChannelDigest() throws Exception
   {
      QRecord channel = channel();
      runtime.asSystem(() -> AlertsDb.insert(AlertsSubscription.TABLE_NAME, new QRecord().withValue("subscriberType", "CHANNEL")
         .withValue("channelId", channel.getValue("id")).withValue("scopeType", "ALL").withValue("deliveryMode", "DIGEST_DAILY").withValue("enabled", true)));
      settings(new QRecord().withValue("pausedUntil", clock.instant().plus(Duration.ofDays(3))));
      raise("Channel digest", AlertSeverity.ERROR);
      QRecord row = all(AlertsDelivery.TABLE_NAME).get(0);
      assertThat(row.getValueString("kind")).isEqualTo("DIGEST");
      assertThat(row.getValueInstant("nextAttemptAt")).isEqualTo(Instant.parse("2026-10-13T08:00:00Z"));
      assertThat(runtime.getDeliveryWorker().deliverDue()).isZero();
      clock.set(Instant.parse("2026-10-13T08:00:00Z"));
      assertThat(new DigestWorker(runtime).sendDue()).isEqualTo(1);
      assertThat(TestSteps.SENT).singleElement().matches(message -> message.text().contains("Channel digest"));
      assertThat(new DigestWorker(runtime).sendDue()).isZero();
   }



   /*******************************************************************************
    ** The silent bell receives immediate alerts and digests during quiet hours
    ** and during a pause, while the same user's email is suppressed.
    *******************************************************************************/
   @ParameterizedTest
   @ValueSource(strings = {"IMMEDIATE", "DIGEST_HOURLY"})
   void testBellIgnoresQuietHoursAndPause(String mode) throws Exception
   {
      subscribe(mode, null);
      settings(new QRecord().withValue("emailEnabled", true).withValue("emailAddress", "admin@example.com")
         .withValue("quietHoursStart", "11:00").withValue("quietHoursEnd", "16:00"));
      raise("Quiet bell", AlertSeverity.WARNING);
      clock.advance(Duration.ofHours(1));
      if("DIGEST_HOURLY".equals(mode))
      {
         assertThat(new DigestWorker(runtime).sendDue()).isEqualTo(1);
      }
      settings(new QRecord().withValue("quietHoursStart", null).withValue("quietHoursEnd", null)
         .withValue("pausedUntil", clock.instant().plus(Duration.ofHours(2))));
      raise("Paused bell", AlertSeverity.WARNING);
      clock.advance(Duration.ofHours(1));
      if("DIGEST_HOURLY".equals(mode))
      {
         assertThat(new DigestWorker(runtime).sendDue()).isEqualTo(1);
      }
      Integer inAppId = runtime.getChannels().defaultChannel(ChannelType.IN_APP).getValueInteger("id");
      assertThat(items()).filteredOn(r -> inAppId.equals(r.getValueInteger("channelId"))).hasSize(2).allSatisfy(r ->
      {
         assertThat(r.getValueString("suppressedReason")).isNull();
         assertThat(r.getValueString("status")).isEqualTo("SENT");
      });
      assertThat(items()).filteredOn(r -> !inAppId.equals(r.getValueInteger("channelId")))
         .extracting(r -> r.getValueString("suppressedReason")).containsExactly("QUIET_HOURS", "USER_PAUSED");
      assertThat(all(AlertsNotificationRecipient.TABLE_NAME)).hasSize(2);
   }



   /*******************************************************************************
    ** Record email dispatch without an external provider.
    *******************************************************************************/
   public static class RecordingEmailChannel extends TestSteps.RecordingChannel
   {
      /***************************************************************************
       ** Use the standard personal email destination.
       ***************************************************************************/
      @Override
      public String getType()
      {
         return ("EMAIL");
      }
   }



   /*******************************************************************************
    ** A standard channel backed by the test transport.
    *******************************************************************************/
   private QRecord channel() throws QException
   {
      return (runtime.asSystem(() -> AlertsDb.insert(AlertsChannel.TABLE_NAME, new QRecord().withValue("name", "chat").withValue("label", "Chat")
         .withValue("channelType", "SLACK").withValue("enabled", true).withValue("detailLevel", "STANDARD"))));
   }
}
