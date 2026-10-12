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

package com.kingsrook.qbits.alerts.processes;


import com.kingsrook.qbits.alerts.AlertsRuntime;
import com.kingsrook.qqq.backend.core.actions.processes.BackendStep;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;


/*******************************************************************************
 ** The hidden scheduled processes' steps: evaluate due monitors (plus snooze,
 ** quiet-period and flapping upkeep), and deliver due notifications.
 *******************************************************************************/
public final class AlertsScheduledSteps
{
   public static final String OUTPUT_COUNT = "count";



   /*******************************************************************************
    ** Steps only.
    *******************************************************************************/
   private AlertsScheduledSteps()
   {
   }



   /*******************************************************************************
    ** alertsEvaluateMonitors
    *******************************************************************************/
   public static class EvaluateMonitors implements BackendStep
   {
      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         output.addValue(OUTPUT_COUNT, AlertsRuntime.get().getEvaluator().evaluateDue());
      }
   }



   /*******************************************************************************
    ** alertsDeliverNotifications
    *******************************************************************************/
   public static class DeliverNotifications implements BackendStep
   {
      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         AlertsRuntime runtime = AlertsRuntime.get();
         runtime.sink().flush();
         output.addValue(OUTPUT_COUNT, runtime.getDeliveryWorker().deliverDue());
      }
   }
}
