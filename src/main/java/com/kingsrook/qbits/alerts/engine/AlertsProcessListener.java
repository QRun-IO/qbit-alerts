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


import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.alerts.AlertsRuntime;
import com.kingsrook.qbits.alerts.model.AlertsMonitor;
import com.kingsrook.qbits.alerts.spi.AlertMonitorKind;
import com.kingsrook.qqq.backend.core.actions.processes.listeners.ProcessLifecycleListenerInterface;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.utils.ValueUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Feeds PROCESS_FAILED and process HEARTBEAT monitors from QQQ's process
 ** lifecycle events (no ESB needed): a failed run raises, a completed run
 ** resolves and beats the process's heartbeat key. Only processes some
 ** monitor watches are handled (the watched names are cached for a minute),
 ** so other process runs cost one set lookup. Never affects the process.
 *******************************************************************************/
public class AlertsProcessListener implements ProcessLifecycleListenerInterface
{
   private static final QLogger LOG = QLogger.getLogger(AlertsProcessListener.class);

   private static final long CACHE_MILLIS = 60_000;

   private static volatile Set<String> watched     = null;
   private static volatile long        watchedAt   = 0;



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public boolean appliesTo(String processName)
   {
      AlertsRuntime runtime = AlertsRuntime.find(QContext.getQInstance());
      if(runtime == null || processName == null)
      {
         return (false);
      }
      for(String pattern : watched(runtime))
      {
         if(processName.equals(pattern) || processName.matches(pattern.replace(".", "\\.").replace("*", ".*")))
         {
            return (true);
         }
      }
      return (false);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public void onProcessStarted(RunProcessInput input)
   {
      ////////////////////////////////////////////
      // noop - only outcomes matter for alerts //
      ////////////////////////////////////////////
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public void onProcessCompleted(RunProcessInput input, RunProcessOutput output)
   {
      AlertsRuntime runtime = AlertsRuntime.find(QContext.getQInstance());
      if(runtime == null)
      {
         return;
      }
      try
      {
         runtime.getEngine().heartbeat(MonitorEvaluator.processHeartbeatKey(input.getProcessName()), runtime.now());
         runtime.getEvaluator().processCompleted(input.getProcessName());
      }
      catch(Exception e)
      {
         LOG.warn("Error recording a process completion for alerts", e, logPair("processName", input.getProcessName()));
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public void onProcessFailed(RunProcessInput input, Exception exception)
   {
      AlertsRuntime runtime = AlertsRuntime.find(QContext.getQInstance());
      if(runtime == null)
      {
         return;
      }
      try
      {
         QProcessMetaData process = QContext.getQInstance().getProcess(input.getProcessName());
         runtime.getEvaluator().processFailed(input.getProcessName(), process == null ? null : process.getLabel(),
            exception == null ? null : exception.getClass().getSimpleName() + ": " + exception.getMessage());
      }
      catch(Exception e)
      {
         LOG.warn("Error recording a process failure for alerts", e, logPair("processName", input.getProcessName()));
      }
   }



   /*******************************************************************************
    ** Forget the cached names (after monitors change; tests).
    *******************************************************************************/
   public static void clearCache()
   {
      watched = null;
   }



   /*******************************************************************************
    ** Process names (or * patterns) that enabled PROCESS_FAILED or process
    ** HEARTBEAT monitors watch.
    *******************************************************************************/
   private static Set<String> watched(AlertsRuntime runtime)
   {
      Set<String> current = watched;
      if(current != null && System.currentTimeMillis() - watchedAt < CACHE_MILLIS)
      {
         return (current);
      }
      Set<String> names = new HashSet<>();
      try
      {
         for(QRecord monitor : runtime.asSystem(() -> AlertsDb.query(AlertsMonitor.TABLE_NAME, AlertsDb.eq("enabled", true)
            .withCriteria("kind", QCriteriaOperator.IN, java.util.List.of(AlertMonitorKind.PROCESS_FAILED.name(), AlertMonitorKind.HEARTBEAT.name())))))
         {
            Map<String, java.io.Serializable> definition = MonitorEvaluator.definition(monitor);
            String                            name       = ValueUtils.getValueAsString(definition.get("processName"));
            if(name != null)
            {
               names.add(name);
            }
         }
      }
      catch(Exception e)
      {
         LOG.debug("Could not read process monitors", e);
      }
      watched = names;
      watchedAt = System.currentTimeMillis();
      return (names);
   }
}
