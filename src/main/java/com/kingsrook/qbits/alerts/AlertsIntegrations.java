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

package com.kingsrook.qbits.alerts;


import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.alerts.model.AlertsAlert;
import com.kingsrook.qbits.alerts.model.AlertsMonitor;
import com.kingsrook.qbits.alerts.model.AlertsType;


/*******************************************************************************
 ** Opt-in hooks for wiring the main Alerts tables into a host's ESB and
 ** Quick Search without the qbit depending on either module: the host passes
 ** these names and field lists to its own qqq-esb (EsbTableMetaData
 ** publications) and qbit-quick-search (SearchableTableConfig) configuration.
 **
 ** The main tables are Alerts, Monitors and Alert Types. What leaves the
 ** database: an alert event carries the alert row (title, summary, details,
 ** status, severity, ...); search hits are re-read through the tables'
 ** record locks, so a user only finds alerts they could open.
 *******************************************************************************/
public final class AlertsIntegrations
{

   /*******************************************************************************
    ** A table for Quick Search: field weights (most important first), the
    ** field basepull compares, and how a hit is labelled.
    **
    ** @param tableName         the table
    ** @param fieldWeights      searchable fields and weights
    ** @param basepullTimestamp the field that moves when a row changes
    ** @param recordLabelFormat the hit label format
    ** @param recordLabelFields fields for the format
    *******************************************************************************/
   public record SearchableTable(String tableName, Map<String, Integer> fieldWeights, String basepullTimestamp, String recordLabelFormat, List<String> recordLabelFields)
   {
   }



   /*******************************************************************************
    ** Hooks only.
    *******************************************************************************/
   private AlertsIntegrations()
   {
   }



   /*******************************************************************************
    ** The tables whose insert / update / delete events a host may publish to
    ** its ESB.
    *******************************************************************************/
   public static List<String> esbTableNames()
   {
      return (List.of(AlertsAlert.TABLE_NAME, AlertsMonitor.TABLE_NAME, AlertsType.TABLE_NAME));
   }



   /*******************************************************************************
    ** The main tables as Quick Search tables.
    *******************************************************************************/
   public static List<SearchableTable> quickSearchTables()
   {
      Map<String, Integer> alert = new LinkedHashMap<>();
      alert.put("title", 10);
      alert.put("summary", 5);
      alert.put("alertTypeName", 4);
      alert.put("dedupKey", 3);
      alert.put("sourceName", 1);

      Map<String, Integer> monitor = new LinkedHashMap<>();
      monitor.put("name", 10);
      monitor.put("description", 4);
      monitor.put("sourceName", 1);

      Map<String, Integer> type = new LinkedHashMap<>();
      type.put("label", 10);
      type.put("name", 8);
      type.put("description", 4);
      type.put("sourceName", 1);

      return (List.of(
         new SearchableTable(AlertsAlert.TABLE_NAME, alert, "modifyDate", "%s", List.of("title")),
         new SearchableTable(AlertsMonitor.TABLE_NAME, monitor, "modifyDate", "Monitor: %s", List.of("name")),
         new SearchableTable(AlertsType.TABLE_NAME, type, "modifyDate", "Alert type: %s", List.of("label"))));
   }
}
