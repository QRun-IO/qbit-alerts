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

package com.kingsrook.qbits.alerts.delivery.channels;


import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import com.kingsrook.qbits.alerts.delivery.AlertChannelInterface;
import com.kingsrook.qbits.alerts.delivery.AlertMessage;
import com.kingsrook.qbits.alerts.delivery.AlertSecretResolver;
import com.kingsrook.qbits.alerts.delivery.DeliveryResult;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.ChannelType;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** The optional external channels (design section 5.6). None is required;
 ** each is an admin-configured alerts_channel row whose secret is a
 ** reference (secretRef), resolved at send time:
 ** - SLACK: an Incoming Webhook URL (the URL is the secret); Block Kit body.
 ** - TEAMS: a Teams Workflows "when a webhook request is received" URL (the
 **   secret); an Adaptive Card. (Office 365 connectors are retired.)
 ** - WEBHOOK: settings {"url": ...}; the secret is the HMAC signing key; a
 **   documented JSON body signed with the Standard Webhooks headers (the
 **   shared format of QRun-IO/qqq#1105, see StandardWebhookSigner).
 ** - PAGERDUTY: the Events API v2 routing key (the secret); one
 **   trigger / acknowledge / resolve event per alert, dedup_key = type:key.
 ** - ALERTMANAGER: settings {"url": "https://alertmanager:9093"} (optional
 **   bearer secret); POST /api/v2/alerts with the qqq_* labels and
 **   qqq_origin="qbit-alerts" (the ingest loop guard); endsAt on resolve.
 ** No vendor SDKs: plain java.net.http and JSON.
 *******************************************************************************/
public final class ExternalChannels
{
   public static final String ORIGIN_LABEL = "qqq_origin";
   public static final String ORIGIN_VALUE = "qbit-alerts";

   static final String PAGERDUTY_URL = "https://events.pagerduty.com/v2/enqueue";



   /*******************************************************************************
    ** Channels only.
    *******************************************************************************/
   private ExternalChannels()
   {
   }



   /*******************************************************************************
    ** All five, for registration.
    *******************************************************************************/
   public static List<AlertChannelInterface> all()
   {
      return (List.of(new Slack(), new Teams(), new Webhook(), new PagerDuty(), new Alertmanager()));
   }



   /*******************************************************************************
    ** Slack Incoming Webhook.
    *******************************************************************************/
   public static class Slack implements AlertChannelInterface
   {
      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public String getType()
      {
         return (ChannelType.SLACK.name());
      }



      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public List<String> validate(QRecord channel, AlertSecretResolver secrets)
      {
         return (secrets.resolve(channel.getValueString("secretRef")) == null ? List.of("Set the secret reference to the Slack Incoming Webhook URL.") : List.of());
      }



      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public DeliveryResult deliver(AlertMessage message, QRecord channel, String recipientAddress, AlertSecretResolver secrets)
      {
         Map<String, Object> body = new LinkedHashMap<>();
         body.put("text", message.subject());
         body.put("blocks", List.of(
            Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", "*" + slackEscape(message.subject()) + "*\n" + slackEscape(truncate(message.text(), 2800)))),
            Map.of("type", "actions", "elements", List.of(Map.of("type", "button", "text", Map.of("type", "plain_text", "text", "Open in Alerts"), "url", message.link())))));
         return (HttpChannelSupport.postJson(secrets.resolve(channel.getValueString("secretRef")), null, HttpChannelSupport.json(body), HttpChannelSupport.allowInsecure(channel)));
      }
   }



   /*******************************************************************************
    ** Microsoft Teams through a Workflows webhook.
    *******************************************************************************/
   public static class Teams implements AlertChannelInterface
   {
      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public String getType()
      {
         return (ChannelType.TEAMS.name());
      }



      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public List<String> validate(QRecord channel, AlertSecretResolver secrets)
      {
         return (secrets.resolve(channel.getValueString("secretRef")) == null ? List.of("Set the secret reference to the Teams Workflows webhook URL.") : List.of());
      }



      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public DeliveryResult deliver(AlertMessage message, QRecord channel, String recipientAddress, AlertSecretResolver secrets)
      {
         Map<String, Object> card = new LinkedHashMap<>();
         card.put("$schema", "http://adaptivecards.io/schemas/adaptive-card.json");
         card.put("type", "AdaptiveCard");
         card.put("version", "1.4");
         card.put("body", List.of(
            Map.of("type", "TextBlock", "text", message.subject(), "weight", "Bolder", "wrap", true),
            Map.of("type", "TextBlock", "text", truncate(message.text(), 2800), "wrap", true)));
         card.put("actions", List.of(Map.of("type", "Action.OpenUrl", "title", "Open in Alerts", "url", message.link())));
         Map<String, Object> body = Map.of("type", "message", "attachments", List.of(Map.of("contentType", "application/vnd.microsoft.card.adaptive", "content", card)));
         return (HttpChannelSupport.postJson(secrets.resolve(channel.getValueString("secretRef")), null, HttpChannelSupport.json(body), HttpChannelSupport.allowInsecure(channel)));
      }
   }



   /*******************************************************************************
    ** Generic signed webhook.
    *******************************************************************************/
   public static class Webhook implements AlertChannelInterface
   {
      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public String getType()
      {
         return (ChannelType.WEBHOOK.name());
      }



      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public List<String> validate(QRecord channel, AlertSecretResolver secrets)
      {
         List<String> problems = new ArrayList<>();
         if(!StringUtils.hasContent(Objects.toString(HttpChannelSupport.settings(channel).get("url"), null)))
         {
            problems.add("Add the receiver's URL to the channel's settings: {\"url\": \"https://...\"}.");
         }
         if(secrets.resolve(channel.getValueString("secretRef")) == null)
         {
            problems.add("Set the secret reference to the signing key (a whsec_ secret).");
         }
         return (problems);
      }



      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public DeliveryResult deliver(AlertMessage message, QRecord channel, String recipientAddress, AlertSecretResolver secrets)
      {
         List<String> problems = validate(channel, secrets);
         if(!problems.isEmpty())
         {
            return (DeliveryResult.failed(problems.get(0)));
         }
         Map<String, Object> body = new LinkedHashMap<>();
         body.put("type", "qqq.alert." + message.kind().toLowerCase(Locale.ROOT));
         body.put("subject", message.subject());
         body.put("severity", message.severity());
         body.put("link", message.link());
         body.put("alerts", message.entries());
         byte[] bytes = HttpChannelSupport.json(body);
         Map<String, String> headers = StandardWebhookSigner.headers(messageId(message), Instant.now(), bytes, secrets.resolve(channel.getValueString("secretRef")));
         return (HttpChannelSupport.postJson(Objects.toString(HttpChannelSupport.settings(channel).get("url")), headers, bytes, HttpChannelSupport.allowInsecure(channel)));
      }
   }



   /*******************************************************************************
    ** PagerDuty Events API v2.
    *******************************************************************************/
   public static class PagerDuty implements AlertChannelInterface
   {
      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public String getType()
      {
         return (ChannelType.PAGERDUTY.name());
      }



      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public List<String> validate(QRecord channel, AlertSecretResolver secrets)
      {
         return (secrets.resolve(channel.getValueString("secretRef")) == null ? List.of("Set the secret reference to the PagerDuty Events API v2 routing key.") : List.of());
      }



      /***************************************************************************
       ** One event per alert; the first failure decides the outcome.
       ***************************************************************************/
      @Override
      public DeliveryResult deliver(AlertMessage message, QRecord channel, String recipientAddress, AlertSecretResolver secrets)
      {
         String routingKey = secrets.resolve(channel.getValueString("secretRef"));
         if(routingKey == null)
         {
            return (DeliveryResult.failed(validate(channel, secrets).get(0)));
         }
         Map<String, Serializable> settings = HttpChannelSupport.settings(channel);
         String                    url      = Objects.toString(settings.get("url"), PAGERDUTY_URL);
         String                    source   = Objects.toString(settings.get("source"), ORIGIN_VALUE);
         for(AlertMessage.Entry entry : entries(message))
         {
            String action = "TEST".equals(message.kind()) ? "trigger" : switch(entry.status())
            {
               case "RESOLVED" -> "resolve";
               case "ACKNOWLEDGED" -> "acknowledge";
               default -> "trigger";
            };
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("routing_key", routingKey);
            event.put("event_action", action);
            event.put("dedup_key", entry.stableKey());
            if("trigger".equals(action))
            {
               Map<String, Object> payload = new LinkedHashMap<>();
               payload.put("summary", truncate(entry.title(), 1024));
               payload.put("source", source);
               payload.put("severity", entry.severity().toLowerCase(Locale.ROOT));
               payload.put("component", entry.typeLabel());
               payload.put("group", entry.group());
               Map<String, Object> details = new LinkedHashMap<>();
               details.put("summary", entry.summary());
               details.put("records", entry.itemCount());
               details.put("link", entry.link());
               details.values().removeIf(Objects::isNull);
               payload.put("custom_details", details);
               payload.values().removeIf(Objects::isNull);
               event.put("payload", payload);
               event.put("links", List.of(Map.of("href", entry.link(), "text", "Open in Alerts")));
            }
            DeliveryResult result = HttpChannelSupport.postJson(url, null, HttpChannelSupport.json(event), HttpChannelSupport.allowInsecure(channel));
            if(result.outcome() != DeliveryResult.Outcome.SENT)
            {
               return (result);
            }
         }
         return (DeliveryResult.sent(null));
      }
   }



   /*******************************************************************************
    ** Prometheus Alertmanager as a sink.
    *******************************************************************************/
   public static class Alertmanager implements AlertChannelInterface
   {
      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public String getType()
      {
         return (ChannelType.ALERTMANAGER.name());
      }



      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public List<String> validate(QRecord channel, AlertSecretResolver secrets)
      {
         return (StringUtils.hasContent(Objects.toString(HttpChannelSupport.settings(channel).get("url"), null)) ? List.of()
            : List.of("Add Alertmanager's base URL to the channel's settings: {\"url\": \"https://alertmanager:9093\"}."));
      }



      /***************************************************************************
       ** One POST with every alert. Open alerts get an endsAt a week out, so
       ** they stay active without a re-post; a resolve sends endsAt = when.
       ***************************************************************************/
      @Override
      public DeliveryResult deliver(AlertMessage message, QRecord channel, String recipientAddress, AlertSecretResolver secrets)
      {
         List<String> problems = validate(channel, secrets);
         if(!problems.isEmpty())
         {
            return (DeliveryResult.failed(problems.get(0)));
         }
         List<Map<String, Object>> alerts = new ArrayList<>();
         for(AlertMessage.Entry entry : entries(message))
         {
            Map<String, String> labels = new LinkedHashMap<>();
            labels.put("alertname", entry.alertTypeName() == null ? "alerts.test" : entry.alertTypeName());
            labels.put("severity", entry.severity().toLowerCase(Locale.ROOT));
            labels.put("qqq_type", Objects.toString(entry.alertTypeName(), "alerts.test"));
            labels.put("qqq_group", Objects.toString(entry.group(), ""));
            labels.put("qqq_key", Objects.toString(entry.dedupKey(), ""));
            labels.put(ORIGIN_LABEL, ORIGIN_VALUE);
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("labels", labels);
            alert.put("annotations", Map.of("summary", entry.title(), "qqq_url", entry.link()));
            alert.put("generatorURL", entry.link());
            if(entry.firstRaisedAt() != null)
            {
               alert.put("startsAt", entry.firstRaisedAt());
            }
            boolean resolved = "RESOLVED".equals(entry.status()) || "TEST".equals(message.kind());
            alert.put("endsAt", resolved ? Objects.toString(entry.resolvedAt(), Instant.now().toString()) : Instant.now().plus(7, ChronoUnit.DAYS).toString());
            alerts.add(alert);
         }
         String              bearer  = secrets.resolve(channel.getValueString("secretRef"));
         Map<String, String> headers = bearer == null ? null : Map.of("Authorization", "Bearer " + bearer);
         String              base    = Objects.toString(HttpChannelSupport.settings(channel).get("url")).replaceAll("/+$", "");
         return (HttpChannelSupport.postJson(base + "/api/v2/alerts", headers, HttpChannelSupport.json(alerts), HttpChannelSupport.allowInsecure(channel)));
      }
   }



   /*******************************************************************************
    ** The message's entries (a TEST message gets one stand-in entry).
    *******************************************************************************/
   static List<AlertMessage.Entry> entries(AlertMessage message)
   {
      if(message.entries() != null && !message.entries().isEmpty())
      {
         return (message.entries());
      }
      return (List.of(new AlertMessage.Entry(null, "alerts.test", "Test", "test", "INFO", "OPEN", message.subject(), null, null, null, message.link(), null, null)));
   }



   /*******************************************************************************
    ** A stable id for a webhook delivery (the same on every retry): a hash of
    ** the kind and each alert's key and state.
    *******************************************************************************/
   static String messageId(AlertMessage message)
   {
      StringBuilder content = new StringBuilder(message.kind());
      for(AlertMessage.Entry entry : entries(message))
      {
         content.append('|').append(entry.alertId()).append(':').append(entry.status()).append(':').append(entry.firstRaisedAt()).append(':').append(entry.resolvedAt());
      }
      try
      {
         byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.toString().getBytes(StandardCharsets.UTF_8));
         return ("msg_" + HexFormat.of().formatHex(digest).substring(0, 32));
      }
      catch(Exception e)
      {
         return ("msg_" + Integer.toHexString(content.toString().hashCode()));
      }
   }



   /*******************************************************************************
    ** Slack mrkdwn escaping (&, &lt;, &gt;).
    *******************************************************************************/
   static String slackEscape(String text)
   {
      return (text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static String truncate(String text, int max)
   {
      return (text == null || text.length() <= max ? text : text.substring(0, max - 1) + "…");
   }
}
