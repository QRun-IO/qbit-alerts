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

package com.kingsrook.qbits.alerts.home;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.alerts.AlertsRuntime;
import com.kingsrook.qbits.alerts.AlertsSecurity;
import com.kingsrook.qbits.alerts.engine.AlertsDb;
import com.kingsrook.qbits.alerts.model.AlertsGroup;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.Status;
import com.kingsrook.qqq.backend.core.alerts.AlertSeverity;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** Link-only summaries for other pages (design section 6.5): the Admin home
 ** "Needs Attention", other qbits' homes. They use the owner's numbers and
 ** permission filtering (the current session's record locks) and never query
 ** the alert tables themselves. Public API.
 *******************************************************************************/
public final class AlertSummaryLoader
{

   /*******************************************************************************
    ** Open alerts in one group, by severity.
    **
    ** @param groupId    the group (null: no group)
    ** @param groupName  its name
    ** @param groupLabel its label
    ** @param counts     open alerts by severity
    ** @param link       the group's filtered Alerts list
    *******************************************************************************/
   public record GroupSummary(Integer groupId, String groupName, String groupLabel, Map<AlertSeverity, Integer> counts, String link)
   {
      /***************************************************************************
       ** Open alerts of this severity or higher.
       ***************************************************************************/
      public int atLeast(AlertSeverity severity)
      {
         int total = 0;
         for(Map.Entry<AlertSeverity, Integer> entry : counts.entrySet())
         {
            if(entry.getKey().isAtLeast(severity))
            {
               total += entry.getValue();
            }
         }
         return (total);
      }



      /***************************************************************************
       ** "CDN · 1 critical, 2 errors".
       ***************************************************************************/
      public String line()
      {
         List<String> parts = new ArrayList<>();
         for(AlertSeverity severity : List.of(AlertSeverity.CRITICAL, AlertSeverity.ERROR, AlertSeverity.WARNING, AlertSeverity.INFO))
         {
            Integer count = counts.get(severity);
            if(count != null && count > 0)
            {
               String word = severity.name().toLowerCase();
               parts.add(count + " " + (count == 1 ? word : (word.equals("info") ? word : word + "s")));
            }
         }
         return (groupLabel + " · " + String.join(", ", parts));
      }
   }



   /*******************************************************************************
    ** Loader only.
    *******************************************************************************/
   private AlertSummaryLoader()
   {
   }



   /*******************************************************************************
    ** Open alerts the current session can see, by group (largest first). Only
    ** groups with at least minSeverity alerts are included (null: all).
    *******************************************************************************/
   public static List<GroupSummary> summarize(AlertSeverity minSeverity) throws QException
   {
      AlertsRuntime runtime = AlertsRuntime.get();
      AlertsSecurity.ensureSessionKeys(runtime.getQInstance(), QContext.getQSession());

      Map<Integer, Map<AlertSeverity, Integer>> byGroup = new LinkedHashMap<>();
      for(QRecord alert : AlertsDb.query("alerts_alert", new QQueryFilter().withCriteria("status", QCriteriaOperator.IN, Status.openNames()).withLimit(AlertsHome.SCAN_LIMIT)))
      {
         AlertSeverity severity = AlertSeverity.fromLabel(alert.getValueString("severity"), AlertSeverity.WARNING);
         byGroup.computeIfAbsent(alert.getValueInteger("groupId"), key -> new HashMap<>()).merge(severity, 1, Integer::sum);
      }

      Map<Integer, QRecord> groups = new HashMap<>();
      if(AlertsHome.canRead(AlertsGroup.TABLE_NAME))
      {
         for(QRecord group : AlertsDb.query(AlertsGroup.TABLE_NAME, new QQueryFilter()))
         {
            groups.put(group.getValueInteger("id"), group);
         }
      }

      List<GroupSummary> summaries = new ArrayList<>();
      for(Map.Entry<Integer, Map<AlertSeverity, Integer>> entry : byGroup.entrySet())
      {
         QRecord      group   = groups.get(entry.getKey());
         GroupSummary summary = new GroupSummary(entry.getKey(), group == null ? null : group.getValueString("name"), group == null ? "Other" : group.getValueString("label"),
            entry.getValue(), AlertsHome.listUrl(AlertsDb.eq("groupId", entry.getKey()).withCriteria("status", QCriteriaOperator.IN, Status.openNames())));
         if(minSeverity == null || summary.atLeast(minSeverity) > 0)
         {
            summaries.add(summary);
         }
      }
      summaries.sort((a, b) -> Integer.compare(b.atLeast(AlertSeverity.INFO), a.atLeast(AlertSeverity.INFO)));
      return (summaries);
   }



   /*******************************************************************************
    ** HTML for a host's "Needs Attention" widget: one linked line per group
    ** with ERROR or worse, plus "All alerts". Empty when nothing qualifies.
    *******************************************************************************/
   public static String needsAttentionHtml() throws QException
   {
      List<GroupSummary> summaries = summarize(AlertSeverity.ERROR);
      if(summaries.isEmpty())
      {
         return ("");
      }
      StringBuilder html = new StringBuilder("<div style=\"display:flex;flex-direction:column;gap:6px\">");
      for(GroupSummary summary : summaries)
      {
         html.append("<div>").append(HomeHtml.link(summary.link(), summary.line())).append("</div>");
      }
      html.append("<div>").append(HomeHtml.link("/app/alerts", "All alerts")).append("</div>");
      return (html.append("</div>").toString());
   }
}
