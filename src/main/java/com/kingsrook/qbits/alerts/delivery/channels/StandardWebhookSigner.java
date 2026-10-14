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


import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import com.kingsrook.qqq.backend.core.utils.webhooks.WebhookSecret;
import com.kingsrook.qqq.backend.core.utils.webhooks.WebhookSigner;


/*******************************************************************************
 ** Compatibility adapter for the qbit's existing webhook secret strings.
 ** Core signs every request. Core requires at least 16 key bytes, whereas the
 ** previous signer accepted shorter keys. HMAC-SHA256 zero-pads keys to its
 ** 64-byte block size, so padding a short legacy key to 16 bytes preserves
 ** its signature exactly. New secrets should use WebhookSecret.generate().
 *******************************************************************************/
public final class StandardWebhookSigner
{
   /*******************************************************************************
    ** Signing only.
    *******************************************************************************/
   private StandardWebhookSigner()
   {
   }



   /*******************************************************************************
    ** The three core Standard Webhooks headers, preserving legacy short keys.
    *******************************************************************************/
   public static Map<String, String> headers(String messageId, Instant timestamp, byte[] body, String secret)
   {
      return (WebhookSigner.headers(messageId, timestamp, body, List.of(parseSecret(secret))));
   }



   /*******************************************************************************
    ** Preserve raw UTF-8 and whsec_ base64 secrets, including legacy short keys.
    *******************************************************************************/
   private static WebhookSecret parseSecret(String secret)
   {
      if(secret == null || secret.isBlank())
      {
         throw (new IllegalArgumentException("A webhook signing secret is required"));
      }
      String trimmed = secret.trim();
      byte[] key = trimmed.startsWith(WebhookSecret.PREFIX)
         ? Base64.getDecoder().decode(trimmed.substring(WebhookSecret.PREFIX.length()))
         : trimmed.getBytes(StandardCharsets.UTF_8);
      if(key.length == 0)
      {
         throw (new IllegalArgumentException("A webhook signing secret is required"));
      }
      if(key.length < WebhookSecret.MINIMUM_KEY_BYTES)
      {
         return (WebhookSecret.parse(WebhookSecret.PREFIX + Base64.getEncoder().encodeToString(Arrays.copyOf(key, WebhookSecret.MINIMUM_KEY_BYTES))));
      }
      return (WebhookSecret.parse(trimmed));
   }
}
