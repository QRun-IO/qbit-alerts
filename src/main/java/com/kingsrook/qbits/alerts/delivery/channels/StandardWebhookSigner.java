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


import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;


/*******************************************************************************
 ** STAND-IN for core's WebhookSigner (QRun-IO/qqq#1109, the shared signer of
 ** the qbit-webhooks assessment, QRun-IO/qqq#1105 D2). Same format: the
 ** Standard Webhooks headers webhook-id, webhook-timestamp and
 ** webhook-signature = "v1," + base64(HMAC-SHA256(key, id + "." + timestamp +
 ** "." + body)); a "whsec_" secret is base64 after the prefix, any other text
 ** is the raw key. Replace with com.kingsrook.qqq.backend.core.utils.webhooks
 ** .WebhookSigner when the QQQ RC with #1109 ships, so receivers verify Alerts
 ** and qbit-webhooks deliveries the same way.
 *******************************************************************************/
public final class StandardWebhookSigner
{
   public static final String HEADER_ID        = "webhook-id";
   public static final String HEADER_TIMESTAMP = "webhook-timestamp";
   public static final String HEADER_SIGNATURE = "webhook-signature";

   private static final String PREFIX = "whsec_";



   /*******************************************************************************
    ** Signing only.
    *******************************************************************************/
   private StandardWebhookSigner()
   {
   }



   /*******************************************************************************
    ** The three signature headers.
    *******************************************************************************/
   public static Map<String, String> headers(String messageId, Instant timestamp, byte[] body, String secret)
   {
      long                seconds = timestamp.getEpochSecond();
      Map<String, String> headers = new LinkedHashMap<>();
      headers.put(HEADER_ID, messageId);
      headers.put(HEADER_TIMESTAMP, Long.toString(seconds));
      headers.put(HEADER_SIGNATURE, "v1," + sign(messageId, seconds, body, secret));
      return (headers);
   }



   /*******************************************************************************
    ** base64 HMAC-SHA256 over id.timestamp.body.
    *******************************************************************************/
   public static String sign(String messageId, long timestampSeconds, byte[] body, String secret)
   {
      if(messageId == null || messageId.isEmpty() || messageId.contains(".") || messageId.chars().anyMatch(Character::isWhitespace))
      {
         throw (new IllegalArgumentException("A webhook message id is required and may not contain '.' or whitespace"));
      }
      try
      {
         Mac mac = Mac.getInstance("HmacSHA256");
         mac.init(new SecretKeySpec(keyBytes(secret), "HmacSHA256"));
         mac.update((messageId + "." + timestampSeconds + ".").getBytes(StandardCharsets.UTF_8));
         mac.update(body == null ? new byte[0] : body);
         return (Base64.getEncoder().encodeToString(mac.doFinal()));
      }
      catch(GeneralSecurityException e)
      {
         throw (new IllegalStateException("HMAC-SHA256 is unavailable", e));
      }
   }



   /*******************************************************************************
    ** The key bytes of a secret's text form.
    *******************************************************************************/
   static byte[] keyBytes(String secret)
   {
      if(secret == null || secret.isBlank())
      {
         throw (new IllegalArgumentException("A webhook signing secret is required"));
      }
      String trimmed = secret.trim();
      return (trimmed.startsWith(PREFIX) ? Base64.getDecoder().decode(trimmed.substring(PREFIX.length())) : trimmed.getBytes(StandardCharsets.UTF_8));
   }
}
