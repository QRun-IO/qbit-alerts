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


import java.util.List;
import java.util.regex.Pattern;


/*******************************************************************************
 ** Masks personal data and secrets in every outbound string (design section
 ** 7.4): URLs with credentials, JWTs, bearer tokens, key= / secret= /
 ** password= / token= values, email addresses, IPv4 and IPv6 addresses,
 ** phone numbers, and long hex or base64 strings (32+ characters). Titles and
 ** summaries are meant to be written without personal data; this is the
 ** safety net.
 *******************************************************************************/
public final class AlertRedactor
{
   public static final String MASK = "[redacted]";

   private static final List<Pattern> PATTERNS = List.of(
      Pattern.compile("\\b[a-zA-Z][a-zA-Z0-9+.-]*://[^\\s/:@]+:[^\\s/@]+@[^\\s]+"),
      Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"),
      Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]+"),
      Pattern.compile("(?i)\\b(api[_-]?key|key|secret|password|passwd|pwd|token|access[_-]?token)\\s*[=:]\\s*[^\\s,;&]+"),
      Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
      Pattern.compile("\\b(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\b"),
      Pattern.compile("(?i)\\b(?:[0-9a-f]{1,4}:){2,7}[0-9a-f]{1,4}\\b"),
      Pattern.compile("\\+\\d[\\d ().-]{7,}\\d"),
      Pattern.compile("\\(\\d{3}\\)\\s?\\d{3}[-.\\s]\\d{4}\\b"),
      Pattern.compile("\\b\\d{3}[-.\\s]\\d{3}[-.\\s]\\d{4}\\b"),
      Pattern.compile("\\b[A-Fa-f0-9]{32,}\\b"),
      Pattern.compile("\\b[A-Za-z0-9+/_-]{32,}={0,2}"));



   /*******************************************************************************
    ** Masking only.
    *******************************************************************************/
   private AlertRedactor()
   {
   }



   /*******************************************************************************
    ** The text with every sensitive match replaced by [redacted] (null stays
    ** null).
    *******************************************************************************/
   public static String redact(String text)
   {
      if(text == null || text.isEmpty())
      {
         return (text);
      }
      String result = text;
      for(Pattern pattern : PATTERNS)
      {
         result = pattern.matcher(result).replaceAll(MASK);
      }
      return (result);
   }
}
