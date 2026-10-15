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


import java.nio.file.Files;
import java.nio.file.Path;
import com.kingsrook.qbits.alerts.api.AlertsUiApi;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.middleware.javalin.specs.extensions.MiddlewareSpecExtensionsMetaData;
import com.kingsrook.qqq.middleware.javalin.specs.v1.MiddlewareVersionV1;
import com.kingsrook.qqq.middleware.javalin.tools.UiApiContract;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The committed extension fragment describes exactly the six UI operations.
 *******************************************************************************/
class AlertsApiContractTest extends BaseAlertsTest
{
   /*******************************************************************************
    ** Regenerate only by explicit build property, then check the deterministic fragment.
    *******************************************************************************/
   @Test
   void contract() throws Exception
   {
      var extension = AlertsUiApi.extension();
      var version = new MiddlewareVersionV1();
      String generated = UiApiContract.generateExtensionYaml(version, extension);
      Path path = Path.of("src/main/resources/openapi/qqq-v1/alerts.yaml");
      if(Boolean.getBoolean("alerts.openapi.update"))
      {
         Files.createDirectories(path.getParent());
         Files.writeString(path, generated);
      }
      assertThat(UiApiContract.check(generated, Files.readString(path), null, 1,
         "mvnw.cmd -Dtest=AlertsApiContractTest -Dalerts.openapi.update=true test")).isEmpty();
      var model = version.generateExtensionOpenAPIModel("qqq", extension);
      assertThat(model.getComponents().getSchemas().keySet()).allMatch(name -> name.startsWith("Alerts") || name.equals("BasicErrorResponseV1"));
      assertThat(model.getPaths().keySet()).containsExactlyInAnyOrder(
         "/qqq/v1/alerts/inbox/summary", "/qqq/v1/alerts/inbox", "/qqq/v1/alerts/inbox/read",
         "/qqq/v1/alerts/inbox/unread", "/qqq/v1/alerts/inbox/dismiss", "/qqq/v1/alerts/record-panel");
      assertThat(extension.getSpecs()).hasSize(6);
      assertThat(extension.getSemanticVersion()).isEqualTo("1.2.0");
      assertThat(generated).contains("alertsInboxSummary", "alertsInboxRead", "alertsInboxUnread", "alertsInboxDismiss", "alertsRecordPanel", "inboxHref", "preferencesSection", "actions", "follow");
      assertThat(generated).doesNotContain("/watch", "/ingest/", "/hooks/");
   }



   /*******************************************************************************
    ** Production registers one extension; the existing route opt-out still works.
    *******************************************************************************/
   @Test
   void registration() throws Exception
   {
      assertThat(MiddlewareSpecExtensionsMetaData.of(qInstance).getExtensions()).containsOnlyKeys("alerts");
      AlertsUiApi.register(qInstance);
      assertThat(MiddlewareSpecExtensionsMetaData.of(qInstance).getExtensions()).hasSize(1);
      assertThat(AlertsUiApi.extension().getEffectivePathPrefix()).isEqualTo("/alerts");
      QInstance disabled = new QInstance();
      disabled.addBackend(defineH2Backend());
      new AlertsQBitProducer().withQBitConfig(new AlertsQBitConfig().withBackendName(BACKEND_NAME).withRegisterRoutes(false)).produce(disabled);
      assertThat(MiddlewareSpecExtensionsMetaData.of(disabled)).isNull();
      assertThat(new AlertsQBitConfig().getLegacyAlertmanagerPath()).isFalse();
   }
}
