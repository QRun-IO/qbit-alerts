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


import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.serviceidentity.QDelegatedSession;
import com.kingsrook.qqq.backend.core.serviceidentity.QServicePrincipalMetaData;
import com.kingsrook.qqq.backend.core.serviceidentity.ServiceIdentities;
import com.kingsrook.qqq.backend.core.serviceidentity.ServiceIdentitiesMetaData;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Per-recipient checks run on the recipient's behalf through the alerts
 ** service principal when the host declares it; otherwise unchanged.
 *******************************************************************************/
class AlertsServicePrincipalTest extends BaseAlertsTest
{
   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void recipientSessionsGoThroughTheDeclaredPrincipal() throws Exception
   {
      QSession before = AlertsSecurity.recipientSession(qInstance, config.getServicePrincipal(), "alice");
      assertThat(before).isNotInstanceOf(QDelegatedSession.class);
      assertThat(before.getUser().getIdReference()).isEqualTo("alice");

      ServiceIdentitiesMetaData.declare(qInstance, new QServicePrincipalMetaData().withName("alerts").withMayActOnBehalfOfUsers(true));
      QSession after = AlertsSecurity.recipientSession(qInstance, config.getServicePrincipal(), "alice");
      assertThat(after).isInstanceOf(QDelegatedSession.class);
      assertThat(after.getUser().getIdReference()).isEqualTo("alice");
      assertThat(ServiceIdentities.principalOf(after)).isEqualTo("alerts");
      assertThat(ServiceIdentities.attributionName(after)).endsWith("via service:alerts");
   }
}
