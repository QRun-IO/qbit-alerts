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


package com.kingsrook.qbits.alerts.notifications;


import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import com.kingsrook.qbits.alerts.AlertsPreferences;
import com.kingsrook.qbits.alerts.AlertsQBitConfig;
import com.kingsrook.qbits.alerts.BaseAlertsTest;
import com.kingsrook.qbits.alerts.api.AlertsApi;
import com.kingsrook.qbits.alerts.engine.AlertsDb;
import com.kingsrook.qbits.alerts.model.AlertsNotification;
import com.kingsrook.qbits.alerts.model.AlertsNotificationPreference;
import com.kingsrook.qbits.alerts.model.AlertsNotificationRecipient;
import com.kingsrook.qbits.alerts.model.AlertsUserPreference;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.alerts.AlertLink;
import com.kingsrook.qqq.backend.core.alerts.NotificationAudience;
import com.kingsrook.qqq.backend.core.alerts.NotificationCategory;
import com.kingsrook.qqq.backend.core.alerts.NotificationSeverity;
import com.kingsrook.qqq.backend.core.alerts.NotificationSignal;
import com.kingsrook.qqq.backend.core.alerts.QAlerts;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.messaging.Content;
import com.kingsrook.qqq.backend.core.model.actions.messaging.SendMessageInput;
import com.kingsrook.qqq.backend.core.model.actions.messaging.SendMessageOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.messaging.QMessagingProviderMetaData;
import com.kingsrook.qqq.backend.core.modules.messaging.MessagingProviderInterface;
import com.kingsrook.qqq.backend.core.modules.messaging.QMessagingProviderDispatcher;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Notification email (immediate and daily digest) and the ready-made notices.
 *******************************************************************************/
class NotificationEmailAndNoticesTest extends BaseAlertsTest
{
   static final List<SendMessageInput> SENT = new CopyOnWriteArrayList<>();



   /*******************************************************************************
    ** Captures messages instead of sending them.
    *******************************************************************************/
   public static class CapturingProvider implements MessagingProviderInterface
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public String getType()
      {
         return ("alerts-capture");
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public SendMessageOutput sendMessage(SendMessageInput sendMessageInput)
      {
         SENT.add(sendMessageInput);
         return (new SendMessageOutput());
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   protected void customizeConfig(AlertsQBitConfig config)
   {
      QMessagingProviderDispatcher.registerMessagingProvider(new CapturingProvider());
      qInstance.addMessagingProvider(new QMessagingProviderMetaData().withName("capture").withType("alerts-capture"));
      SENT.clear();
      config.withMessagingProviderName("capture").withFromAddress("alerts@example.com");
   }



   /*******************************************************************************
    ** A user's email settings: address, time zone and digest hour, and one
    ** category preference.
    *******************************************************************************/
   private void emailSettings(String userId, String category, String mode, String minSeverity) throws QException
   {
      AlertsPreferences.ensure(runtime, loginAs(userId));
      runtime.asSystem(() ->
      {
         QRecord settings = AlertsDb.first(AlertsUserPreference.TABLE_NAME, AlertsDb.eq("userId", userId));
         AlertsDb.update(AlertsUserPreference.TABLE_NAME, new QRecord().withValue("id", settings.getValue("id")).withValue("emailAddress", userId + "@example.com")
            .withValue("timeZone", "UTC").withValue("digestHour", 12));
         AlertsDb.insert(AlertsNotificationPreference.TABLE_NAME, new QRecord().withValue("userId", userId).withValue("category", category)
            .withValue("emailMode", mode).withValue("emailMinSeverity", minSeverity));
         return (null);
      });
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private String emailStatus(String userId) throws QException
   {
      return (runtime.asSystem(() -> AlertsDb.first(AlertsNotificationRecipient.TABLE_NAME, AlertsDb.eq("userId", userId))).getValueString("emailStatus"));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String text(SendMessageInput input)
   {
      return (input.getContentList().stream().map(Content::getBody).reduce("", String::concat));
   }



   /*******************************************************************************
    ** Immediate email at or above the minimum severity; below it is skipped;
    ** users without email settings are left alone.
    *******************************************************************************/
   @Test
   void testImmediate() throws Exception
   {
      emailSettings("viewer-1", "JOB", "IMMEDIATE", "WARNING");
      emailSettings("viewer-2", "JOB", "IMMEDIATE", "ERROR");
      QAlerts.notify(new NotificationSignal("Export failed").withBody("Disk <full>").withSeverity(NotificationSeverity.WARNING)
         .withCategory(NotificationCategory.JOB).withLink(AlertLink.toPath("/app/widget", "Widgets")).withAudience(NotificationAudience.users("viewer-1", "viewer-2", "manager-1")));

      NotificationEmailer.Result result = new NotificationEmailer(runtime).run();
      assertThat(result.sent()).isEqualTo(1);
      assertThat(result.skipped()).isEqualTo(1);
      assertThat(SENT).singleElement().satisfies(message ->
      {
         assertThat(message.getSubject()).isEqualTo("Export failed");
         assertThat(text(message)).contains("Disk &lt;full&gt;").contains("https://admin.example.com/app/widget").contains("My Notification Settings");
      });
      assertThat(emailStatus("viewer-1")).isEqualTo("SENT");
      assertThat(emailStatus("viewer-2")).isEqualTo("SKIPPED");
      assertThat(emailStatus("manager-1")).isEqualTo("NONE");
      assertThat(new NotificationEmailer(runtime).run().sent()).isEqualTo(0);
   }



   /*******************************************************************************
    ** A requested email does not require a standing category preference.
    *******************************************************************************/
   @Test
   void testRequestedWithoutPreference() throws Exception
   {
      addressOnly();
      requested("JOB");
      assertThat(new NotificationEmailer(runtime).run().sent()).isEqualTo(1);
      assertThat(emailStatus("viewer-1")).isEqualTo("SENT");
      assertThat(new NotificationEmailer(runtime).run().sent()).isZero();
      assertThat(SENT).hasSize(1);
   }



   /*******************************************************************************
    ** An explicit request and an immediate preference still send only once.
    *******************************************************************************/
   @Test
   void testRequestedWithImmediatePreference() throws Exception
   {
      emailSettings("viewer-1", "JOB", "IMMEDIATE", "INFO");
      requested("JOB");
      assertThat(new NotificationEmailer(runtime).run().sent()).isEqualTo(1);
      assertThat(new NotificationEmailer(runtime).run().sent()).isZero();
      assertThat(SENT).hasSize(1);
   }



   /*******************************************************************************
    ** A request overrides digest timing (not the category's minimum severity).
    *******************************************************************************/
   @Test
   void testRequestedWithDigestPreference() throws Exception
   {
      emailSettings("viewer-1", "JOB", "DAILY_DIGEST", "INFO");
      requested("JOB");
      NotificationEmailer.Result result = new NotificationEmailer(runtime).run();
      assertThat(result.sent()).isEqualTo(1);
      assertThat(result.digests()).isZero();
      assertThat(SENT).hasSize(1);
   }



   /*******************************************************************************
    ** Knowing an address alone does not opt a notification into email.
    *******************************************************************************/
   @Test
   void testNotRequestedWithoutPreference() throws Exception
   {
      addressOnly();
      runtime.getNotifications().notify(new NotificationSignal("Done").withCategory(NotificationCategory.JOB)
         .withAudience(NotificationAudience.user("viewer-1")));
      assertThat(new NotificationEmailer(runtime).run().sent()).isZero();
      assertThat(emailStatus("viewer-1")).isEqualTo("NONE");
      assertThat(SENT).isEmpty();
   }



   /*******************************************************************************
    ** Missing addresses are skipped rather than counted as delivery failures.
    *******************************************************************************/
   @Test
   void testRequestedWithoutAddress() throws Exception
   {
      requested("JOB");
      NotificationEmailer.Result result = new NotificationEmailer(runtime).run();
      assertThat(result.skipped()).isEqualTo(1);
      assertThat(result.failed()).isZero();
      assertThat(emailStatus("viewer-1")).isEqualTo("SKIPPED");
      assertThat(SENT).isEmpty();
   }



   /*******************************************************************************
    ** Alert subscriptions own alert emails, even with an explicit request.
    *******************************************************************************/
   @Test
   void testRequestedAlertNeverEmailed() throws Exception
   {
      emailSettings("viewer-1", "ALERT", "IMMEDIATE", "INFO");
      requested("ALERT");
      assertThat(new NotificationEmailer(runtime).run().skipped()).isEqualTo(1);
      assertThat(emailStatus("viewer-1")).isEqualTo("SKIPPED");
      assertThat(SENT).isEmpty();
   }



   /*******************************************************************************
    ** Requests older than the delivery window are not emailed.
    *******************************************************************************/
   @Test
   void testRequestedOutsideDeliveryWindow() throws Exception
   {
      addressOnly();
      requested("JOB");
      clock.set(clock.instant().plusSeconds(86401));
      assertThat(new NotificationEmailer(runtime).run().sent()).isZero();
      assertThat(emailStatus("viewer-1")).isEqualTo("REQUESTED");
      assertThat(SENT).isEmpty();
   }



   /*******************************************************************************
    ** Provider failures remain terminal and do not cause repeated sends.
    *******************************************************************************/
   @Test
   void testRequestedDeliveryFailure() throws Exception
   {
      addressOnly();
      requested("JOB");
      runtime.getConfig().withMessagingProviderName("missing-provider");
      NotificationEmailer.Result result = new NotificationEmailer(runtime).run();
      assertThat(result.failed()).isEqualTo(1);
      assertThat(emailStatus("viewer-1")).isEqualTo("FAILED");
      assertThat(new NotificationEmailer(runtime).run().failed()).isZero();
      assertThat(SENT).isEmpty();
   }



   /*******************************************************************************
    ** A request still honours the category's email minimum severity.
    *******************************************************************************/
   @Test
   void testRequestedBelowMinSeverity() throws Exception
   {
      emailSettings("viewer-1", "JOB", "OFF", "ERROR");
      requested("JOB");
      NotificationEmailer.Result result = new NotificationEmailer(runtime).run();
      assertThat(result.skipped()).isEqualTo(1);
      assertThat(emailStatus("viewer-1")).isEqualTo("SKIPPED");
      assertThat(SENT).isEmpty();
   }



   /*******************************************************************************
    ** A user's pause or quiet hours hold a requested email until they end.
    *******************************************************************************/
   @Test
   void testRequestedHeldByPauseAndQuietHours() throws Exception
   {
      clock.set(Instant.parse("2026-10-12T12:00:00Z"));
      addressOnly();
      requested("JOB");
      setUserPreference(new QRecord().withValue("pausedUntil", clock.instant().plusSeconds(3600)));
      assertThat(new NotificationEmailer(runtime).run().sent()).isZero();
      assertThat(emailStatus("viewer-1")).isEqualTo("REQUESTED");

      clock.set(Instant.parse("2026-10-12T13:30:00Z"));
      setUserPreference(new QRecord().withValue("timeZone", "UTC").withValue("quietHoursStart", "13:00").withValue("quietHoursEnd", "14:00"));
      assertThat(new NotificationEmailer(runtime).run().sent()).isZero();
      assertThat(emailStatus("viewer-1")).isEqualTo("REQUESTED");

      clock.set(Instant.parse("2026-10-12T14:00:00Z"));
      assertThat(new NotificationEmailer(runtime).run().sent()).isEqualTo(1);
      assertThat(emailStatus("viewer-1")).isEqualTo("SENT");
      assertThat(SENT).hasSize(1);
   }



   /*******************************************************************************
    ** A pause does not hold a requested SECURITY email.
    *******************************************************************************/
   @Test
   void testRequestedSecurityBypassesPause() throws Exception
   {
      addressOnly();
      requested("SECURITY");
      setUserPreference(new QRecord().withValue("pausedUntil", clock.instant().plusSeconds(3600)));
      assertThat(new NotificationEmailer(runtime).run().sent()).isEqualTo(1);
   }



   /*******************************************************************************
    ** Update the user's alert settings row.
    *******************************************************************************/
   private void setUserPreference(QRecord values) throws QException
   {
      runtime.asSystem(() ->
      {
         QRecord setting = AlertsDb.first(AlertsUserPreference.TABLE_NAME, AlertsDb.eq("userId", "viewer-1"));
         return (AlertsDb.update(AlertsUserPreference.TABLE_NAME, values.withValue("id", setting.getValue("id"))));
      });
   }



   /*******************************************************************************
    ** Store an address without opting into any category.
    *******************************************************************************/
   private void addressOnly() throws QException
   {
      runtime.asSystem(() -> AlertsDb.insert(AlertsUserPreference.TABLE_NAME,
         new QRecord().withValue("userId", "viewer-1").withValue("emailAddress", "viewer-1@example.com")));
   }



   /*******************************************************************************
    ** Set up a recipient explicitly requesting email for worker tests.
    *******************************************************************************/
   private void requested(String category) throws QException
   {
      Integer id = runtime.getNotifications().notify(new NotificationSignal("Done").withCategory(NotificationCategory.valueOf(category))
         .withAudience(NotificationAudience.user("viewer-1")));
      runtime.asSystem(() ->
      {
         QRecord recipient = AlertsDb.first(AlertsNotificationRecipient.TABLE_NAME, AlertsDb.eq("notificationId", id));
         return (AlertsDb.update(AlertsNotificationRecipient.TABLE_NAME,
            new QRecord().withValue("id", recipient.getValue("id")).withValue("emailStatus", "REQUESTED")));
      });
   }



   /*******************************************************************************
    ** The daily digest waits for the user's hour, then lists the unread ones.
    *******************************************************************************/
   @Test
   void testDigest() throws Exception
   {
      clock.set(Instant.parse("2026-10-12T09:00:00Z"));
      emailSettings("viewer-1", "RELEASE", "DAILY_DIGEST", "INFO");
      AlertsNotices.release("2.4", "Dark mode everywhere.", null);
      QAlerts.notify(new NotificationSignal("Version 2.5 is out").withCategory(NotificationCategory.RELEASE).withAudience(NotificationAudience.user("viewer-1")));

      assertThat(new NotificationEmailer(runtime).run().digests()).isEqualTo(0);
      clock.set(Instant.parse("2026-10-12T12:05:00Z"));
      assertThat(new NotificationEmailer(runtime).run().digests()).isEqualTo(1);
      assertThat(SENT).singleElement().satisfies(message ->
      {
         assertThat(message.getSubject()).isEqualTo("2 new notifications");
         assertThat(text(message)).contains("Version 2.4 is out").contains("Dark mode everywhere.").contains("Version 2.5 is out");
      });
      assertThat(new NotificationEmailer(runtime).run().digests()).isEqualTo(0);
      assertThat(NotificationEmailer.isDigestHour(new QRecord().withValue("timeZone", "Not/AZone").withValue("digestHour", 12), clock.instant())).isTrue();
      assertThat(NotificationEmailer.atLeast("CRITICAL", null)).isTrue();
      assertThat(NotificationEmailer.atLeast("INFO", null)).isFalse();
   }



   /*******************************************************************************
    ** Security notices: no body in email; the notices helpers.
    *******************************************************************************/
   @Test
   void testNotices() throws Exception
   {
      emailSettings("viewer-1", "SECURITY", "IMMEDIATE", "INFO");
      loginAs("viewer-1");
      AlertsNotices.newSignIn("viewer-1", "Firefox on Windows");
      AlertsNotices.apiKeyExpiring("viewer-1", "ci-bot", Instant.parse("2026-11-01T00:00:00Z"));
      AlertsNotices.apiKeyExpiring("viewer-1", "ci-bot", Instant.parse("2026-11-01T00:00:00Z"));
      AlertsNotices.permissionChanged("viewer-1", "You were added to the Editors role");
      AlertsNotices.shared("viewer-1", "Sam shared a notebook with you", AlertLink.toPath("/app/widget", "Open"), "qbit-sql-workbench");

      List<QRecord> notifications = runtime.asSystem(() -> all(AlertsNotification.TABLE_NAME));
      assertThat(notifications).hasSize(4);
      assertThat(notifications).filteredOn(n -> "SECURITY".equals(n.getValueString("category"))).hasSize(3);
      assertThat(notifications).anyMatch(n -> n.getValueString("title").equals("API key expires Nov 1, 2026: ci-bot"));
      assertThat(notifications).noneMatch(n -> n.getValueString("title").startsWith("API key created"));

      new NotificationEmailer(runtime).run();
      assertThat(SENT).hasSize(3);
      assertThat(SENT).noneMatch(message -> text(message).contains("change your password"));
   }



   /*******************************************************************************
    ** Pauses defer immediate and digest mail past a day without hiding the inbox;
    ** SECURITY still sends in both email modes.
    *******************************************************************************/
   @Test
   void testPausedEmail() throws Exception
   {
      emailSettings("viewer-1", "JOB", "IMMEDIATE", "INFO");
      emailSettings("viewer-1", "SECURITY", "IMMEDIATE", "INFO");
      emailSettings("viewer-2", "JOB", "DAILY_DIGEST", "INFO");
      emailSettings("viewer-2", "SECURITY", "DAILY_DIGEST", "INFO");
      runtime.asSystem(() ->
      {
         for(QRecord setting : all(AlertsUserPreference.TABLE_NAME))
         {
            AlertsDb.update(AlertsUserPreference.TABLE_NAME, new QRecord().withValue("id", setting.getValue("id"))
               .withValue("pausedUntil", clock.instant().plus(Duration.ofDays(2))));
         }
         return (null);
      });
      for(NotificationCategory category : List.of(NotificationCategory.JOB, NotificationCategory.SECURITY))
      {
         runtime.getNotifications().notify(new NotificationSignal(category.name()).withCategory(category).withAudience(NotificationAudience.users("viewer-1", "viewer-2")));
      }
      NotificationEmailer.Result paused = new NotificationEmailer(runtime).run();
      assertThat(paused.sent()).isEqualTo(1);
      assertThat(paused.digests()).isEqualTo(1);
      assertThat(paused.skipped()).isEqualTo(2);
      assertThat(SENT).hasSize(2).allSatisfy(message -> assertThat(text(message)).contains("SECURITY").doesNotContain("JOB"));
      assertThat(all(AlertsNotificationRecipient.TABLE_NAME)).filteredOn(row -> row.getValueInstant("emailedAt") == null).hasSize(2)
         .allSatisfy(row -> assertThat(row.getValueString("emailStatus")).isEqualTo("NONE"));
      assertThat(new AlertsApi(runtime).inboxSummary()).containsEntry("unreadCount", 2);
      clock.advance(Duration.ofDays(2));
      NotificationEmailer.Result resumed = new NotificationEmailer(runtime).run();
      assertThat(resumed.sent()).isEqualTo(1);
      assertThat(resumed.digests()).isEqualTo(1);
      assertThat(SENT).hasSize(4);
      assertThat(all(AlertsNotificationRecipient.TABLE_NAME)).allSatisfy(row -> assertThat(row.getValueInstant("emailedAt")).isNotNull());
      assertThat(new NotificationEmailer(runtime).run().sent()).isZero();
   }



   /*******************************************************************************
    ** A full scan page of paused recipients cannot starve a later security notice.
    *******************************************************************************/
   @Test
   void testPausedBacklogDoesNotBlockSecurity() throws Exception
   {
      emailSettings(VIEWER, "JOB", "IMMEDIATE", "INFO");
      emailSettings(VIEWER, "SECURITY", "IMMEDIATE", "INFO");
      runtime.asSystem(() ->
      {
         QRecord settings = AlertsDb.first(AlertsUserPreference.TABLE_NAME, AlertsDb.eq("userId", VIEWER));
         AlertsDb.update(AlertsUserPreference.TABLE_NAME, new QRecord().withValue("id", settings.getValue("id"))
            .withValue("pausedUntil", clock.instant().plusSeconds(3600)));
         List<QRecord> notifications = new ArrayList<>();
         for(int i = 0; i < NotificationEmailer.SCAN; i++)
         {
            notifications.add(new QRecord().withValue("title", "Paused job " + i).withValue("category", "JOB").withValue("status", "SENT"));
         }
         notifications = InsertAction.executeForRecords(
            new InsertInput(AlertsNotification.TABLE_NAME).withRecords(notifications));
         List<QRecord> recipients = notifications.stream().map(notification -> new QRecord().withValue("notificationId", notification.getValue("id"))
            .withValue("userId", VIEWER).withValue("deliveredAt", clock.instant())).toList();
         InsertAction.executeForRecords(
            new InsertInput(AlertsNotificationRecipient.TABLE_NAME).withRecords(recipients));
         return (null);
      });
      runtime.getNotifications().notify(new NotificationSignal("Security after backlog").withCategory(NotificationCategory.SECURITY).withAudience(NotificationAudience.user(VIEWER)));
      assertThat(new NotificationEmailer(runtime).run().sent()).isEqualTo(1);
      assertThat(SENT).singleElement().satisfies(message -> assertThat(message.getSubject()).isEqualTo("Security after backlog"));
   }
}
