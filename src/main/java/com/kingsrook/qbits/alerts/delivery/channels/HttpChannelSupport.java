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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import com.kingsrook.qbits.alerts.delivery.AlertRedactor;
import com.kingsrook.qbits.alerts.delivery.DeliveryResult;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Shared HTTP plumbing for the external channels: one pooled client, a 10
 ** second timeout, JSON bodies, and the outcome rules:
 ** - 2xx: SENT;
 ** - 408, 425, 429 and 5xx, or a network error: RETRY;
 ** - any other 4xx (a bad URL or key): FAILED, permanently.
 ** URLs must be https unless the channel's settings say "allowInsecureHttp"
 ** (for local receivers and tests). Error text is redacted and never includes
 ** the URL, which for Slack and Teams is itself the secret.
 *******************************************************************************/
public final class HttpChannelSupport
{
   static final Duration TIMEOUT = Duration.ofSeconds(10);

   private static final HttpClient CLIENT = HttpClient.newBuilder()
      .connectTimeout(TIMEOUT)
      .followRedirects(HttpClient.Redirect.NEVER)
      .build();



   /*******************************************************************************
    ** Helpers only.
    *******************************************************************************/
   private HttpChannelSupport()
   {
   }



   /*******************************************************************************
    ** POST a JSON body and classify the response.
    *******************************************************************************/
   public static DeliveryResult postJson(String url, Map<String, String> headers, byte[] body, boolean allowInsecure)
   {
      String problem = checkUrl(url, allowInsecure);
      if(problem != null)
      {
         return (DeliveryResult.failed(problem));
      }
      try
      {
         HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
            .timeout(TIMEOUT)
            .header("Content-Type", "application/json")
            .header("User-Agent", "qbit-alerts")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body));
         if(headers != null)
         {
            headers.forEach(request::header);
         }
         HttpResponse<String> response = CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
         return (classify(response.statusCode(), response.body()));
      }
      catch(InterruptedException e)
      {
         Thread.currentThread().interrupt();
         return (DeliveryResult.retry("Interrupted while sending."));
      }
      catch(Exception e)
      {
         return (DeliveryResult.retry(AlertRedactor.redact(e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + stripUrl(e.getMessage(), url)))));
      }
   }



   /*******************************************************************************
    ** SENT, RETRY or FAILED for an HTTP status.
    *******************************************************************************/
   static DeliveryResult classify(int status, String body)
   {
      if(status >= 200 && status < 300)
      {
         return (DeliveryResult.sent(null));
      }
      String detail = "HTTP " + status + (StringUtils.hasContent(body) ? ": " + AlertRedactor.redact(StringUtils.safeTruncate(body.trim(), 200, "…")) : "");
      if(status == 408 || status == 425 || status == 429 || status >= 500)
      {
         return (DeliveryResult.retry(detail));
      }
      return (DeliveryResult.failed(detail));
   }



   /*******************************************************************************
    ** Why a URL can't be used (null when it can).
    *******************************************************************************/
   static String checkUrl(String url, boolean allowInsecure)
   {
      if(!StringUtils.hasContent(url))
      {
         return ("No URL is configured (check the channel's secret reference or settings).");
      }
      String lower = url.trim().toLowerCase(Locale.ROOT);
      if(lower.startsWith("https://") || (allowInsecure && lower.startsWith("http://")))
      {
         return (null);
      }
      return ("The URL must use https.");
   }



   /*******************************************************************************
    ** The channel's settings JSON as a map (empty when blank or unreadable).
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   public static Map<String, Serializable> settings(QRecord channel)
   {
      String json = channel == null ? null : channel.getValueString("settings");
      if(!StringUtils.hasContent(json))
      {
         return (new LinkedHashMap<>());
      }
      try
      {
         return (JsonUtils.toObject(json, LinkedHashMap.class));
      }
      catch(Exception e)
      {
         return (new LinkedHashMap<>());
      }
   }



   /*******************************************************************************
    ** Whether the channel allows plain http (local receivers, tests).
    *******************************************************************************/
   public static boolean allowInsecure(QRecord channel)
   {
      return (Boolean.TRUE.equals(settings(channel).get("allowInsecureHttp")));
   }



   /*******************************************************************************
    ** JSON bytes.
    *******************************************************************************/
   public static byte[] json(Object body)
   {
      return (JsonUtils.toJson(body).getBytes(StandardCharsets.UTF_8));
   }



   /*******************************************************************************
    ** Remove a (secret) URL from an error message.
    *******************************************************************************/
   private static String stripUrl(String message, String url)
   {
      return (url == null ? message : message.replace(url, "[url]"));
   }
}
