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


import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Stream;
import com.kingsrook.qbits.alerts.delivery.channels.StandardWebhookSigner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Fixed wire vectors generated independently with Python's hmac/hashlib.
 ** Cover both secret encodings, short legacy keys and a key over one block.
 *******************************************************************************/
class StandardWebhookSignerTest
{
   /*******************************************************************************
    ** Raw and whsec_ secrets produce the exact same legacy header values.
    *******************************************************************************/
   @ParameterizedTest
   @MethodSource("wireVectors")
   void testLegacyWireFormat(String key, String expectedSignature)
   {
      Instant timestamp = Instant.ofEpochSecond(1791806400L);
      byte[] body = "{\"event\":\"test\"}".getBytes(StandardCharsets.UTF_8);
      Map<String, String> expected = Map.of("webhook-id", "msg_test", "webhook-timestamp", "1791806400", "webhook-signature", expectedSignature);
      assertThat(StandardWebhookSigner.headers("msg_test", timestamp, body, "  " + key + "  ")).isEqualTo(expected);
      String encoded = "whsec_" + Base64.getEncoder().encodeToString(key.getBytes(StandardCharsets.UTF_8));
      assertThat(StandardWebhookSigner.headers("msg_test", timestamp, body, encoded)).isEqualTo(expected);
   }



   /*******************************************************************************
    ** Independent expected signatures, including the 15/16-byte core boundary.
    *******************************************************************************/
   private static Stream<Arguments> wireVectors()
   {
      return (Stream.of(
         Arguments.of("short", "v1,Jz84jUiA/gQwIt0Td1DzUA/OE5soKzC5H9C5e94lliM="),
         Arguments.of("sixteen-byte-key", "v1,qep/FxanCVlGy2Zohku2Bgd3ufM4KNRcj5qT8t7blNA="),
         Arguments.of("sixteen-byte-key!", "v1,tbs9jXA3r4N06ZLpuOe6V4RasiRwYwbM2+2ZxRu8lNU="),
         Arguments.of("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", "v1,K6s1XISWGs8vzyUo3Ecush1eoxy/d5CiND+gzS70I8U=")));
   }



   /*******************************************************************************
    ** Empty or malformed keys still fail before any request is sent.
    *******************************************************************************/
   @Test
   void testInvalidSecrets()
   {
      for(String secret : new String[] { null, "", "   ", "whsec_", "whsec_!" })
      {
         assertThatThrownBy(() -> StandardWebhookSigner.headers("msg_test", Instant.EPOCH, null, secret)).isInstanceOf(IllegalArgumentException.class);
      }
   }
}
