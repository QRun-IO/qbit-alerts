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


/*******************************************************************************
 ** A rendered, redacted message for one channel and recipient: one alert, or
 ** a batch of several that arrived together ("3 new alerts").
 **
 ** @param subject   one line (email subject, chat headline)
 ** @param text      plain-text body
 ** @param html      simple HTML body (escaped)
 ** @param severity  the highest severity in the message
 ** @param kind      RAISED, RESOLVED, REMINDER, ESCALATED, DIGEST or TEST
 ** @param alertIds  the alerts in the message
 ** @param link      a link to the alert (or the Alerts list) in the app
 ** @param entries   one structured entry per alert, for channels that send
 **                  one event per alert (PagerDuty, Alertmanager, webhooks);
 **                  already redacted and capped by the detail level
 ** @param idempotencyKey durable digest dispatch key, null for immediate messages
 *******************************************************************************/
public record AlertMessage(String subject, String text, String html, String severity, String kind, List<Integer> alertIds, String link, List<Entry> entries, String idempotencyKey)
{
   /*******************************************************************************
    ** A message without a durable dispatch key.
    *******************************************************************************/
   public AlertMessage(String subject, String text, String html, String severity, String kind, List<Integer> alertIds, String link, List<Entry> entries)
   {
      this(subject, text, html, severity, kind, alertIds, link, entries, null);
   }



   /*******************************************************************************
    ** A message without structured entries.
    *******************************************************************************/
   public AlertMessage(String subject, String text, String html, String severity, String kind, List<Integer> alertIds, String link)
   {
      this(subject, text, html, severity, kind, alertIds, link, List.of());
   }



   /*******************************************************************************
    ** One alert, as structured channels send it.
    **
    ** @param alertId       the alert's id (null for a test message)
    ** @param alertTypeName the type, e.g. cdn.purge.dead
    ** @param typeLabel     the type's label
    ** @param dedupKey      the producer's dedup key
    ** @param severity      INFO, WARNING, ERROR or CRITICAL
    ** @param status        OPEN, ACKNOWLEDGED, SNOOZED or RESOLVED
    ** @param title         the redacted title
    ** @param summary       the redacted summary (null at MINIMAL)
    ** @param group         the group's label
    ** @param itemCount     the record count (null at MINIMAL)
    ** @param link          the alert in the app
    ** @param firstRaisedAt when it opened (ISO)
    ** @param resolvedAt    when it resolved (ISO), or null
    *******************************************************************************/
   public record Entry(Integer alertId, String alertTypeName, String typeLabel, String dedupKey, String severity, String status, String title, String summary,
                       String group, Integer itemCount, String link, String firstRaisedAt, String resolvedAt)
   {
      /***************************************************************************
       ** The stable key for systems that dedup (PagerDuty, Alertmanager).
       ***************************************************************************/
      public String stableKey()
      {
         return ((alertTypeName == null ? "alerts.test" : alertTypeName) + ":" + (dedupKey == null ? String.valueOf(alertId) : dedupKey));
      }
   }
}
