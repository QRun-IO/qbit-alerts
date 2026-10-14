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


import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import com.kingsrook.qbits.alerts.delivery.channels.ExternalChannels;
import com.kingsrook.qqq.backend.core.instances.QMetaDataVariableInterpreter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.webhooks.WebhookSecret;
import com.kingsrook.qqq.backend.core.utils.webhooks.WebhookVerifier;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The external channels against a local HTTP receiver: request shapes
 ** (Slack Block Kit, Teams Adaptive Card, signed webhook, PagerDuty Events v2,
 ** Alertmanager v2), outcome classification, https enforcement and secrets.
 *******************************************************************************/
class ExternalChannelsTest
{
   private HttpServer            server;
   private String                base;
   private final List<Received>  received = new ArrayList<>();
   private final AtomicInteger   status   = new AtomicInteger(200);
   private AlertSecretResolver   secrets;



   /*******************************************************************************
    ** One captured request.
    *******************************************************************************/
   record Received(String path, Map<String, List<String>> headers, String body)
   {
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @BeforeEach
   void start() throws IOException
   {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", exchange ->
      {
         String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
         received.add(new Received(exchange.getRequestURI().getPath(), exchange.getRequestHeaders(), body));
         byte[] reply = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
         exchange.sendResponseHeaders(status.get(), reply.length);
         try(OutputStream out = exchange.getResponseBody())
         {
            out.write(reply);
         }
      });
      server.start();
      base = "http://127.0.0.1:" + server.getAddress().getPort();

      QMetaDataVariableInterpreter interpreter = new QMetaDataVariableInterpreter();
      interpreter.addValueMap("test", Map.of("URL", base + "/hook", "KEY", "whsec_c2VjcmV0LWtleS1mb3ItdGVzdHM=", "ROUTING", "R0UT1NG", "BEARER", "am-token"));
      secrets = new AlertSecretResolver(interpreter);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @AfterEach
   void stop()
   {
      server.stop(0);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static QRecord channel(String type, String secretRef, Map<String, Object> settings)
   {
      return (new QRecord().withValue("channelType", type).withValue("secretRef", secretRef).withValue("settings", JsonUtils.toJson(settings)));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static AlertMessage message(String kind, String status)
   {
      AlertMessage.Entry entry = new AlertMessage.Entry(7, "cdn.purge.dead", "CDN purge failed", "provider:cf", "ERROR", status, "3 CDN purges failed", "Retry later.", "CDN", 3,
         "https://admin.example.com/app/alerts_alert/7", "2026-10-12T10:00:00Z", "RESOLVED".equals(status) ? "2026-10-12T11:00:00Z" : null);
      return (new AlertMessage("[ERROR] 3 CDN purges failed", "[ERROR] CDN purge failed / CDN: 3 CDN purges failed (2 h)\n<b>x</b>", "<p>x</p>", "ERROR", kind, List.of(7),
         "https://admin.example.com/app/alerts_alert/7", List.of(entry)));
   }



   /*******************************************************************************
    ** Slack and Teams post to the secret URL.
    *******************************************************************************/
   @Test
   void testSlackAndTeams() throws Exception
   {
      ExternalChannels.Slack slack = new ExternalChannels.Slack();
      assertThat(slack.validate(channel("SLACK", null, Map.of()), secrets)).hasSize(1);
      QRecord slackChannel = channel("SLACK", "${test.URL}", Map.of("allowInsecureHttp", true));
      assertThat(slack.validate(slackChannel, secrets)).isEmpty();
      assertThat(slack.deliver(message("RAISED", "OPEN"), slackChannel, null, secrets).outcome()).isEqualTo(DeliveryResult.Outcome.SENT);
      Map<?, ?> slackBody = JsonUtils.toObject(received.get(0).body(), Map.class);
      assertThat(slackBody.get("text")).isEqualTo("[ERROR] 3 CDN purges failed");
      assertThat(received.get(0).body()).contains("&lt;b&gt;x&lt;/b&gt;").contains("Open in Alerts");

      ExternalChannels.Teams teams = new ExternalChannels.Teams();
      QRecord teamsChannel = channel("TEAMS", "${test.URL}", Map.of("allowInsecureHttp", true));
      assertThat(teams.validate(channel("TEAMS", null, Map.of()), secrets)).hasSize(1);
      assertThat(teams.deliver(message("RAISED", "OPEN"), teamsChannel, null, secrets).outcome()).isEqualTo(DeliveryResult.Outcome.SENT);
      assertThat(received.get(1).body()).contains("application/vnd.microsoft.card.adaptive").contains("AdaptiveCard").contains("Action.OpenUrl");

      //////////////////////////////////////////////
      // https only, unless explicitly allowed     //
      //////////////////////////////////////////////
      DeliveryResult insecure = slack.deliver(message("RAISED", "OPEN"), channel("SLACK", "${test.URL}", Map.of()), null, secrets);
      assertThat(insecure.outcome()).isEqualTo(DeliveryResult.Outcome.FAILED);
      assertThat(insecure.error()).contains("https").doesNotContain(base);
   }



   /*******************************************************************************
    ** The webhook is signed with the shared Standard Webhooks format.
    *******************************************************************************/
   @Test
   void testSignedWebhook() throws Exception
   {
      ExternalChannels.Webhook webhook = new ExternalChannels.Webhook();
      assertThat(webhook.validate(channel("WEBHOOK", null, Map.of()), secrets)).hasSize(2);
      assertThat(webhook.deliver(message("RAISED", "OPEN"), channel("WEBHOOK", null, Map.of()), null, secrets).outcome()).isEqualTo(DeliveryResult.Outcome.FAILED);

      QRecord channel = channel("WEBHOOK", "${test.KEY}", Map.of("url", base + "/receiver", "allowInsecureHttp", true));
      assertThat(webhook.deliver(message("RESOLVED", "RESOLVED"), channel, null, secrets).outcome()).isEqualTo(DeliveryResult.Outcome.SENT);
      Received request = received.get(0);
      assertThat(request.path()).isEqualTo("/receiver");
      String id        = request.headers().get("Webhook-id").get(0);
      String timestamp = request.headers().get("Webhook-timestamp").get(0);
      String signature = request.headers().get("Webhook-signature").get(0);
      assertThat(id).startsWith("msg_");
      new WebhookVerifier(List.of(WebhookSecret.parse("whsec_c2VjcmV0LWtleS1mb3ItdGVzdHM=")))
         .verify(Map.of("webhook-id", id, "webhook-timestamp", timestamp, "webhook-signature", signature), request.body().getBytes(StandardCharsets.UTF_8));
      assertThat(request.body()).contains("\"type\":\"qqq.alert.resolved\"").contains("\"dedupKey\":\"provider:cf\"");

      ////////////////////////////////////////////////////
      // the id is stable across retries of one message //
      ////////////////////////////////////////////////////
      webhook.deliver(message("RESOLVED", "RESOLVED"), channel, null, secrets);
      assertThat(received.get(1).headers().get("Webhook-id").get(0)).isEqualTo(id);
   }



   /*******************************************************************************
    ** PagerDuty: trigger / acknowledge / resolve with a stable dedup key.
    *******************************************************************************/
   @Test
   void testPagerDuty() throws Exception
   {
      ExternalChannels.PagerDuty pagerDuty = new ExternalChannels.PagerDuty();
      assertThat(pagerDuty.validate(channel("PAGERDUTY", null, Map.of()), secrets)).hasSize(1);
      assertThat(pagerDuty.deliver(message("RAISED", "OPEN"), channel("PAGERDUTY", null, Map.of()), null, secrets).outcome()).isEqualTo(DeliveryResult.Outcome.FAILED);
      QRecord channel = channel("PAGERDUTY", "${test.ROUTING}", Map.of("url", base + "/v2/enqueue", "allowInsecureHttp", true, "source", "kof22"));

      pagerDuty.deliver(message("RAISED", "OPEN"), channel, null, secrets);
      pagerDuty.deliver(message("RAISED", "ACKNOWLEDGED"), channel, null, secrets);
      pagerDuty.deliver(message("RESOLVED", "RESOLVED"), channel, null, secrets);
      List<Map<?, ?>> events = new ArrayList<>();
      for(Received request : received)
      {
         events.add(JsonUtils.toObject(request.body(), Map.class));
      }
      assertThat(events.stream().map(event -> String.valueOf(event.get("event_action"))).toList()).containsExactly("trigger", "acknowledge", "resolve");
      assertThat(events).allMatch(event -> "cdn.purge.dead:provider:cf".equals(event.get("dedup_key")) && "R0UT1NG".equals(event.get("routing_key")));
      Map<?, ?> payload = (Map<?, ?>) events.get(0).get("payload");
      assertThat(payload.get("severity")).isEqualTo("error");
      assertThat(payload.get("source")).isEqualTo("kof22");
   }



   /*******************************************************************************
    ** Alertmanager sink: v2 alerts with the qqq labels and the origin guard.
    *******************************************************************************/
   @Test
   void testAlertmanagerSink() throws Exception
   {
      ExternalChannels.Alertmanager alertmanager = new ExternalChannels.Alertmanager();
      assertThat(alertmanager.validate(channel("ALERTMANAGER", null, Map.of()), secrets)).hasSize(1);
      assertThat(alertmanager.deliver(message("RAISED", "OPEN"), channel("ALERTMANAGER", null, Map.of()), null, secrets).outcome()).isEqualTo(DeliveryResult.Outcome.FAILED);
      QRecord channel = channel("ALERTMANAGER", "${test.BEARER}", Map.of("url", base + "/", "allowInsecureHttp", true));

      alertmanager.deliver(message("RAISED", "OPEN"), channel, null, secrets);
      alertmanager.deliver(message("RESOLVED", "RESOLVED"), channel, null, secrets);
      assertThat(received.get(0).path()).isEqualTo("/api/v2/alerts");
      assertThat(received.get(0).headers().get("Authorization").get(0)).isEqualTo("Bearer am-token");
      List<?>   firing = JsonUtils.toObject(received.get(0).body(), List.class);
      Map<?, ?> labels = (Map<?, ?>) ((Map<?, ?>) firing.get(0)).get("labels");
      assertThat(labels.get("qqq_origin")).isEqualTo("qbit-alerts");
      assertThat(labels.get("qqq_type")).isEqualTo("cdn.purge.dead");
      assertThat(labels.get("severity")).isEqualTo("error");
      List<?> resolvedBody = JsonUtils.toObject(received.get(1).body(), List.class);
      assertThat(((Map<?, ?>) resolvedBody.get(0)).get("endsAt")).isEqualTo("2026-10-12T11:00:00Z");

      ////////////////////////////////////////////////////
      // a test message is one sample alert, already over //
      ////////////////////////////////////////////////////
      alertmanager.deliver(new AlertMessage("TEST: hello", "t", "h", "INFO", "TEST", List.of(), "https://x/app/alerts_alert"), channel, null, secrets);
      assertThat(received.get(2).body()).contains("alerts.test");
   }



   /*******************************************************************************
    ** Outcomes: 5xx and 429 retry; other 4xx fail permanently; no receiver
    ** retries.
    *******************************************************************************/
   @Test
   void testOutcomes()
   {
      ExternalChannels.Slack slack   = new ExternalChannels.Slack();
      QRecord                channel = channel("SLACK", "${test.URL}", Map.of("allowInsecureHttp", true));
      status.set(503);
      assertThat(slack.deliver(message("RAISED", "OPEN"), channel, null, secrets).outcome()).isEqualTo(DeliveryResult.Outcome.RETRY);
      status.set(429);
      assertThat(slack.deliver(message("RAISED", "OPEN"), channel, null, secrets).outcome()).isEqualTo(DeliveryResult.Outcome.RETRY);
      status.set(404);
      DeliveryResult notFound = slack.deliver(message("RAISED", "OPEN"), channel, null, secrets);
      assertThat(notFound.outcome()).isEqualTo(DeliveryResult.Outcome.FAILED);
      assertThat(notFound.error()).startsWith("HTTP 404");

      ExternalChannels.PagerDuty pagerDuty = new ExternalChannels.PagerDuty();
      assertThat(pagerDuty.deliver(message("RAISED", "OPEN"), channel("PAGERDUTY", "${test.ROUTING}", Map.of("url", base, "allowInsecureHttp", true)), null, secrets).outcome())
         .isEqualTo(DeliveryResult.Outcome.FAILED);

      server.stop(0);
      assertThat(slack.deliver(message("RAISED", "OPEN"), channel, null, secrets).outcome()).isEqualTo(DeliveryResult.Outcome.RETRY);
      assertThat(ExternalChannels.all()).hasSize(5);
   }
}
