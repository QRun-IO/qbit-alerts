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

package com.kingsrook.qbits.alerts.metadata;


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.alerts.AlertsPermissionChecker;
import com.kingsrook.qbits.alerts.AlertsQBitConfig;
import com.kingsrook.qbits.alerts.processes.AlertsAction;
import com.kingsrook.qbits.alerts.processes.AlertsActionSteps;
import com.kingsrook.qbits.alerts.processes.AlertsScheduledSteps;
import com.kingsrook.qqq.backend.core.actions.processes.BackendStep;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.MetaDataProducerMultiOutput;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QBackendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QComponentType;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QFrontendComponentMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QFrontendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitComponentMetaDataProducer;
import com.kingsrook.qqq.backend.core.model.metadata.scheduleing.QScheduleMetaData;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.ExtractViaQueryStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.StreamedETLWithFrontendProcess;


/*******************************************************************************
 ** The Alerts processes. None is in any app's nav:
 ** - the record and table actions (AlertsAction), each a standard streamed
 **   ETL process on its table: optional input, Review summary lines, Result
 **   summary lines;
 ** - hidden scheduled processes: alertsEvaluateMonitors (monitors, plus
 **   snooze / quiet-period / flapping upkeep), alertsDeliverNotifications,
 **   and alertsEscalateAndRemind.
 **   They exist without a scheduler and run on one only when the config names
 **   it.
 *******************************************************************************/
public class AlertsProcessesMetaDataProducer extends QBitComponentMetaDataProducer<MetaDataProducerMultiOutput, AlertsQBitConfig>
{
   public static final String ESCALATE_AND_REMIND = "alertsEscalateAndRemind";
   public static final String EVALUATE_MONITORS     = "alertsEvaluateMonitors";
   public static final String DELIVER_NOTIFICATIONS = "alertsDeliverNotifications";



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public MetaDataProducerMultiOutput produce(QInstance qInstance) throws QException
   {
      AlertsQBitConfig            config = getQBitConfig();
      MetaDataProducerMultiOutput output = new MetaDataProducerMultiOutput();
      for(QProcessMetaData process : buildProcesses(config))
      {
         output.add(process);
      }
      return (output);
   }



   /*******************************************************************************
    ** Every process.
    *******************************************************************************/
   public static List<QProcessMetaData> buildProcesses(AlertsQBitConfig config)
   {
      List<QProcessMetaData> processes = new ArrayList<>();
      for(AlertsAction action : AlertsAction.values())
      {
         processes.add(action(action));
      }
      processes.add(scheduled(config, EVALUATE_MONITORS, "Evaluate alert monitors", AlertsScheduledSteps.EvaluateMonitors.class, config.getEvaluateEverySeconds()));
      processes.add(scheduled(config, DELIVER_NOTIFICATIONS, "Deliver alert notifications", AlertsScheduledSteps.DeliverNotifications.class, config.getDeliverEverySeconds()));
      processes.add(scheduled(config, ESCALATE_AND_REMIND, "Escalate and remind", AlertsScheduledSteps.EscalateAndRemind.class, 60));
      return (processes);
   }



   /*******************************************************************************
    ** One record / table action.
    *******************************************************************************/
   static QProcessMetaData action(AlertsAction action)
   {
      QProcessMetaData process = StreamedETLWithFrontendProcess.processMetaDataBuilder()
         .withName(action.getProcessName())
         .withLabel(action.getLabel())
         .withIcon(new QIcon(action.getIcon()))
         .withTableName(action.getTableName())
         .withSourceTable(action.getTableName())
         .withDestinationTable(action.getTableName())
         .withExtractStepClass(ExtractViaQueryStep.class)
         .withTransformStepClass(AlertsActionSteps.Transform.class)
         .withLoadStepClass(AlertsActionSteps.Load.class)
         .withSupportsFullValidation(true)
         .withDoFullValidation(true)
         .withMinInputRecords(1)
         .withMaxInputRecords(action.getMaxRecords())
         .withPreviewMessage("This is what " + action.getLabel().toLowerCase() + " will do.")
         .getProcessMetaData();
      process.setPermissionRules(AlertsPermissionChecker.rules(action.getPermissionName()));

      if(!action.inputFields().isEmpty())
      {
         process.withStep(0, new QFrontendStepMetaData()
            .withName("input")
            .withLabel(action.getLabel())
            .withComponent(new QFrontendComponentMetaData().withType(QComponentType.EDIT_FORM))
            .withFormFields(action.inputFields()));
      }
      return (process);
   }



   /*******************************************************************************
    ** A hidden process with one backend step and, with a scheduler, a repeat.
    *******************************************************************************/
   private static QProcessMetaData scheduled(AlertsQBitConfig config, String name, String label, Class<? extends BackendStep> step, Integer repeatSeconds)
   {
      QProcessMetaData process = new QProcessMetaData()
         .withName(name)
         .withLabel(label)
         .withIsHidden(true)
         .withIcon(new QIcon("schedule"))
         .withPermissionRules(AlertsPermissionChecker.rules(AlertsQBitConfig.PERMISSION_ADMIN))
         .withStep(new QBackendStepMetaData().withName("run").withCode(new QCodeReference(step)));
      if(config.getSchedulerName() != null)
      {
         process.withSchedule(new QScheduleMetaData()
            .withSchedulerName(config.getSchedulerName())
            .withRepeatSeconds(repeatSeconds)
            .withInitialDelaySeconds(repeatSeconds));
      }
      return (process);
   }



   /*******************************************************************************
    ** after the tables
    *******************************************************************************/
   @Override
   public int getSortOrder()
   {
      return (200);
   }
}
