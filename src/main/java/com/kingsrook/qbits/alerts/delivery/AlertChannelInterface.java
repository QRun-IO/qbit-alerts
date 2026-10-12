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
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** A channel type (design section 5.6): in-app, email, an email list, and
 ** later Slack, Teams, webhooks, PagerDuty and Alertmanager. Register more
 ** with AlertsQBitConfig.withChannelType, so another qbit can add one without
 ** touching this one.
 **
 ** The message is already rendered and redacted for the channel's detail
 ** level; a channel never sees an alert's raw details. Secrets come only from
 ** the channel's secretRef, resolved at send time.
 *******************************************************************************/
public interface AlertChannelInterface
{
   /***************************************************************************
    ** The channel type this implements (an alerts_channel channelType).
    ***************************************************************************/
   String getType();

   /***************************************************************************
    ** Problems with a channel row's settings (empty when it can send).
    ***************************************************************************/
   List<String> validate(QRecord channel, AlertSecretResolver secrets);

   /***************************************************************************
    ** Send one message to one recipient (an address for email; null for
    ** channels that post to a fixed place). Never throws: the result says
    ** SENT, RETRY or FAILED (permanent).
    ***************************************************************************/
   DeliveryResult deliver(AlertMessage message, QRecord channel, String recipientAddress, AlertSecretResolver secrets);

}
