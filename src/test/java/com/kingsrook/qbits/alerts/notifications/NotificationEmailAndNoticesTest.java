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


import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import com.kingsrook.qbits.alerts.AlertsPreferences;
import com.kingsrook.qbits.alerts.AlertsQBitConfig;
import com.kingsrook.qbits.alerts.BaseAlertsTest;
import com.kingsrook.qbits.alerts.engine.AlertsDb;
import com.kingsrook.qbits.alerts.model.AlertsNotification;
import com.kingsrook.qbits.alerts.model.AlertsNotificationPreference;
import com.kingsrook.qbits.alerts.model.AlertsNotificationRecipient;
import com.kingsrook.qbits.alerts.model.AlertsUserPreference;
import com.kingsrook.qbits.alerts.spi.AlertLink;
import com.kingsrook.qbits.alerts.spi.NotificationAudience;
import com.kingsrook.qbits.alerts.spi.NotificationCategory;
import com.kingsrook.qbits.alerts.spi.NotificationSeverity;
import com.kingsrook.qbits.alerts.spi.NotificationSignal;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.messaging.Content;
import com.kingsrook.qqq.backend.core.model.actions.messaging.SendMessageInput;
import com.kingsrook.qqq.backend.core.model.actions.messaging.SendMessageOutput;
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
      runtime.getNotifications().notify(new NotificationSignal("Export failed").withBody("Disk <full>").withSeverity(NotificationSeverity.WARNING)
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
    ** The daily digest waits for the user's hour, then lists the unread ones.
    *******************************************************************************/
   @Test
   void testDigest() throws Exception
   {
      clock.set(Instant.parse("2026-10-12T09:00:00Z"));
      emailSettings("viewer-1", "RELEASE", "DAILY_DIGEST", "INFO");
      AlertsNotices.release("2.4", "Dark mode everywhere.", null);
      runtime.getNotifications().notify(new NotificationSignal("Version 2.5 is out").withCategory(NotificationCategory.RELEASE).withAudience(NotificationAudience.user("viewer-1")));

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
      AlertsNotices.apiKeyCreated("viewer-1", "ci-bot");
      AlertsNotices.apiKeyExpiring("viewer-1", "ci-bot", Instant.parse("2026-11-01T00:00:00Z"));
      AlertsNotices.apiKeyExpiring("viewer-1", "ci-bot", Instant.parse("2026-11-01T00:00:00Z"));
      AlertsNotices.permissionChanged("viewer-1", "You were added to the Editors role");
      AlertsNotices.shared("viewer-1", "Sam shared a notebook with you", AlertLink.toPath("/app/widget", "Open"), "qbit-sql-workbench");

      List<QRecord> notifications = runtime.asSystem(() -> all(AlertsNotification.TABLE_NAME));
      assertThat(notifications).hasSize(5);
      assertThat(notifications).filteredOn(n -> "SECURITY".equals(n.getValueString("category"))).hasSize(4);
      assertThat(notifications).anyMatch(n -> n.getValueString("title").equals("API key expires Nov 1, 2026: ci-bot"));

      new NotificationEmailer(runtime).run();
      assertThat(SENT).hasSize(4);
      assertThat(SENT).noneMatch(message -> text(message).contains("change your password"));
   }
}
