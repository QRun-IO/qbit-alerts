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


package com.kingsrook.qbits.alerts.notifications;


import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.alerts.AlertsSecurity;
import com.kingsrook.qbits.alerts.BaseAlertsTest;
import com.kingsrook.qbits.alerts.TestSteps;
import com.kingsrook.qbits.alerts.api.AlertsApi;
import com.kingsrook.qbits.alerts.engine.AlertsDb;
import com.kingsrook.qbits.alerts.engine.AlertsProcessListener;
import com.kingsrook.qbits.alerts.model.AlertsNotification;
import com.kingsrook.qqq.backend.core.actions.processes.RunProcessAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLine;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLineInterface;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessOutput;
import com.kingsrook.qqq.backend.core.model.actions.processes.Status;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.StreamedETLWithFrontendProcess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Job notifications: a person's failed or partial process runs reach their
 ** bell (docs/NOTIFICATIONS.md 6.1).
 *******************************************************************************/
class JobNotificationsTest extends BaseAlertsTest
{
   /*******************************************************************************
    **
    *******************************************************************************/
   @AfterEach
   void resetFlaky()
   {
      TestSteps.fail = false;
   }



   /*******************************************************************************
    ** Run the test process as the current session.
    *******************************************************************************/
   private RunProcessOutput runNightly() throws Exception
   {
      return (new RunProcessAction().execute(new RunProcessInput().withProcessName("nightlyThing").withFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.SKIP)));
   }



   /*******************************************************************************
    ** A failed run notifies the person who started it, once per run.
    *******************************************************************************/
   @Test
   void testFailedRunNotifiesStarter() throws Exception
   {
      TestSteps.fail = true;
      loginAs("viewer-1");
      try
      {
         runNightly();
      }
      catch(Exception e)
      {
         ////////////////////////////////////////
         // the run's own failure is expected //
         ////////////////////////////////////////
      }

      List<QRecord> notifications = runtime.asSystem(() -> all(AlertsNotification.TABLE_NAME));
      assertThat(notifications).singleElement().satisfies(notification ->
      {
         assertThat(notification.getValueString("title")).isEqualTo("Nightly thing failed");
         assertThat(notification.getValueString("body")).contains("disk full");
         assertThat(notification.getValueString("category")).isEqualTo("JOB");
         assertThat(notification.getValueString("severity")).isEqualTo("ERROR");
         assertThat(notification.getValueString("sourceKey")).startsWith("process:");
         assertThat(notification.getValueString("audienceValue")).isEqualTo("viewer-1");
      });

      Map<String, Object> summary = new AlertsApi(runtime).inboxSummary();
      assertThat(summary.get("unreadCount")).isEqualTo(1);

      //////////////////////////////////////////////////////////////
      // system runs and a successful run notify nobody           //
      //////////////////////////////////////////////////////////////
      QContext.init(qInstance, AlertsSecurity.systemSession());
      try
      {
         runNightly();
      }
      catch(Exception e)
      {
         ////////////////////////////////////////
         // the run's own failure is expected //
         ////////////////////////////////////////
      }
      TestSteps.fail = false;
      loginAs("viewer-1");
      runNightly();
      assertThat(runtime.asSystem(() -> all(AlertsNotification.TABLE_NAME))).hasSize(1);
   }



   /*******************************************************************************
    ** A run the person watches on the process screens (BREAK) already shows
    ** its result, so it sends nothing.
    *******************************************************************************/
   @Test
   void testInteractiveRunNotifiesNobody() throws Exception
   {
      TestSteps.fail = true;
      loginAs("viewer-1");
      try
      {
         new RunProcessAction().execute(new RunProcessInput().withProcessName("nightlyThing").withFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.BREAK));
      }
      catch(Exception e)
      {
         ////////////////////////////////////////
         // the run's own failure is expected //
         ////////////////////////////////////////
      }
      assertThat(runtime.asSystem(() -> all(AlertsNotification.TABLE_NAME))).isEmpty();

      assertThat(AlertsProcessListener.isInteractive(null)).isFalse();
      assertThat(AlertsProcessListener.isInteractive(new RunProcessInput())).isTrue();
      assertThat(AlertsProcessListener.isInteractive(new RunProcessInput().withFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.SKIP))).isFalse();
      assertThat(AlertsProcessListener.isInteractive(new RunProcessInput().withFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.FAIL))).isFalse();
   }



   /*******************************************************************************
    ** Turned off, nothing is sent.
    *******************************************************************************/
   @Test
   void testOff() throws Exception
   {
      config.setNotifyFailedJobs(false);
      TestSteps.fail = true;
      loginAs("viewer-1");
      try
      {
         runNightly();
      }
      catch(Exception e)
      {
         ////////////////////////////////////////
         // the run's own failure is expected //
         ////////////////////////////////////////
      }
      assertThat(runtime.asSystem(() -> AlertsDb.count(AlertsNotification.TABLE_NAME, new com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter()))).isEqualTo(0);
   }



   /*******************************************************************************
    ** Partial runs: ERROR summary lines count; links follow the pattern.
    *******************************************************************************/
   @Test
   void testPartialRunsAndLinks()
   {
      RunProcessOutput output = new RunProcessOutput();
      assertThat(AlertsProcessListener.errorCount(output)).isEqualTo(0);
      ArrayList<ProcessSummaryLineInterface> lines = new ArrayList<>();
      lines.add(new ProcessSummaryLine(Status.OK, 10, "rows were loaded"));
      lines.add(new ProcessSummaryLine(Status.ERROR, 3, "rows had bad dates"));
      output.addValue(StreamedETLWithFrontendProcess.FIELD_PROCESS_SUMMARY, lines);
      assertThat(AlertsProcessListener.errorCount(output)).isEqualTo(3);
      assertThat(AlertsProcessListener.errorCount(null)).isEqualTo(0);

      assertThat(AlertsProcessListener.jobLink(runtime, "p", "u-1", "widget").getPath()).isEqualTo("/app/widget");
      assertThat(AlertsProcessListener.jobLink(runtime, "p", "u-1", null)).isNull();
      config.setJobRunLinkPattern("/runs/{processName}/{processUUID}");
      assertThat(AlertsProcessListener.jobLink(runtime, "p", "u-1", null).getPath()).isEqualTo("/runs/p/u-1");
   }
}
