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

package com.kingsrook.qbits.alerts.email;


import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import com.kingsrook.qbits.alerts.AlertsPreferences;
import com.kingsrook.qbits.alerts.AlertsQBitConfig;
import com.kingsrook.qbits.alerts.BaseAlertsTest;
import com.kingsrook.qbits.alerts.delivery.AlertMessage;
import com.kingsrook.qbits.alerts.delivery.DeliveryResult;
import com.kingsrook.qbits.alerts.delivery.EmailChannel;
import com.kingsrook.qbits.alerts.engine.AlertsDb;
import com.kingsrook.qbits.alerts.model.AlertsNotificationPreference;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.ChannelType;
import com.kingsrook.qbits.alerts.model.AlertsUserPreference;
import com.kingsrook.qbits.alerts.notifications.NotificationEmailer;
import com.kingsrook.qbits.email.EmailQBitConfig;
import com.kingsrook.qbits.email.EmailQBitProducer;
import com.kingsrook.qbits.email.EmailRuntime;
import com.kingsrook.qbits.email.metadata.EmailTables;
import com.kingsrook.qqq.backend.core.alerts.AlertLink;
import com.kingsrook.qqq.backend.core.alerts.NotificationAudience;
import com.kingsrook.qqq.backend.core.alerts.NotificationCategory;
import com.kingsrook.qqq.backend.core.alerts.NotificationSeverity;
import com.kingsrook.qqq.backend.core.alerts.NotificationSignal;
import com.kingsrook.qqq.backend.core.alerts.QAlerts;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSBackendMetaData;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Email through qbit-email: with the email qbit produced on the same H2
 ** instance, alert channel emails, notification emails and digests become
 ** email_message rows (one recipient each, the right template, category,
 ** idempotency key and linked record); a repeat is a duplicate; no provider
 ** is a refusal; an unsubscribed address is skipped.
 *******************************************************************************/
class EmailQBitSendingTest extends BaseAlertsTest
{
   /*******************************************************************************
    ** The email qbit in the same instance, and the alerts config without a
    ** messaging provider (qbit-email makes it unnecessary).
    *******************************************************************************/
   @Override
   protected void customizeInstance(QInstance qInstance)
   {
      try
      {
         new EmailQBitProducer().withQBitConfig(new EmailQBitConfig().withBackendName(BACKEND_NAME).withPublicBaseUrl("https://admin.example.com")).produce(qInstance);
      }
      catch(Exception e)
      {
         throw (new IllegalStateException(e));
      }
   }



   /*******************************************************************************
    ** The email tables, next to the alerts ones (a clean database each test).
    *******************************************************************************/
   @Override
   protected void primeDatabase() throws Exception
   {
      try(Connection connection = ConnectionManager.getConnection((RDBMSBackendMetaData) qInstance.getBackend(BACKEND_NAME)); Statement statement = connection.createStatement())
      {
         statement.execute("DROP ALL OBJECTS");
      }
      super.primeDatabase();
      String script;
      try(InputStream in = EmailQBitSendingTest.class.getClassLoader().getResourceAsStream("email-h2.sql"))
      {
         script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
      try(Connection connection = ConnectionManager.getConnection((RDBMSBackendMetaData) qInstance.getBackend(BACKEND_NAME)); Statement statement = connection.createStatement())
      {
         for(String sql : script.split(";"))
         {
            if(!sql.isBlank())
            {
               statement.execute(sql);
            }
         }
      }
   }



   /*******************************************************************************
    ** An enabled default provider (messages are queued, not sent, in these tests).
    *******************************************************************************/
   private void addProvider() throws Exception
   {
      runtime.asSystem(() -> AlertsDb.insert(EmailTables.PROVIDER, new QRecord().withValue("name", "relay").withValue("label", "Relay").withValue("kind", "SMTP")
         .withValue("host", "127.0.0.1").withValue("port", 2525).withValue("security", "NONE").withValue("fromAddress", "sender@example.com")
         .withValue("enabled", true).withValue("isDefault", true)));
   }



   /*******************************************************************************
    ** The queued messages, oldest first.
    *******************************************************************************/
   private List<QRecord> messages() throws QException
   {
      return (runtime.asSystem(() -> AlertsDb.query(EmailTables.MESSAGE, new com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter().withOrderBy(
         new com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy("id")))));
   }



   /*******************************************************************************
    ** The name of a message's template.
    *******************************************************************************/
   private String templateName(QRecord message) throws QException
   {
      return (runtime.asSystem(() -> AlertsDb.byId(EmailTables.TEMPLATE, message.getValue("templateId"))).getValueString("name"));
   }



   /*******************************************************************************
    ** A user's email settings and one category preference (as the
    ** notification email tests do).
    *******************************************************************************/
   private void emailSettings(String userId, String category, String mode) throws QException
   {
      AlertsPreferences.ensure(runtime, loginAs(userId));
      runtime.asSystem(() ->
      {
         QRecord settings = AlertsDb.first(AlertsUserPreference.TABLE_NAME, AlertsDb.eq("userId", userId));
         AlertsDb.update(AlertsUserPreference.TABLE_NAME, new QRecord().withValue("id", settings.getValue("id")).withValue("emailAddress", userId + "@example.com")
            .withValue("timeZone", "UTC").withValue("digestHour", 12));
         AlertsDb.insert(AlertsNotificationPreference.TABLE_NAME, new QRecord().withValue("userId", userId).withValue("category", category)
            .withValue("emailMode", mode).withValue("emailMinSeverity", "INFO"));
         return (null);
      });
   }



   /*******************************************************************************
    ** An alert message for alert 5.
    *******************************************************************************/
   private static AlertMessage alertMessage()
   {
      return (new AlertMessage("[ERROR] Export failed", "text", "<p>html</p>", "ERROR", "RAISED", List.of(5), "https://admin.example.com/app/alerts_alert/5",
         List.of(new AlertMessage.Entry(5, "jobs.export", "Jobs", "k", "ERROR", "OPEN", "Export failed", "3 records failed", "Nightly", 3,
            "https://admin.example.com/app/alerts_alert/5", "2026-10-12T12:00:00Z", null))));
   }



   /*******************************************************************************
    ** The qbit is detected, and the email channel needs no messaging provider.
    *******************************************************************************/
   @Test
   void testActiveWithoutMessagingProvider() throws Exception
   {
      assertThat(AlertsEmail.isActive()).isTrue();
      assertThat(AlertsEmail.isActive(null)).isFalse();
      assertThat(EmailRuntime.config(qInstance)).isNotNull();
      assertThat(new EmailChannel(new AlertsQBitConfig(), ChannelType.EMAIL).validate(null, null)).isEmpty();
      assertThat(new NotificationEmailer(runtime).isConfigured()).isTrue();
   }



   /*******************************************************************************
    ** An alert email: one message, the alerts_alert template, NOTIFICATION, the
    ** recipient, an idempotency key without the address, the alert as the linked
    ** record, and the sender. Sending it again does not queue a second one.
    *******************************************************************************/
   @Test
   void testAlertChannelEmail() throws Exception
   {
      addProvider();
      EmailChannel channel = new EmailChannel(config, ChannelType.EMAIL);
      assertThat(channel.deliver(alertMessage(), null, "ops@example.com", null).outcome()).isEqualTo(DeliveryResult.Outcome.SENT);

      List<QRecord> messages = messages();
      assertThat(messages).hasSize(1);
      QRecord message = messages.get(0);
      assertThat(templateName(message)).isEqualTo("alerts_alert");
      assertThat(message.getValueString("category")).isEqualTo("NOTIFICATION");
      assertThat(message.getValueString("toAddresses")).isEqualTo("ops@example.com");
      assertThat(message.getValueString("subject")).isEqualTo("[ERROR] Export failed");
      assertThat(message.getValueString("idempotencyKey")).startsWith("alerts:alert:5:").doesNotContain("ops@example.com").contains(":RAISED:");
      assertThat(message.getValueString("linkedTableName")).isEqualTo("alerts_alert");
      assertThat(message.getValueString("linkedRecordId")).isEqualTo("5");
      assertThat(message.getValueString("senderQbit")).isEqualTo("qbit-alerts");
      assertThat(message.getValueString("senderProcess")).isEqualTo("alertsDeliverNotifications");
      assertThat(message.getValueString("htmlBody")).contains("Export failed").contains("3 records").contains("https://admin.example.com/app/alerts_alert/5");

      assertThat(channel.deliver(alertMessage(), null, "ops@example.com", null).outcome()).isEqualTo(DeliveryResult.Outcome.SENT);
      assertThat(messages()).hasSize(1);

      clock.advance(java.time.Duration.ofMinutes(10));
      assertThat(channel.deliver(alertMessage(), null, "ops@example.com", null).outcome()).isEqualTo(DeliveryResult.Outcome.SENT);
      assertThat(messages()).hasSize(2);
   }



   /*******************************************************************************
    ** A list channel sends one message per address.
    *******************************************************************************/
   @Test
   void testEmailListIsOneMessagePerAddress() throws Exception
   {
      addProvider();
      QRecord list = new QRecord().withValue("settings", "{\"addresses\": [\"ops@example.com\", \"dev@example.com\"]}");
      assertThat(new EmailChannel(config, ChannelType.EMAIL_LIST).deliver(alertMessage(), list, null, null).outcome()).isEqualTo(DeliveryResult.Outcome.SENT);

      List<QRecord> messages = messages();
      assertThat(messages).hasSize(2);
      assertThat(messages).extracting(m -> m.getValueString("toAddresses")).containsExactlyInAnyOrder("ops@example.com", "dev@example.com");
      assertThat(messages).extracting(m -> m.getValueString("idempotencyKey")).doesNotHaveDuplicates();
   }



   /*******************************************************************************
    ** No enabled provider: qbit-email refuses, and the channel fails (not retried)
    ** saying so.
    *******************************************************************************/
   @Test
   void testRefusedWithoutProvider() throws Exception
   {
      DeliveryResult result = new EmailChannel(config, ChannelType.EMAIL).deliver(alertMessage(), null, "ops@example.com", null);
      assertThat(result.outcome()).isEqualTo(DeliveryResult.Outcome.FAILED);
      assertThat(result.error()).startsWith("Email was refused");
      assertThat(messages()).isEmpty();
   }



   /*******************************************************************************
    ** An unsubscribed address is suppressed: the channel says so, nothing is sent.
    *******************************************************************************/
   @Test
   void testSuppressedAddress() throws Exception
   {
      addProvider();
      runtime.asSystem(() -> AlertsDb.insert(EmailTables.SUPPRESSION, new QRecord().withValue("address", "ops@example.com").withValue("scope", "ALL").withValue("reason", "HARD_BOUNCE")));
      DeliveryResult result = new EmailChannel(config, ChannelType.EMAIL).deliver(alertMessage(), null, "ops@example.com", null);
      assertThat(result.outcome()).isEqualTo(DeliveryResult.Outcome.FAILED);
      assertThat(result.error()).contains("suppressed");
   }



   /*******************************************************************************
    ** An immediate notification email: one message, alerts_notification, the
    ** notification row linked, one recipient; running again sends nothing more.
    ** SECURITY notices carry no body.
    *******************************************************************************/
   @Test
   void testImmediateNotification() throws Exception
   {
      addProvider();
      emailSettings("viewer-1", "JOB", "IMMEDIATE");
      emailSettings("viewer-2", "SECURITY", "IMMEDIATE");
      loginAs(ADMIN);
      QAlerts.notify(new NotificationSignal("Export failed").withBody("Disk full").withSeverity(NotificationSeverity.WARNING)
         .withCategory(NotificationCategory.JOB).withLink(AlertLink.toPath("/app/widget", "Widgets")).withAudience(NotificationAudience.users("viewer-1")));
      QAlerts.notify(new NotificationSignal("New sign-in").withBody("Secret detail").withSeverity(NotificationSeverity.WARNING)
         .withCategory(NotificationCategory.SECURITY).withAudience(NotificationAudience.users("viewer-2")));

      NotificationEmailer.Result result = new NotificationEmailer(runtime).run();
      assertThat(result.sent()).isEqualTo(2);

      List<QRecord> messages = messages();
      assertThat(messages).hasSize(2);
      QRecord job = messages.stream().filter(m -> "viewer-1@example.com".equals(m.getValueString("toAddresses"))).findFirst().orElseThrow();
      assertThat(templateName(job)).isEqualTo("alerts_notification");
      assertThat(job.getValueString("category")).isEqualTo("NOTIFICATION");
      assertThat(job.getValueString("subject")).isEqualTo("Export failed");
      assertThat(job.getValueString("idempotencyKey")).startsWith("alerts:notification:").doesNotContain("viewer-1@example.com");
      assertThat(job.getValueString("linkedTableName")).isEqualTo("alerts_notification");
      assertThat(job.getValueString("linkedRecordId")).isNotBlank();
      assertThat(job.getValueString("senderQbit")).isEqualTo("qbit-alerts");
      assertThat(job.getValueString("senderProcess")).isEqualTo("alertsDeliverNotifications");
      assertThat(job.getValueString("htmlBody")).contains("Disk full").contains("https://admin.example.com/app/widget").contains("My Notification Settings");
      QRecord security = messages.stream().filter(m -> "viewer-2@example.com".equals(m.getValueString("toAddresses"))).findFirst().orElseThrow();
      assertThat(security.getValueString("htmlBody")).doesNotContain("Secret detail");

      assertThat(new NotificationEmailer(runtime).run().sent()).isEqualTo(0);
      assertThat(messages()).hasSize(2);
   }



   /*******************************************************************************
    ** A notification with no enabled provider fails (the row says FAILED).
    *******************************************************************************/
   @Test
   void testNotificationRefused() throws Exception
   {
      emailSettings("viewer-1", "JOB", "IMMEDIATE");
      loginAs(ADMIN);
      QAlerts.notify(new NotificationSignal("Export failed").withSeverity(NotificationSeverity.WARNING).withCategory(NotificationCategory.JOB)
         .withAudience(NotificationAudience.users("viewer-1")));
      NotificationEmailer.Result refused = new NotificationEmailer(runtime).run();
      assertThat(refused.failed()).isEqualTo(1);
      assertThat(refused.sent()).isEqualTo(0);
   }



   /*******************************************************************************
    ** A notification to an address that has been suppressed is skipped.
    *******************************************************************************/
   @Test
   void testNotificationSuppressed() throws Exception
   {
      addProvider();
      emailSettings("viewer-1", "JOB", "IMMEDIATE");
      loginAs(ADMIN);
      QAlerts.notify(new NotificationSignal("Export failed").withSeverity(NotificationSeverity.WARNING).withCategory(NotificationCategory.JOB)
         .withAudience(NotificationAudience.users("viewer-1")));
      runtime.asSystem(() -> AlertsDb.insert(EmailTables.SUPPRESSION, new QRecord().withValue("address", "viewer-1@example.com").withValue("scope", "ALL").withValue("reason", "HARD_BOUNCE")));
      NotificationEmailer.Result suppressed = new NotificationEmailer(runtime).run();
      assertThat(suppressed.skipped()).isEqualTo(1);
      assertThat(suppressed.sent()).isEqualTo(0);
   }



   /*******************************************************************************
    ** The daily digest: one message to the user, alerts_digest, keyed by user and
    ** window start, listing the unread items.
    *******************************************************************************/
   @Test
   void testDigest() throws Exception
   {
      addProvider();
      clock.set(Instant.parse("2026-10-12T12:05:00Z"));
      emailSettings("viewer-1", "RELEASE", "DAILY_DIGEST");
      loginAs(ADMIN);
      QAlerts.notify(new NotificationSignal("Version 2.5 is out").withBody("Dark mode").withCategory(NotificationCategory.RELEASE)
         .withAudience(NotificationAudience.user("viewer-1")));
      QAlerts.notify(new NotificationSignal("Version 2.6 is out").withCategory(NotificationCategory.RELEASE).withAudience(NotificationAudience.user("viewer-1")));

      assertThat(new NotificationEmailer(runtime).run().digests()).isEqualTo(1);
      List<QRecord> messages = messages();
      assertThat(messages).hasSize(1);
      QRecord message = messages.get(0);
      assertThat(templateName(message)).isEqualTo("alerts_digest");
      assertThat(message.getValueString("category")).isEqualTo("NOTIFICATION");
      assertThat(message.getValueString("toAddresses")).isEqualTo("viewer-1@example.com");
      assertThat(message.getValueString("subject")).isEqualTo("2 new notifications");
      assertThat(message.getValueString("idempotencyKey")).isEqualTo("alerts:digest:viewer-1:2026-10-12T12:00:00Z");
      assertThat(message.getValueString("htmlBody")).contains("Version 2.5 is out").contains("Dark mode").contains("Version 2.6 is out");
      assertThat(message.getValueString("linkedTableName")).isEqualTo("alerts_notification");
      assertThat(new NotificationEmailer(runtime).run().digests()).isEqualTo(0);
   }



   /*******************************************************************************
    ** Recipient hashes are stable, case-insensitive and hide the address.
    *******************************************************************************/
   @Test
   void testHash()
   {
      assertThat(AlertsEmailSender.hash("Ops@Example.com ")).isEqualTo(AlertsEmailSender.hash("ops@example.com")).hasSize(16).doesNotContain("ops");
      assertThat(AlertsEmailSender.hash("a@example.com")).isNotEqualTo(AlertsEmailSender.hash("b@example.com"));
   }
}
