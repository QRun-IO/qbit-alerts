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


import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.alerts.model.AlertsAlert;
import com.kingsrook.qbits.alerts.model.AlertsMonitor;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** Engine bookkeeping columns: evaluation leases, run results and notification
 ** timers that change on every scheduler pass. An update that touches only
 ** these (plus the primary key and modifyDate) is not record-audited; the core
 ** FIELD audit would otherwise write an audit row per monitor run and per
 ** reminder check. Any update that also changes a configuration or alert-state
 ** field (status, severity, acknowledgement, assignment, a monitor's settings)
 ** is audited in full, as before. Run history lives in the run fields
 ** themselves; alert raises, transitions and notifications have their own
 ** event and delivery tables.
 *******************************************************************************/
public final class RuntimeFields
{
   private static final Set<String> ALWAYS = Set.of("id", "modifyDate");

   private static final Map<String, Set<String>> BY_TABLE = Map.of(
      AlertsMonitor.TABLE_NAME, Set.of("claimedBy", "claimedUntil", "lastRunAt", "nextRunAt", "lastStatus", "lastValue", "lastError",
         "lastElapsedMs", "consecutiveFailures", "conditionSince"),
      AlertsAlert.TABLE_NAME, Set.of("timedNotificationCheckedAt", "lastNotifiedAt", "recordRefCount"));



   /*******************************************************************************
    ** Constants only.
    *******************************************************************************/
   private RuntimeFields()
   {
   }



   /*******************************************************************************
    ** The bookkeeping fields of a table (empty when it has none).
    *******************************************************************************/
   public static Set<String> of(String tableName)
   {
      return (BY_TABLE.getOrDefault(tableName, Set.of()));
   }



   /*******************************************************************************
    ** Whether an update record carries nothing but bookkeeping fields.
    *******************************************************************************/
   public static boolean onlyRuntime(String tableName, QRecord update)
   {
      Set<String> runtime = of(tableName);
      return (!runtime.isEmpty() && update.getValues().keySet().stream().allMatch(field -> runtime.contains(field) || ALWAYS.contains(field)));
   }
}
