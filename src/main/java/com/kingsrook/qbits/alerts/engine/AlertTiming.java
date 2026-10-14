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


import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Locale;
import com.kingsrook.qqq.backend.core.alerts.AlertSeverity;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** User-local quiet windows and subscription digest boundaries.
 *******************************************************************************/
public final class AlertTiming
{
   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private AlertTiming()
   {
   }



   /*******************************************************************************
    ** Missing or invalid zones use UTC, independently of the server's zone.
    *******************************************************************************/
   public static ZoneId zone(QRecord preference)
   {
      try
      {
         String name = preference == null ? null : preference.getValueString("timeZone");
         return (StringUtils.hasContent(name) ? ZoneId.of(name) : ZoneOffset.UTC);
      }
      catch(DateTimeException e)
      {
         return (ZoneOffset.UTC);
      }
   }



   /*******************************************************************************
    ** The start is inclusive, the end exclusive. Overnight windows belong to
    ** their starting day. Days accept ISO numbers (1=Monday), names or MON etc.
    ** Missing, invalid or equal endpoints disable quiet hours.
    *******************************************************************************/
   public static boolean isQuiet(QRecord preference, AlertSeverity severity, Instant now)
   {
      if(preference == null || severity.isAtLeast(AlertSeverity.fromLabel(preference.getValueString("quietBypassSeverity"), AlertSeverity.CRITICAL)))
      {
         return (false);
      }
      String startValue = preference.getValueString("quietHoursStart");
      String endValue = preference.getValueString("quietHoursEnd");
      if(!StringUtils.hasContent(startValue) || !StringUtils.hasContent(endValue))
      {
         return (false);
      }
      try
      {
         LocalTime start = LocalTime.parse(startValue);
         LocalTime end = LocalTime.parse(endValue);
         ZonedDateTime local = now.atZone(zone(preference));
         LocalTime time = local.toLocalTime();
         boolean overnight = start.isAfter(end);
         boolean inside = overnight ? !time.isBefore(start) || time.isBefore(end) : !time.isBefore(start) && time.isBefore(end);
         if(!inside)
         {
            return (false);
         }
         var day = (overnight && time.isBefore(end) ? local.minusDays(1) : local).getDayOfWeek();
         String days = preference.getValueString("quietDays");
         return (!StringUtils.hasContent(days) || Arrays.stream(days.toUpperCase(Locale.ROOT).split("[,\\s]+"))
            .anyMatch(value -> value.equals(day.name()) || value.equals(day.name().substring(0, 3)) || value.equals(String.valueOf(day.getValue()))));
      }
      catch(DateTimeException e)
      {
         return (false);
      }
   }



   /*******************************************************************************
    ** A daily window ends at the next local digest hour (default 08:00).
    ** Local date arithmetic preserves the hour across daylight saving changes.
    *******************************************************************************/
   public static Instant digestEnd(String mode, QRecord preference, Instant now)
   {
      ZonedDateTime local = now.atZone(zone(preference));
      if("DIGEST_DAILY".equals(mode))
      {
         int hour = digestHour(preference);
         ZonedDateTime end = local.toLocalDate().atTime(hour, 0).atZone(local.getZone());
         return ((end.toInstant().isAfter(now) ? end : local.toLocalDate().plusDays(1).atTime(hour, 0).atZone(local.getZone())).toInstant());
      }
      return (local.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant());
   }



   /*******************************************************************************
    ** Stable window identity stored with each queued item.
    *******************************************************************************/
   public static Instant digestStart(String mode, QRecord preference, Instant end)
   {
      return ("DIGEST_DAILY".equals(mode) ? end.atZone(zone(preference)).toLocalDate().minusDays(1).atTime(digestHour(preference), 0).atZone(zone(preference)).toInstant() : end.minus(1, ChronoUnit.HOURS));
   }



   /*******************************************************************************
    ** A valid configured hour, or the default 08:00.
    *******************************************************************************/
   private static int digestHour(QRecord preference)
   {
      Integer configured = preference == null ? null : preference.getValueInteger("digestHour");
      return (configured == null || configured < 0 || configured > 23 ? 8 : configured);
   }
}
