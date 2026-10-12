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


/*******************************************************************************
 ** The outcome of one delivery attempt.
 **
 ** @param outcome           SENT, RETRY (try again later) or FAILED (permanent)
 ** @param providerMessageId the provider's id for the message, when it has one
 ** @param error             why it did not send (already redacted)
 *******************************************************************************/
public record DeliveryResult(Outcome outcome, String providerMessageId, String error)
{
   /*******************************************************************************
    ** Delivery outcomes.
    *******************************************************************************/
   public enum Outcome
   {
      SENT, RETRY, FAILED
   }



   /*******************************************************************************
    ** Sent.
    *******************************************************************************/
   public static DeliveryResult sent(String providerMessageId)
   {
      return (new DeliveryResult(Outcome.SENT, providerMessageId, null));
   }



   /*******************************************************************************
    ** Failed for now; try again.
    *******************************************************************************/
   public static DeliveryResult retry(String error)
   {
      return (new DeliveryResult(Outcome.RETRY, null, error));
   }



   /*******************************************************************************
    ** Failed for good (bad address, channel not configured).
    *******************************************************************************/
   public static DeliveryResult failed(String error)
   {
      return (new DeliveryResult(Outcome.FAILED, null, error));
   }
}
