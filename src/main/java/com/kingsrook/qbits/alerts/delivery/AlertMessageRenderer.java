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


import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.alerts.home.HomeHtml;
import com.kingsrook.qbits.alerts.model.AlertsAlert;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.NotificationKind;
import com.kingsrook.qbits.alerts.spi.AlertSeverity;
import com.kingsrook.qbits.alerts.spi.DetailLevel;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Renders alerts into a message for an external channel, from fixed
 ** templates (producers supply only data), at a detail level (design 7.4):
 ** - MINIMAL: severity, type label, group, title, age and one link;
 ** - STANDARD: adds the summary and the counts;
 ** - FULL: adds the details.
 ** The level is the lower of the channel's and the type's ceiling. Every
 ** string except the generated link goes through AlertRedactor.
 *******************************************************************************/
public final class AlertMessageRenderer
{

   /*******************************************************************************
    ** What the renderer knows about one alert.
    **
    ** @param alert      the alerts_alert row
    ** @param typeLabel  its type's label
    ** @param groupLabel its group's label
    ** @param level      the effective detail level for this channel
    *******************************************************************************/
   public record Item(QRecord alert, String typeLabel, String groupLabel, DetailLevel level)
   {
   }



   /*******************************************************************************
    ** Rendering only.
    *******************************************************************************/
   private AlertMessageRenderer()
   {
   }



   /*******************************************************************************
    ** One message for one or more alerts.
    *******************************************************************************/
   public static AlertMessage render(List<Item> items, NotificationKind kind, String publicBaseUrl, Instant now)
   {
      AlertSeverity highest  = AlertSeverity.INFO;
      List<Integer> alertIds = new ArrayList<>();
      for(Item item : items)
      {
         AlertSeverity severity = AlertSeverity.fromLabel(item.alert().getValueString("severity"), AlertSeverity.WARNING);
         if(severity.compareTo(highest) > 0)
         {
            highest = severity;
         }
         alertIds.add(item.alert().getValueInteger("id"));
      }

      String base   = StringUtils.hasContent(publicBaseUrl) ? publicBaseUrl.replaceAll("/+$", "") : "";
      String prefix = kind == NotificationKind.TEST ? "TEST: " : "";
      String subject;
      String link;
      if(items.size() == 1)
      {
         QRecord alert = items.get(0).alert();
         String  state = kind == NotificationKind.RESOLVED ? "RESOLVED" : AlertSeverity.fromLabel(alert.getValueString("severity"), AlertSeverity.WARNING).name();
         subject = prefix + "[" + state + "] " + AlertRedactor.redact(alert.getValueString("title"));
         link = base + "/app/" + AlertsAlert.TABLE_NAME + "/" + alert.getValue("id");
      }
      else
      {
         String group = commonGroup(items);
         subject = prefix + items.size() + (kind == NotificationKind.RESOLVED ? " alerts resolved" : " new alerts") + (group == null ? "" : " in " + group);
         link = base + "/app/" + AlertsAlert.TABLE_NAME;
      }

      StringBuilder text = new StringBuilder();
      StringBuilder html = new StringBuilder("<div style=\"font-family:sans-serif;font-size:14px;line-height:1.5\">");
      if(kind == NotificationKind.TEST)
      {
         text.append("This is a test notification from Alerts.\n\n");
         html.append("<p><strong>This is a test notification from Alerts.</strong></p>");
      }
      for(Item item : items)
      {
         QRecord     alert = item.alert();
         DetailLevel level = item.level() == null ? DetailLevel.MINIMAL : item.level();
         String      line  = "[" + alert.getValueString("severity") + "] " + safe(item.typeLabel()) + (item.groupLabel() == null ? "" : " / " + safe(item.groupLabel()))
            + ": " + safe(alert.getValueString("title")) + " (" + age(alert.getValueInstant("firstRaisedAt"), now) + ")";
         text.append(line).append('\n');
         html.append("<p><strong>").append(HomeHtml.escape(line)).append("</strong>");

         if(level.compareTo(DetailLevel.STANDARD) >= 0)
         {
            if(StringUtils.hasContent(alert.getValueString("summary")))
            {
               text.append(safe(alert.getValueString("summary"))).append('\n');
               html.append("<br>").append(HomeHtml.escape(safe(alert.getValueString("summary"))));
            }
            Integer count = alert.getValueInteger("itemCount");
            if(count != null)
            {
               text.append(count == 1 ? "1 record" : count + " records").append('\n');
               html.append("<br>").append(count == 1 ? "1 record" : count + " records");
            }
         }
         if(level == DetailLevel.FULL && StringUtils.hasContent(alert.getValueString("details")))
         {
            text.append(safe(alert.getValueString("details"))).append('\n');
            html.append("<br><span style=\"white-space:pre-wrap\">").append(HomeHtml.escape(safe(alert.getValueString("details")))).append("</span>");
         }
         html.append("</p>");
         text.append('\n');
      }

      text.append("Open: ").append(link).append("\n\n");
      text.append("Change or stop these emails: ").append(base).append("/app/alerts_subscription\n");
      text.append("Personal data and secrets are removed from alert messages.\n");
      html.append("<p><a href=\"").append(HomeHtml.escape(link)).append("\">Open in Alerts</a></p>");
      html.append("<p style=\"font-size:12px;opacity:.75\"><a href=\"").append(HomeHtml.escape(base + "/app/alerts_subscription")).append("\">Change or stop these emails</a>. ")
         .append("Personal data and secrets are removed from alert messages.</p></div>");

      return (new AlertMessage(subject, text.toString(), html.toString(), highest.name(), kind.name(), alertIds, link));
   }



   /*******************************************************************************
    ** The group all items share, or null.
    *******************************************************************************/
   private static String commonGroup(List<Item> items)
   {
      String group = items.get(0).groupLabel();
      for(Item item : items)
      {
         if(group == null || !group.equals(item.groupLabel()))
         {
            return (null);
         }
      }
      return (group);
   }



   /*******************************************************************************
    ** Redacted text (null is empty).
    *******************************************************************************/
   private static String safe(String text)
   {
      return (text == null ? "" : AlertRedactor.redact(text));
   }



   /*******************************************************************************
    ** "3 h", "12 min", "2 d".
    *******************************************************************************/
   public static String age(Instant since, Instant now)
   {
      if(since == null || now == null)
      {
         return ("now");
      }
      Duration duration = Duration.between(since, now);
      if(duration.isNegative() || duration.toMinutes() < 1)
      {
         return ("just now");
      }
      if(duration.toMinutes() < 60)
      {
         return (duration.toMinutes() + " min");
      }
      if(duration.toHours() < 48)
      {
         return (duration.toHours() + " h");
      }
      return (duration.toDays() + " d");
   }



   /*******************************************************************************
    ** Labels by id, for items (helper for callers that cache them).
    *******************************************************************************/
   public static String label(Map<Integer, String> labels, Integer id)
   {
      return (id == null ? null : labels.get(id));
   }
}
