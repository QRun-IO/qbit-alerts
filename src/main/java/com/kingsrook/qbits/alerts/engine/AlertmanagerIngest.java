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

package com.kingsrook.qbits.alerts.engine;


import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.kingsrook.qbits.alerts.AlertsRuntime;
import com.kingsrook.qbits.alerts.delivery.AlertRedactor;
import com.kingsrook.qbits.alerts.delivery.channels.ExternalChannels;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.Resolution;
import com.kingsrook.qbits.alerts.model.AlertsType;
import com.kingsrook.qbits.alerts.spi.AlertSeverity;
import com.kingsrook.qbits.alerts.spi.AlertSignal;
import com.kingsrook.qbits.alerts.spi.DetailLevel;
import com.kingsrook.qbits.alerts.spi.QAlertTypeMetaData;
import com.kingsrook.qbits.alerts.spi.ResolveMode;
import com.kingsrook.qqq.backend.core.exceptions.QAuthenticationException;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QNotFoundException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Prometheus Alertmanager as a source (design section 5.7): the webhook
 ** receiver payload (version 4) becomes raises and resolves.
 ** - the alert's fingerprint is the dedup key; firing / resolved are raise /
 **   resolve; the severity label maps to a severity (unknown: WARNING);
 **   summary / description become title / summary (redacted); labels become
 **   attributes (bounded);
 ** - the type is prometheus.(alertname), created on first sight as an
 **   EXTERNAL type in group "infrastructure" with notifications OFF
 **   (Alertmanager already notifies people; turning QQQ notifications on is a
 **   per-type choice);
 ** - alerts carrying qqq_origin="qbit-alerts" (sent by this qbit's
 **   Alertmanager channel) are ignored, so nothing loops.
 ** Authentication: a bearer token compared (in constant time) with the
 ** configured secret reference. Ingest is off without one.
 *******************************************************************************/
public class AlertmanagerIngest
{
   public static final String TYPE_PREFIX = "prometheus.";
   public static final String GROUP      = "infrastructure";

   private final AlertsRuntime runtime;



   /*******************************************************************************
    ** What an ingest did.
    **
    ** @param raised   alerts raised (or refreshed)
    ** @param resolved alerts resolved
    ** @param ignored  alerts ignored (loop guard, or no alertname)
    *******************************************************************************/
   public record Result(int raised, int resolved, int ignored)
   {
   }



   /*******************************************************************************
    ** Constructor
    *******************************************************************************/
   public AlertmanagerIngest(AlertsRuntime runtime)
   {
      this.runtime = runtime;
   }



   /*******************************************************************************
    ** Check the bearer token: 404 when ingest is off, 401 when it doesn't match.
    *******************************************************************************/
   public void authenticate(String authorizationHeader) throws QException
   {
      String ref = runtime.getConfig().getAlertmanagerIngestTokenSecretRef();
      if(!StringUtils.hasContent(ref))
      {
         throw (new QNotFoundException("Alertmanager ingest is not enabled."));
      }
      String expected = runtime.getChannels().getSecrets().resolve(ref);
      String given    = authorizationHeader != null && authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7) ? authorizationHeader.substring(7).trim() : null;
      if(expected == null || given == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8)))
      {
         throw (new QAuthenticationException("Invalid ingest token."));
      }
   }



   /*******************************************************************************
    ** Apply one webhook payload.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   public Result ingest(Map<String, Object> payload) throws QException
   {
      Object alerts = payload == null ? null : payload.get("alerts");
      if(!(alerts instanceof List<?> list))
      {
         throw (new QBadRequestException("Expected an Alertmanager webhook payload with an \"alerts\" list."));
      }

      int raised   = 0;
      int resolved = 0;
      int ignored  = 0;
      for(Object item : list)
      {
         if(!(item instanceof Map<?, ?> alert))
         {
            ignored++;
            continue;
         }
         Map<String, Object> labels      = alert.get("labels") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
         Map<String, Object> annotations = alert.get("annotations") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
         String              alertname   = labels.get("alertname") == null ? null : String.valueOf(labels.get("alertname"));
         if(alertname == null || ExternalChannels.ORIGIN_VALUE.equals(String.valueOf(labels.get(ExternalChannels.ORIGIN_LABEL))))
         {
            ignored++;
            continue;
         }

         String typeName    = typeName(alertname);
         String fingerprint = alert.get("fingerprint") == null ? fingerprint(labels) : String.valueOf(alert.get("fingerprint"));
         ensureType(typeName, alertname);

         if("resolved".equalsIgnoreCase(String.valueOf(alert.get("status"))))
         {
            if(runtime.getEngine().resolve(typeName, fingerprint, Resolution.PRODUCER, null, "Resolved in Alertmanager") != null)
            {
               resolved++;
            }
            continue;
         }

         String      title  = firstNonBlank(annotations.get("summary"), annotations.get("description"), alertname);
         AlertSignal signal = new AlertSignal(typeName)
            .withDedupKey(fingerprint)
            .withSeverity(AlertSeverity.fromLabel(labels.get("severity") == null ? null : String.valueOf(labels.get("severity")), AlertSeverity.WARNING))
            .withTitle(AlertRedactor.redact(title))
            .withSummary(AlertRedactor.redact(annotations.get("description") == null ? null : String.valueOf(annotations.get("description"))))
            .withOccurredAt(parseInstant(alert.get("startsAt")));
         for(Map.Entry<String, Object> label : new LinkedHashMap<>(labels).entrySet())
         {
            signal.withAttribute(label.getKey(), String.valueOf(label.getValue()));
         }
         runtime.getEngine().raise(signal);
         raised++;
      }
      return (new Result(raised, resolved, ignored));
   }



   /*******************************************************************************
    ** Create the EXTERNAL type the first time (notifications off).
    *******************************************************************************/
   private void ensureType(String typeName, String alertname) throws QException
   {
      runtime.asSystem(() ->
      {
         if(AlertsDb.first(AlertsType.TABLE_NAME, AlertsDb.eq("name", typeName)) == null)
         {
            QRecord type = runtime.getTypeSync().upsertType(new QAlertTypeMetaData()
               .withName(typeName)
               .withLabel(alertname)
               .withSourceName("prometheus")
               .withGroupName(GROUP)
               .withDefaultSeverity(AlertSeverity.WARNING)
               .withResolveMode(ResolveMode.PRODUCER)
               .withExternalDetailCeiling(DetailLevel.STANDARD)
               .withDescription("Ingested from Prometheus Alertmanager (" + alertname + ").")
               .withHowToFix("See the alert's runbook in Prometheus. Alertmanager already notifies its receivers; turn on Notify here only to route it through Alerts too."));
            AlertsDb.update(AlertsType.TABLE_NAME, new QRecord().withValue("id", type.getValue("id")).withValue("notify", false));
         }
         return (null);
      });
   }



   /*******************************************************************************
    ** "HighCPU Usage" to "prometheus.highcpu-usage".
    *******************************************************************************/
   public static String typeName(String alertname)
   {
      String slug = alertname.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]+", "-").replaceAll("^-+|-+$", "");
      return (TYPE_PREFIX + (slug.isEmpty() ? "unnamed" : slug));
   }



   /*******************************************************************************
    ** A fingerprint from the labels when Alertmanager didn't send one.
    *******************************************************************************/
   static String fingerprint(Map<String, Object> labels)
   {
      return (Integer.toHexString(new java.util.TreeMap<>(labels).toString().hashCode()));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String firstNonBlank(Object... values)
   {
      for(Object value : values)
      {
         if(value != null && StringUtils.hasContent(String.valueOf(value)))
         {
            return (String.valueOf(value));
         }
      }
      return (null);
   }



   /*******************************************************************************
    ** An ISO instant, or null (Alertmanager's zero time is null too).
    *******************************************************************************/
   private static Instant parseInstant(Object value)
   {
      try
      {
         Instant instant = value == null ? null : Instant.parse(String.valueOf(value));
         return (instant == null || instant.getEpochSecond() <= 0 ? null : instant);
      }
      catch(Exception e)
      {
         return (null);
      }
   }
}
