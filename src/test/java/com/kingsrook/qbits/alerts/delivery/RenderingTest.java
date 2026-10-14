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

package com.kingsrook.qbits.alerts.delivery;


import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.alerts.AlertsQBitConfig;
import com.kingsrook.qbits.alerts.engine.AlertRouter;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.ChannelType;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.DeliveryKind;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues;
import com.kingsrook.qqq.backend.core.alerts.DetailLevel;
import com.kingsrook.qqq.backend.core.instances.QMetaDataVariableInterpreter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Redaction corpus, detail levels, secret references and labels: nothing
 ** personal or secret reaches an external message.
 *******************************************************************************/
class RenderingTest
{
   static final List<String> CORPUS = List.of(
      "jane.doe@example.com",
      "+1 (555) 123-4567",
      "555-123-4567",
      "10.20.30.40",
      "fe80::1ff:fe23:4567:890a",
      "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0In0.abc123def456",
      "Bearer abcdefghijklmnop.qrstuv",
      "password=hunter2",
      "api_key: AKIA1234567890",
      "token=s3cr3t",
      "https://admin:pa55word@db.example.com/x",
      "0123456789abcdef0123456789abcdef01",
      "c2VjcmV0LXZhbHVlLXRoYXQtaXMtbG9uZy1lbm91Z2g=");



   /*******************************************************************************
    ** Every corpus string is masked; ordinary text survives.
    *******************************************************************************/
   @Test
   void testRedactor()
   {
      for(String sensitive : CORPUS)
      {
         String redacted = AlertRedactor.redact("Problem with " + sensitive + " today");
         assertThat(redacted).as(sensitive).contains(AlertRedactor.MASK);
         assertThat(redacted).as(sensitive).doesNotContain(sensitive);
      }
      assertThat(AlertRedactor.redact("3 CDN purges failed on 2026-10-12 after 5 retries")).isEqualTo("3 CDN purges failed on 2026-10-12 after 5 retries");
      assertThat(AlertRedactor.redact(null)).isNull();
      assertThat(AlertRedactor.redact("")).isEmpty();
   }



   /*******************************************************************************
    ** Detail levels: MINIMAL has no summary or details; STANDARD adds the
    ** summary and counts; FULL adds details. All of it is redacted.
    *******************************************************************************/
   @Test
   void testDetailLevels()
   {
      Instant now   = Instant.parse("2026-10-12T12:00:00Z");
      QRecord alert = new QRecord().withValue("id", 7).withValue("title", "Backups failed for ops@example.com").withValue("severity", "ERROR")
         .withValue("summary", "The nightly job stopped.").withValue("details", "password=hunter2 in the log <script>x</script>").withValue("itemCount", 3)
         .withValue("firstRaisedAt", now.minusSeconds(3 * 3600));

      AlertMessage minimal = AlertMessageRenderer.render(List.of(new AlertMessageRenderer.Item(alert, "Backup failed", "Backups", DetailLevel.MINIMAL)), DeliveryKind.RAISED, "https://x.example/", now);
      assertThat(minimal.subject()).isEqualTo("[ERROR] Backups failed for [redacted]");
      assertThat(minimal.text()).contains("3 h").doesNotContain("nightly job").doesNotContain("hunter2");
      assertThat(minimal.link()).isEqualTo("https://x.example/app/alerts_alert/7");

      AlertMessage standard = AlertMessageRenderer.render(List.of(new AlertMessageRenderer.Item(alert, "Backup failed", "Backups", DetailLevel.STANDARD)), DeliveryKind.RAISED, null, now);
      assertThat(standard.text()).contains("nightly job").contains("3 records").doesNotContain("hunter2");

      AlertMessage full = AlertMessageRenderer.render(List.of(new AlertMessageRenderer.Item(alert, "Backup failed", "Backups", DetailLevel.FULL)), DeliveryKind.RESOLVED, null, now);
      assertThat(full.subject()).startsWith("[RESOLVED]");
      assertThat(full.text()).contains("[redacted] in the log").doesNotContain("hunter2");
      assertThat(full.html()).contains("&lt;").doesNotContain("<script");

      AlertMessage test = AlertMessageRenderer.render(List.of(new AlertMessageRenderer.Item(alert, "Backup failed", null, DetailLevel.MINIMAL),
         new AlertMessageRenderer.Item(alert, "Backup failed", "Backups", DetailLevel.MINIMAL)), DeliveryKind.TEST, null, now);
      assertThat(test.subject()).isEqualTo("TEST: 2 new alerts");
      assertThat(DetailLevel.lowerOf(DetailLevel.FULL, DetailLevel.MINIMAL)).isEqualTo(DetailLevel.MINIMAL);
      assertThat(AlertMessageRenderer.age(null, now)).isEqualTo("now");
      assertThat(AlertMessageRenderer.age(now.minusSeconds(3 * 86400), now)).isEqualTo("3 d");
      assertThat(AlertMessageRenderer.label(Map.of(1, "One"), 1)).isEqualTo("One");
   }



   /*******************************************************************************
    ** Secrets are references: resolved at send time, never stored.
    *******************************************************************************/
   @Test
   void testSecrets()
   {
      QMetaDataVariableInterpreter interpreter = new QMetaDataVariableInterpreter();
      interpreter.addValueMap("test", Map.of("ALERTS_TEST_SECRET", "s3cret"));
      AlertSecretResolver secrets = new AlertSecretResolver(interpreter);
      assertThat(secrets.resolve("${test.ALERTS_TEST_SECRET}")).isEqualTo("s3cret");
      assertThat(secrets.resolve("ALERTS_TEST_MISSING")).isNull();
      assertThat(secrets.resolve(" ")).isNull();
      assertThat(secrets.describe("${test.ALERTS_TEST_SECRET}")).isEqualTo("present");
      assertThat(secrets.describe("ALERTS_TEST_MISSING")).isEqualTo("missing");
      assertThat(secrets.describe(null)).isEqualTo("none");
      assertThat(new AlertSecretResolver().resolve(null)).isNull();
   }



   /*******************************************************************************
    ** Email list settings, channel validation, masking and labels.
    *******************************************************************************/
   @Test
   void testEmailAndLabels()
   {
      QRecord list = new QRecord().withValue("settings", "{\"addresses\": [\"ops@example.com\", \" \", \"dev@example.com\"]}");
      assertThat(EmailChannel.listAddresses(list)).containsExactly("ops@example.com", "dev@example.com");
      assertThat(EmailChannel.listAddresses(new QRecord().withValue("settings", "not json"))).isEmpty();
      assertThat(EmailChannel.listAddresses(null)).isEmpty();

      EmailChannel channel = new EmailChannel(new AlertsQBitConfig(), ChannelType.EMAIL_LIST);
      assertThat(channel.getType()).isEqualTo("EMAIL_LIST");
      assertThat(channel.validate(new QRecord(), new AlertSecretResolver())).hasSize(3);
      assertThat(channel.deliver(null, new QRecord(), null, new AlertSecretResolver()).outcome()).isEqualTo(DeliveryResult.Outcome.FAILED);

      EmailChannel configured = new EmailChannel(new AlertsQBitConfig().withMessagingProviderName("smtp").withFromAddress("alerts@example.com"), ChannelType.EMAIL);
      assertThat(configured.validate(new QRecord(), new AlertSecretResolver())).isEmpty();
      assertThat(configured.deliver(null, new QRecord(), null, new AlertSecretResolver()).error()).contains("No email address");
      assertThat(configured.deliver(new AlertMessage("s", "t", "h", "ERROR", "RAISED", List.of(1), "l"), new QRecord(), "a@example.com", new AlertSecretResolver()).outcome())
         .isEqualTo(DeliveryResult.Outcome.RETRY);

      assertThat(new InAppChannel().validate(null, null)).isEmpty();
      assertThat(new InAppChannel().deliver(null, null, null, null).outcome()).isEqualTo(DeliveryResult.Outcome.SENT);
      assertThat(AlertRouter.maskEmail("jane@example.com")).isEqualTo("j***@example.com");
      assertThat(AlertRouter.maskEmail("nope")).isEqualTo("***");
      assertThat(AlertRouter.maskEmail(null)).isNull();
      assertThat(AlertsPossibleValues.label("IDP_GROUP")).isEqualTo("IdP Group");
      assertThat(AlertsPossibleValues.label("IN_APP")).isEqualTo("In App");
      assertThat(AlertsPossibleValues.label(null)).isNull();
      assertThat(AlertsPossibleValues.buildEnumSources()).hasSize(25);
   }
}
