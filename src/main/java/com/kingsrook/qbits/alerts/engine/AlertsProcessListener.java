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
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.alerts.AlertsQBitConfig;
import com.kingsrook.qbits.alerts.AlertsRuntime;
import com.kingsrook.qbits.alerts.AlertsSecurity;
import com.kingsrook.qbits.alerts.model.AlertsMonitor;
import com.kingsrook.qbits.alerts.spi.AlertLink;
import com.kingsrook.qbits.alerts.spi.NotificationAudience;
import com.kingsrook.qbits.alerts.spi.NotificationCategory;
import com.kingsrook.qbits.alerts.spi.NotificationSeverity;
import com.kingsrook.qbits.alerts.spi.NotificationSignal;
import com.kingsrook.qqq.backend.core.actions.processes.listeners.ProcessLifecycleListenerInterface;
import com.kingsrook.qqq.backend.core.alerts.AlertMonitorKind;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLine;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLineInterface;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessOutput;
import com.kingsrook.qqq.backend.core.model.actions.processes.Status;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.session.QSystemUserSession;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.StreamedETLWithFrontendProcess;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qqq.backend.core.utils.ValueUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Feeds alerts and notifications from QQQ's process lifecycle events (no ESB
 ** needed). Never affects the process.
 ** - PROCESS_FAILED and process HEARTBEAT monitors: a failed run raises, a
 **   completed run resolves and beats the process's heartbeat key. Only
 **   processes some monitor watches are handled (the watched names are
 **   cached for a minute).
 ** - Job notifications (docs/NOTIFICATIONS.md 6.1): when a run started by a
 **   person fails, or completes with error summary lines (a partial run),
 **   that person gets a JOB notification linked to the run. System runs and
 **   the qbit's own processes are skipped. Off with notifyFailedJobs(false).
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
      return (notifiesJobs(runtime, processName) || isWatched(runtime, processName));
   }



   /*******************************************************************************
    ** Whether a monitor watches this process.
    *******************************************************************************/
   private static boolean isWatched(AlertsRuntime runtime, String processName)
   {
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
         if(isWatched(runtime, input.getProcessName()))
         {
            runtime.getEngine().heartbeat(MonitorEvaluator.processHeartbeatKey(input.getProcessName()), runtime.now());
            runtime.getEvaluator().processCompleted(input.getProcessName());
         }
         int errors = errorCount(output);
         if(errors > 0 && notifiesJobs(runtime, input.getProcessName()))
         {
            notifyStarter(runtime, input, NotificationSeverity.WARNING, "%s finished with errors",
               (errors == 1 ? "1 record" : errors + " records") + " had errors. Open the run's results for the details.");
         }
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
         if(isWatched(runtime, input.getProcessName()))
         {
            QProcessMetaData process = QContext.getQInstance().getProcess(input.getProcessName());
            runtime.getEvaluator().processFailed(input.getProcessName(), process == null ? null : process.getLabel(),
               exception == null ? null : exception.getClass().getSimpleName() + ": " + exception.getMessage());
         }
         if(notifiesJobs(runtime, input.getProcessName()))
         {
            String message = exception == null || exception.getMessage() == null ? "It stopped with an error." : exception.getMessage();
            notifyStarter(runtime, input, NotificationSeverity.ERROR, "%s failed", StringUtils.safeTruncate(message, 1000));
         }
      }
      catch(Exception e)
      {
         LOG.warn("Error recording a process failure for alerts", e, logPair("processName", input.getProcessName()));
      }
   }



   /*******************************************************************************
    ** Whether runs of this process notify the person who started them.
    *******************************************************************************/
   private static boolean notifiesJobs(AlertsRuntime runtime, String processName)
   {
      return (Boolean.TRUE.equals(runtime.getConfig().getNotifyFailedJobs()) && !processName.startsWith(AlertsQBitConfig.PERMISSION_BASE_NAME));
   }



   /*******************************************************************************
    ** How many records a completed run reported as errors (its ERROR summary
    ** lines), or 1 when the output carries an exception.
    *******************************************************************************/
   public static int errorCount(RunProcessOutput output)
   {
      if(output == null)
      {
         return (0);
      }
      if(output.getException().isPresent())
      {
         return (1);
      }
      int    errors  = 0;
      Object summary = output.getValues() == null ? null : output.getValues().get(StreamedETLWithFrontendProcess.FIELD_PROCESS_SUMMARY);
      if(summary instanceof List<?> lines)
      {
         for(Object line : lines)
         {
            if(line instanceof ProcessSummaryLineInterface summaryLine && summaryLine.getStatus() == Status.ERROR)
            {
               Integer count = line instanceof ProcessSummaryLine counted ? counted.getCount() : null;
               errors += count == null || count < 1 ? 1 : count;
            }
         }
      }
      return (errors);
   }



   /*******************************************************************************
    ** Send a JOB notification to the person who started the run (nothing for
    ** system runs or sessions without a user).
    *******************************************************************************/
   private static void notifyStarter(AlertsRuntime runtime, RunProcessInput input, NotificationSeverity severity, String titleFormat, String body)
   {
      QSession session = QContext.getQSession();
      String   userId  = session == null || session instanceof QSystemUserSession ? null : AlertsSecurity.userId(session);
      if(userId == null)
      {
         return;
      }
      QProcessMetaData process   = QContext.getQInstance().getProcess(input.getProcessName());
      String           label     = process == null || process.getLabel() == null ? input.getProcessName() : process.getLabel();
      String           tableName = process == null ? null : process.getTableName();
      runtime.sink().notify(new NotificationSignal(String.format(titleFormat, label))
         .withBody(body)
         .withSeverity(severity)
         .withCategory(NotificationCategory.JOB)
         .withSourceName("processes")
         .withSourceKey("process:" + input.getProcessUUID())
         .withSourceTableName(tableName)
         .withLink(jobLink(runtime, input.getProcessName(), input.getProcessUUID(), tableName))
         .withAudience(NotificationAudience.user(userId)));
   }



   /*******************************************************************************
    ** Where a job notification links: the configured run link pattern, else
    ** the process's table, else nowhere.
    *******************************************************************************/
   public static AlertLink jobLink(AlertsRuntime runtime, String processName, String processUUID, String tableName)
   {
      String pattern = runtime.getConfig().getJobRunLinkPattern();
      if(StringUtils.hasContent(pattern))
      {
         return (AlertLink.toPath(pattern.replace("{processName}", processName).replace("{processUUID}", processUUID == null ? "" : processUUID), "Open the run"));
      }
      return (tableName == null ? null : AlertLink.toPath("/app/" + tableName, "Open " + tableName));
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
