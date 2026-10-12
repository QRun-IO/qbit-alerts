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


import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.alerts.AlertsQBitConfig;
import com.kingsrook.qbits.alerts.BaseAlertsTest;
import com.kingsrook.qbits.alerts.model.AlertsAlert;
import com.kingsrook.qbits.alerts.model.AlertsType;
import com.kingsrook.qqq.backend.core.exceptions.QAuthenticationException;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QNotFoundException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Alertmanager as a source: authentication, firing and resolved alerts, the
 ** EXTERNAL type with notifications off, and the loop guard.
 *******************************************************************************/
class AlertmanagerIngestTest extends BaseAlertsTest
{
   static final String TOKEN_PROPERTY = "alerts.test.ingest.token";



   /*******************************************************************************
    **
    *******************************************************************************/
   @BeforeAll
   static void setToken()
   {
      System.setProperty(TOKEN_PROPERTY, "s3cret-ingest");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @AfterAll
   static void clearToken()
   {
      System.clearProperty(TOKEN_PROPERTY);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   protected void customizeConfig(AlertsQBitConfig config)
   {
      config.withAlertmanagerIngestTokenSecretRef("${prop." + TOKEN_PROPERTY + "}");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Map<String, Object> alert(String status, String alertname, String fingerprint, Map<String, Object> extraLabels)
   {
      Map<String, Object> labels = new java.util.LinkedHashMap<>(Map.of("alertname", alertname, "severity", "critical", "instance", "node-1"));
      labels.putAll(extraLabels);
      return (Map.of("status", status, "labels", labels, "annotations", Map.of("summary", "CPU high on node-1", "description", "Over 90% for 10 minutes."),
         "startsAt", "2026-10-12T11:00:00Z", "fingerprint", fingerprint));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testAuthentication() throws Exception
   {
      AlertmanagerIngest ingest = new AlertmanagerIngest(runtime);
      ingest.authenticate("Bearer s3cret-ingest");
      assertThatThrownBy(() -> ingest.authenticate("Bearer nope")).isInstanceOf(QAuthenticationException.class);
      assertThatThrownBy(() -> ingest.authenticate(null)).isInstanceOf(QAuthenticationException.class);

      config.withAlertmanagerIngestTokenSecretRef(null);
      assertThatThrownBy(() -> ingest.authenticate("Bearer s3cret-ingest")).isInstanceOf(QNotFoundException.class);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testFiringResolvedAndLoopGuard() throws Exception
   {
      AlertmanagerIngest ingest = new AlertmanagerIngest(runtime);
      AlertmanagerIngest.Result first = ingest.ingest(Map.of("version", "4", "alerts", List.of(
         alert("firing", "HighCPU Usage", "abc123", Map.of()),
         alert("firing", "AlertsFromQqq", "def456", Map.of("qqq_origin", "qbit-alerts")),
         "not an alert")));
      assertThat(first).isEqualTo(new AlertmanagerIngest.Result(1, 0, 2));

      QRecord type = runtime.asSystem(() -> AlertsDb.first(AlertsType.TABLE_NAME, AlertsDb.eq("name", "prometheus.highcpu-usage")));
      assertThat(type.getValueBoolean("notify")).isFalse();
      assertThat(type.getValueString("sourceName")).isEqualTo("prometheus");
      QRecord alert = all(AlertsAlert.TABLE_NAME).get(0);
      assertThat(alert.getValueString("severity")).isEqualTo("CRITICAL");
      assertThat(alert.getValueString("title")).isEqualTo("CPU high on node-1");
      assertThat(alert.getValueString("dedupKey")).isEqualTo("abc123");
      assertThat(alert.getValueString("attributes")).contains("instance");

      AlertmanagerIngest.Result second = ingest.ingest(Map.of("alerts", List.of(alert("resolved", "HighCPU Usage", "abc123", Map.of()))));
      assertThat(second.resolved()).isEqualTo(1);
      assertThat(all(AlertsAlert.TABLE_NAME).get(0).getValueString("status")).isEqualTo("RESOLVED");

      assertThatThrownBy(() -> ingest.ingest(Map.of())).isInstanceOf(QBadRequestException.class);
      assertThat(AlertmanagerIngest.typeName("!!")).isEqualTo("prometheus.unnamed");
      assertThat(AlertmanagerIngest.fingerprint(Map.of("a", "b"))).isNotBlank();
   }
}
