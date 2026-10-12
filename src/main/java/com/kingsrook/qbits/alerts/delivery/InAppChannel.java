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


import java.util.List;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.ChannelType;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** The bell. The router delivers in-app notifications itself, by writing the
 ** user's alerts_inbox_item, so there is nothing left to send here; a pending
 ** in-app row (a retried one, or a test) counts as sent.
 *******************************************************************************/
public class InAppChannel implements AlertChannelInterface
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public String getType()
   {
      return (ChannelType.IN_APP.name());
   }



   /*******************************************************************************
    ** Always available.
    *******************************************************************************/
   @Override
   public List<String> validate(QRecord channel, AlertSecretResolver secrets)
   {
      return (List.of());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public DeliveryResult deliver(AlertMessage message, QRecord channel, String recipientAddress, AlertSecretResolver secrets)
   {
      return (DeliveryResult.sent(null));
   }
}
