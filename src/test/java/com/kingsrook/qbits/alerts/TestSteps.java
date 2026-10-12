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


import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.alerts.delivery.AlertChannelInterface;
import com.kingsrook.qbits.alerts.delivery.AlertMessage;
import com.kingsrook.qbits.alerts.delivery.AlertSecretResolver;
import com.kingsrook.qbits.alerts.delivery.DeliveryResult;
import com.kingsrook.qbits.alerts.engine.AlertCheckInterface;
import com.kingsrook.qbits.alerts.spi.AlertSignal;
import com.kingsrook.qqq.backend.core.actions.processes.BackendStep;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** Test doubles: a process step that fails on demand, a recording chat-like
 ** channel, and code checks.
 *******************************************************************************/
public final class TestSteps
{
   public static volatile boolean fail = false;

   public static final List<AlertMessage> SENT = new ArrayList<>();

   public static volatile DeliveryResult.Outcome nextOutcome = DeliveryResult.Outcome.SENT;

   public static volatile List<AlertSignal> checkSignals = new ArrayList<>();



   /*******************************************************************************
    **
    *******************************************************************************/
   private TestSteps()
   {
   }



   /*******************************************************************************
    ** Fails when fail is set.
    *******************************************************************************/
   public static class Flaky implements BackendStep
   {
      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public void run(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         if(fail)
         {
            throw (new QException("disk full on host 10.1.2.3"));
         }
      }
   }



   /*******************************************************************************
    ** A "chat" channel that records messages and returns nextOutcome.
    *******************************************************************************/
   public static class RecordingChannel implements AlertChannelInterface
   {
      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public String getType()
      {
         return ("SLACK");
      }



      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public List<String> validate(QRecord channel, AlertSecretResolver secrets)
      {
         return (List.of());
      }



      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public DeliveryResult deliver(AlertMessage message, QRecord channel, String recipientAddress, AlertSecretResolver secrets)
      {
         SENT.add(message);
         return switch(nextOutcome)
         {
            case SENT -> DeliveryResult.sent("msg-" + SENT.size());
            case RETRY -> DeliveryResult.retry("503 from the chat service");
            case FAILED -> DeliveryResult.failed("404 channel not found");
         };
      }
   }



   /*******************************************************************************
    ** A code check returning checkSignals.
    *******************************************************************************/
   public static class Check implements AlertCheckInterface
   {
      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public List<AlertSignal> check(Context context)
      {
         return (new ArrayList<>(checkSignals));
      }
   }



   /*******************************************************************************
    ** A code check that throws.
    *******************************************************************************/
   public static class BrokenCheck implements AlertCheckInterface
   {
      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public List<AlertSignal> check(Context context) throws QException
      {
         throw (new QException("broker unreachable at " + Instant.EPOCH));
      }
   }
}
