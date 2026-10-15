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
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import com.kingsrook.qbits.alerts.AlertsPreferences;
import com.kingsrook.qbits.alerts.AlertsQBitConfig;
import com.kingsrook.qbits.alerts.BaseAlertsTest;
import com.kingsrook.qbits.alerts.engine.AlertsDb;
import com.kingsrook.qbits.alerts.model.AlertsNotificationPreference;
import com.kingsrook.qbits.alerts.model.AlertsNotificationRecipient;
import com.kingsrook.qbits.alerts.model.AlertsUserPreference;
import com.kingsrook.qqq.backend.core.alerts.AlertLink;
import com.kingsrook.qqq.backend.core.alerts.NotificationAudience;
import com.kingsrook.qqq.backend.core.alerts.NotificationCategory;
import com.kingsrook.qqq.backend.core.alerts.NotificationSeverity;
import com.kingsrook.qqq.backend.core.alerts.NotificationSignal;
import com.kingsrook.qqq.backend.core.alerts.QAlerts;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QUserFacingException;
import com.kingsrook.qqq.backend.core.model.actions.messaging.Content;
import com.kingsrook.qqq.backend.core.model.actions.messaging.SendMessageInput;
import com.kingsrook.qqq.backend.core.model.actions.messaging.SendMessageOutput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.messaging.QMessagingProviderMetaData;
import com.kingsrook.qqq.backend.core.modules.messaging.MessagingProviderCapability;
import com.kingsrook.qqq.backend.core.modules.messaging.MessagingProviderInterface;
import com.kingsrook.qqq.backend.core.modules.messaging.QMessagingProviderDispatcher;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Per-user Slack notifications (immediate and daily digest) through a fake
 ** core messaging provider registered as "slack".
 *******************************************************************************/
class NotificationSlackTest extends BaseAlertsTest
{
   static final List<SendMessageInput> SENT = new CopyOnWriteArrayList<>();

   static volatile String failureMode = "NONE";
   static boolean idempotency;



   /*******************************************************************************
    ** Records inputs instead of sending them; can be told to fail.
    *******************************************************************************/
   public static class FakeSlackProvider implements MessagingProviderInterface
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public String getType()
      {
         return ("alerts-slack-capture");
      }



      /*******************************************************************************
       ** Advertise optional provider deduplication.
       *******************************************************************************/
      @Override
      public Set<MessagingProviderCapability> getCapabilities()
      {
         return (idempotency ? Set.of(MessagingProviderCapability.IDEMPOTENCY) : Set.of());
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public SendMessageOutput sendMessage(SendMessageInput sendMessageInput) throws QException
      {
         if("NO_CONNECTION".equals(failureMode))
         {
            throw (new QUserFacingException("Connect Slack first."));
         }
         if("BOOM".equals(failureMode))
         {
            throw (new IllegalStateException("Slack is down"));
         }
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
      QMessagingProviderDispatcher.registerMessagingProvider(new FakeSlackProvider());
      qInstance.addMessagingProvider(new QMessagingProviderMetaData().withName("slack").withType("alerts-slack-capture"));
      SENT.clear();
      failureMode = "NONE";
      idempotency = false;
   }



   /*******************************************************************************
    ** A user's settings (UTC, digest hour 12, and any extra settings) and one
    ** category preference for Slack.
    *******************************************************************************/
   private void slackSettings(String userId, String category, String mode, String minSeverity, QRecord extra) throws QException
   {
      AlertsPreferences.ensure(runtime, loginAs(userId));
      runtime.asSystem(() ->
      {
         QRecord settings = AlertsDb.first(AlertsUserPreference.TABLE_NAME, AlertsDb.eq("userId", userId));
         QRecord update   = new QRecord().withValue("id", settings.getValue("id")).withValue("timeZone", "UTC").withValue("digestHour", 12);
         if(extra != null)
         {
            extra.getValues().forEach(update::setValue);
         }
         AlertsDb.update(AlertsUserPreference.TABLE_NAME, update);
         AlertsDb.insert(AlertsNotificationPreference.TABLE_NAME, new QRecord().withValue("userId", userId).withValue("category", category)
            .withValue("slackMode", mode).withValue("slackMinSeverity", minSeverity).withValue("inAppMuted", false));
         return (null);
      });
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private String slackStatus(String userId) throws QException
   {
      return (runtime.asSystem(() -> AlertsDb.first(AlertsNotificationRecipient.TABLE_NAME, AlertsDb.eq("userId", userId))).getValueString("slackStatus"));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String text(SendMessageInput input)
   {
      return (input.getContentList().stream().map(Content::getBody).reduce("", String::concat));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void notifyJob(String title, NotificationSeverity severity, String... userIds)
   {
      QAlerts.notify(new NotificationSignal(title).withBody("Disk full").withSeverity(severity).withCategory(NotificationCategory.JOB)
         .withLink(AlertLink.toPath("/app/widget", "Widgets")).withAudience(NotificationAudience.users(userIds)));
   }



   /*******************************************************************************
    ** IMMEDIATE sends one DM to user:<id> through the Slack provider; below
    ** the minimum severity is skipped; OFF and users without a row are left
    ** alone; running again does not send twice.
    *******************************************************************************/
   @Test
   void testImmediate() throws Exception
   {
      slackSettings("viewer-1", "JOB", "IMMEDIATE", "WARNING", null);
      slackSettings("viewer-2", "JOB", "IMMEDIATE", "ERROR", null);
      slackSettings("viewer-3", "JOB", "OFF", "INFO", null);
      notifyJob("Export failed", NotificationSeverity.WARNING, "viewer-1", "viewer-2", "viewer-3", "manager-1");

      NotificationSlackSender.Result result = new NotificationSlackSender(runtime).run();
      assertThat(result.sent()).isEqualTo(1);
      assertThat(result.skipped()).isEqualTo(1);
      assertThat(SENT).singleElement().satisfies(message ->
      {
         assertThat(message.getMessagingProviderName()).isEqualTo("slack");
         assertThat(message.getTo().getAddress()).isEqualTo("user:viewer-1");
         assertThat(message.getSubject()).isEqualTo("Export failed");
         assertThat(message.getIdempotencyKey()).isNull();
         assertThat(text(message)).contains("Export failed").contains("Disk full").contains("https://admin.example.com/app/widget");
      });
      assertThat(slackStatus("viewer-1")).isEqualTo("SENT");
      assertThat(slackStatus("viewer-2")).isEqualTo("SKIPPED");
      assertThat(slackStatus("viewer-3")).isEqualTo("NONE");
      assertThat(slackStatus("manager-1")).isEqualTo("NONE");

      assertThat(new NotificationSlackSender(runtime).run().sent()).isEqualTo(0);
      assertThat(SENT).hasSize(1);
   }



   /*******************************************************************************
    ** Capable providers receive a stable key for immediate notifications.
    *******************************************************************************/
   @Test
   void testIdempotencyKey() throws Exception
   {
      idempotency = true;
      slackSettings("viewer-1", "JOB", "IMMEDIATE", "WARNING", null);
      notifyJob("Export failed", NotificationSeverity.WARNING, "viewer-1");
      assertThat(new NotificationSlackSender(runtime).run().sent()).isEqualTo(1);
      assertThat(SENT).singleElement().satisfies(message ->
         assertThat(message.getIdempotencyKey()).matches("notify:[0-9]+:viewer-1:slack"));
   }



   /*******************************************************************************
    ** A category muted in-app gets no recipient row, so no Slack message.
    *******************************************************************************/
   @Test
   void testMuted() throws Exception
   {
      slackSettings("viewer-1", "JOB", "IMMEDIATE", "INFO", null);
      runtime.asSystem(() -> AlertsDb.update(AlertsNotificationPreference.TABLE_NAME, new QRecord()
         .withValue("id", AlertsDb.first(AlertsNotificationPreference.TABLE_NAME, AlertsDb.eq("userId", "viewer-1")).getValue("id")).withValue("inAppMuted", true)));
      notifyJob("Export failed", NotificationSeverity.ERROR, "viewer-1");

      assertThat(new NotificationSlackSender(runtime).run().sent()).isEqualTo(0);
      assertThat(SENT).isEmpty();
   }



   /*******************************************************************************
    ** Quiet hours hold the DM (the bell is untouched); CRITICAL bypasses; it
    ** goes out once the quiet hours end.
    *******************************************************************************/
   @Test
   void testQuietHours() throws Exception
   {
      clock.set(Instant.parse("2026-10-12T23:00:00Z"));
      slackSettings("viewer-1", "JOB", "IMMEDIATE", "INFO", new QRecord().withValue("quietHoursStart", "22:00").withValue("quietHoursEnd", "07:00"));
      notifyJob("Export failed", NotificationSeverity.ERROR, "viewer-1");
      notifyJob("Database down", NotificationSeverity.CRITICAL, "viewer-1");

      NotificationSlackSender.Result result = new NotificationSlackSender(runtime).run();
      assertThat(result.sent()).isEqualTo(1);
      assertThat(result.held()).isEqualTo(1);
      assertThat(SENT).singleElement().satisfies(message -> assertThat(message.getSubject()).isEqualTo("Database down"));
      assertThat(runtime.asSystem(() -> AlertsDb.count(AlertsNotificationRecipient.TABLE_NAME, AlertsDb.eq("userId", "viewer-1")))).isEqualTo(2);

      clock.set(Instant.parse("2026-10-13T07:05:00Z"));
      assertThat(new NotificationSlackSender(runtime).run().sent()).isEqualTo(1);
      assertThat(SENT).hasSize(2);
   }



   /*******************************************************************************
    ** A paused user gets nothing until the pause ends (even CRITICAL).
    *******************************************************************************/
   @Test
   void testPaused() throws Exception
   {
      clock.set(Instant.parse("2026-10-12T10:00:00Z"));
      slackSettings("viewer-1", "JOB", "IMMEDIATE", "INFO", new QRecord().withValue("pausedUntil", Instant.parse("2026-10-14T10:00:00Z")));
      notifyJob("Database down", NotificationSeverity.CRITICAL, "viewer-1");

      assertThat(new NotificationSlackSender(runtime).run().held()).isEqualTo(1);
      assertThat(SENT).isEmpty();
      assertThat(slackStatus("viewer-1")).isEqualTo("NONE");
      clock.advance(Duration.ofDays(2));
      assertThat(new NotificationSlackSender(runtime).run().sent()).isEqualTo(1);
      assertThat(slackStatus("viewer-1")).isEqualTo("SENT");
   }



   /*******************************************************************************
    ** Pauses hold JOB items in both Slack modes, even in a digest containing
    ** SECURITY. After two days every held item is delivered exactly once.
    *******************************************************************************/
   @Test
   void testPausedSecurityAndMixedDigest() throws Exception
   {
      clock.set(Instant.parse("2026-10-12T12:05:00Z"));
      for(String userId : List.of("viewer-1", "viewer-2"))
      {
         String mode = "viewer-1".equals(userId) ? "IMMEDIATE" : "DAILY_DIGEST";
         QRecord paused = new QRecord().withValue("pausedUntil", clock.instant().plus(Duration.ofDays(2)));
         slackSettings(userId, "JOB", mode, "INFO", paused);
         slackSettings(userId, "SECURITY", mode, "INFO", paused);
      }
      for(NotificationCategory category : List.of(NotificationCategory.JOB, NotificationCategory.SECURITY))
      {
         QAlerts.notify(new NotificationSignal(category.name()).withCategory(category).withSeverity(NotificationSeverity.CRITICAL)
            .withAudience(NotificationAudience.users("viewer-1", "viewer-2")));
      }
      NotificationSlackSender.Result paused = new NotificationSlackSender(runtime).run();
      assertThat(paused.sent()).isEqualTo(1);
      assertThat(paused.digests()).isEqualTo(1);
      assertThat(paused.held()).isEqualTo(2);
      assertThat(SENT).hasSize(2).allSatisfy(message -> assertThat(text(message)).contains("SECURITY").doesNotContain("JOB"));
      clock.advance(Duration.ofDays(2));
      NotificationSlackSender.Result resumed = new NotificationSlackSender(runtime).run();
      assertThat(resumed.sent()).isEqualTo(1);
      assertThat(resumed.digests()).isEqualTo(1);
      assertThat(SENT).hasSize(4);
      assertThat(new NotificationSlackSender(runtime).run()).isEqualTo(new NotificationSlackSender.Result(0, 0, 0, 0, 0));
   }



   /*******************************************************************************
    ** No provider under the configured name: SKIPPED, nothing thrown.
    *******************************************************************************/
   @Test
   void testNoProvider() throws Exception
   {
      runtime.getConfig().withSlackProviderName("not-registered");
      slackSettings("viewer-1", "JOB", "IMMEDIATE", "INFO", null);
      notifyJob("Export failed", NotificationSeverity.ERROR, "viewer-1");

      assertThat(new NotificationSlackSender(runtime).run().skipped()).isEqualTo(1);
      assertThat(SENT).isEmpty();
      assertThat(slackStatus("viewer-1")).isEqualTo("SKIPPED");
   }



   /*******************************************************************************
    ** A user-facing error (no Slack connection) is SKIPPED and not retried;
    ** any other error is FAILED.
    *******************************************************************************/
   @Test
   void testErrors() throws Exception
   {
      slackSettings("viewer-1", "JOB", "IMMEDIATE", "INFO", null);
      slackSettings("viewer-2", "JOB", "IMMEDIATE", "INFO", null);

      failureMode = "NO_CONNECTION";
      notifyJob("Export failed", NotificationSeverity.ERROR, "viewer-1");
      NotificationSlackSender.Result first = new NotificationSlackSender(runtime).run();
      assertThat(first.skipped()).isEqualTo(1);
      assertThat(slackStatus("viewer-1")).isEqualTo("SKIPPED");

      failureMode = "BOOM";
      notifyJob("Another failure", NotificationSeverity.ERROR, "viewer-2");
      NotificationSlackSender.Result second = new NotificationSlackSender(runtime).run();
      assertThat(second.failed()).isEqualTo(1);
      assertThat(slackStatus("viewer-2")).isEqualTo("FAILED");

      failureMode = "NONE";
      assertThat(new NotificationSlackSender(runtime).run().sent()).isEqualTo(0);
      assertThat(SENT).isEmpty();
   }



   /*******************************************************************************
    ** SECURITY sends the title and link, never the body.
    *******************************************************************************/
   @Test
   void testSecurityHasNoBody() throws Exception
   {
      slackSettings("viewer-1", "SECURITY", "IMMEDIATE", "INFO", null);
      QAlerts.notify(new NotificationSignal("New sign-in").withBody("Firefox on Windows, please change your password").withCategory(NotificationCategory.SECURITY)
         .withSeverity(NotificationSeverity.WARNING).withLink(AlertLink.toPath("/app/widget", "Review")).withAudience(NotificationAudience.user("viewer-1")));

      assertThat(new NotificationSlackSender(runtime).run().sent()).isEqualTo(1);
      assertThat(SENT).singleElement().satisfies(message ->
      {
         assertThat(text(message)).contains("New sign-in").contains("https://admin.example.com/app/widget");
         assertThat(text(message)).doesNotContain("Firefox").doesNotContain("password");
      });
   }



   /*******************************************************************************
    ** The digest waits for the user's hour, then sends one DM listing their
    ** unread notifications and marks them DIGESTED; email-only users get none.
    *******************************************************************************/
   @Test
   void testDigest() throws Exception
   {
      clock.set(Instant.parse("2026-10-12T09:00:00Z"));
      slackSettings("viewer-1", "JOB", "DAILY_DIGEST", "INFO", null);
      notifyJob("Export failed", NotificationSeverity.WARNING, "viewer-1");
      notifyJob("Import failed", NotificationSeverity.ERROR, "viewer-1");

      assertThat(new NotificationSlackSender(runtime).run().digests()).isEqualTo(0);
      assertThat(SENT).isEmpty();
      clock.set(Instant.parse("2026-10-12T12:05:00Z"));
      assertThat(new NotificationSlackSender(runtime).run().digests()).isEqualTo(1);
      assertThat(SENT).singleElement().satisfies(message ->
      {
         assertThat(message.getTo().getAddress()).isEqualTo("user:viewer-1");
         assertThat(message.getSubject()).isEqualTo("2 new notifications");
         assertThat(text(message)).contains("Export failed").contains("Import failed");
      });
      assertThat(runtime.asSystem(() -> AlertsDb.query(AlertsNotificationRecipient.TABLE_NAME, AlertsDb.eq("userId", "viewer-1"))))
         .allSatisfy(recipient -> assertThat(recipient.getValueString("slackStatus")).isEqualTo("DIGESTED"));
      assertThat(new NotificationSlackSender(runtime).run().digests()).isEqualTo(0);
      assertThat(SENT).hasSize(1);
   }
}
