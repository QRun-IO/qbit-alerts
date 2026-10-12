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


import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.kingsrook.qbits.alerts.api.AlertsRouteProvider;
import com.kingsrook.qbits.alerts.metadata.AlertsAppMetaDataProducer;
import com.kingsrook.qbits.alerts.metadata.AlertsHomeWidgetsMetaDataProducer;
import com.kingsrook.qbits.alerts.metadata.AlertsProcessesMetaDataProducer;
import com.kingsrook.qbits.alerts.processes.AlertsAction;
import com.kingsrook.qbits.alerts.spi.AlertsInstanceMetaData;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.instances.QInstanceEnricher;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppChildMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.middleware.javalin.QJavalinMetaData;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** The producer and the owner's standards: naming, the app's nav (main tables
 ** only, no processes), full-width home widgets, the changelog matching the
 ** tables, the qbit's own alert types, permissions and the routes.
 *******************************************************************************/
class AlertsQBitProducerTest extends BaseAlertsTest
{

   /*******************************************************************************
    ** The produced instance validated (in the base) and registered the qbit.
    *******************************************************************************/
   @Test
   void testProduce()
   {
      assertThat(qInstance.getQBits()).hasSize(1);
      assertThat(qInstance.getQBits().values().iterator().next().getArtifactId()).isEqualTo(AlertsQBitProducer.ARTIFACT_ID);
      assertThat(AlertsRuntime.find(qInstance)).isSameAs(runtime);
      assertThat(AlertsRuntime.find(new QInstance())).isNull();
      assertThatThrownBy(() -> AlertsRuntime.get(new QInstance())).hasMessageContaining("not installed");
      assertThat(AlertsInstanceMetaData.of(qInstance).getAlertTypes()).containsKeys("alerts.monitor.failing", "alerts.channel.failing", "alerts.sink.overflow", "alerts.producer.flood");
      assertThat(qInstance.getProcessLifecycleListeners()).anyMatch(ref -> ref.getName().endsWith("AlertsProcessListener"));
   }



   /*******************************************************************************
    ** Without a config the producer is disabled; bad configs fail.
    *******************************************************************************/
   @Test
   void testDisabledAndInvalid()
   {
      assertThat(new AlertsQBitProducer().isEnabled()).isFalse();
      assertThatThrownBy(() -> new AlertsQBitProducer().produce(new QInstance())).isInstanceOf(QException.class);

      AlertsQBitConfig noBackend = new AlertsQBitConfig();
      noBackend.setBackendName(null);
      assertThatThrownBy(() -> new AlertsQBitProducer().withQBitConfig(noBackend).produce(new QInstance())).hasMessageContaining("backendName is required");
      assertThatThrownBy(() -> new AlertsQBitProducer().withQBitConfig(new AlertsQBitConfig().withBackendName("nope")).produce(new QInstance())).hasMessageContaining("backend not found");
      assertThatThrownBy(() -> new AlertsQBitProducer().withQBitConfig(new AlertsQBitConfig().withBackendName(BACKEND_NAME).withSchedulerName("nope").withMessagingProviderName("nope")
         .withFlapThreshold(0)).produce(defineMinimal())).hasMessageContaining("scheduler not found").hasMessageContaining("messaging provider not found").hasMessageContaining("positive");
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static QInstance defineMinimal()
   {
      QInstance minimal = new QInstance();
      minimal.addBackend(defineH2Backend());
      return (minimal);
   }



   /*******************************************************************************
    ** Owner naming rule: lowercase snake_case tables with the full qbit name;
    ** processes and widgets use the full "alerts" prefix.
    *******************************************************************************/
   @Test
   void testNaming()
   {
      assertThat(AlertsQBitConfig.TABLE_NAME_PREFIX).isEqualTo("alerts_");
      for(String table : AlertsQBitConfig.allTableNames())
      {
         assertThat(table).matches("^alerts_[a-z][a-z0-9_]*$");
         assertThat(qInstance.getTable(table)).as(table).isNotNull();
      }
      for(QProcessMetaData process : AlertsProcessesMetaDataProducer.buildProcesses(config))
      {
         assertThat(process.getName()).startsWith("alerts");
      }
      for(String widget : AlertsHomeWidgetsMetaDataProducer.homeWidgetNames())
      {
         assertThat(widget).startsWith("alertsHome");
      }
   }



   /*******************************************************************************
    ** The app: top level, home widgets full width, nav = main tables only.
    *******************************************************************************/
   @Test
   void testAppAndNav()
   {
      QAppMetaData app = qInstance.getApp(AlertsQBitConfig.APP_NAME);
      assertThat(app.getLabel()).isEqualTo("Alerts");
      assertThat(app.getWidgets()).containsExactlyElementsOf(AlertsHomeWidgetsMetaDataProducer.homeWidgetNames());
      List<String> children = app.getChildren().stream().map(QAppChildMetaData::getName).toList();
      assertThat(children).containsExactlyElementsOf(AlertsAppMetaDataProducer.MAIN_TABLES);
      assertThat(children).doesNotContain("alerts_event", "alerts_record_ref", "alerts_inbox_item", "alerts_team_member", "alerts_heartbeat");
      for(QAppChildMetaData child : app.getChildren())
      {
         assertThat(qInstance.getProcess(child.getName())).as("no process in the nav").isNull();
      }
      for(String name : AlertsHomeWidgetsMetaDataProducer.homeWidgetNames())
      {
         QWidgetMetaData widget = (QWidgetMetaData) qInstance.getWidget(name);
         assertThat(widget.getGridColumns()).as(name).isEqualTo(12);
      }
      for(AlertsAction action : AlertsAction.values())
      {
         QProcessMetaData process = qInstance.getProcess(action.getProcessName());
         assertThat(process.getTableName()).isEqualTo(action.getTableName());
         assertThat(process.getFrontendStep("review")).as("standard review step").isNotNull();
         assertThat(process.getFrontendStep("result")).as("standard result step").isNotNull();
      }
      assertThat(qInstance.getProcess(AlertsProcessesMetaDataProducer.EVALUATE_MONITORS).getIsHidden()).isTrue();
   }



   /*******************************************************************************
    ** The bundled changelog creates every column of every table.
    *******************************************************************************/
   @Test
   void testChangelogMatchesTables() throws Exception
   {
      String changelog;
      try(InputStream in = getClass().getClassLoader().getResourceAsStream("db/liquibase/qbit-alerts/alerts.changelog.sql"))
      {
         changelog = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
      for(String tableName : AlertsQBitConfig.allTableNames())
      {
         int            start = changelog.indexOf("CREATE TABLE IF NOT EXISTS " + tableName + " (");
         String         body  = changelog.substring(start, changelog.indexOf(");", start));
         QTableMetaData table = qInstance.getTable(tableName);
         for(QFieldMetaData field : table.getFields().values())
         {
            assertThat(body).as(tableName + "." + field.getName()).contains("   " + QInstanceEnricher.inferBackendName(field.getName()) + " ");
         }
      }
   }



   /*******************************************************************************
    ** Permissions: the three alerts permissions are listed; role lists nest.
    *******************************************************************************/
   @Test
   void testPermissions()
   {
      Set<String> available = PermissionsHelper.getAllAvailablePermissionNames(qInstance).stream().collect(Collectors.toSet());
      assertThat(available).contains(AlertsQBitConfig.PERMISSION_VIEW, AlertsQBitConfig.PERMISSION_MANAGE, AlertsQBitConfig.PERMISSION_ADMIN, "alerts_alert.read");
      assertThat(config.getManagerPermissionNames()).containsAll(config.getViewerPermissionNames()).contains(AlertsQBitConfig.PERMISSION_MANAGE);
      assertThat(config.getAdminPermissionNames()).containsAll(config.getManagerPermissionNames());
      assertThat(config.getViewerPermissionNames()).doesNotContain(AlertsQBitConfig.PERMISSION_ADMIN, "alerts_channel.read", "alerts_notification.read");
   }



   /*******************************************************************************
    ** The routes register once; integrations name the main tables.
    *******************************************************************************/
   @Test
   void testRoutesAndIntegrations()
   {
      assertThat(QJavalinMetaData.of(qInstance).getAdditionalRouteProviderReferences()).filteredOn(ref -> ref.getName().equals(AlertsRouteProvider.class.getName())).hasSize(1);
      AlertsRouteProvider.register(qInstance);
      assertThat(QJavalinMetaData.of(qInstance).getAdditionalRouteProviderReferences()).filteredOn(ref -> ref.getName().equals(AlertsRouteProvider.class.getName())).hasSize(1);
      assertThat(AlertsIntegrations.esbTableNames()).containsExactly("alerts_alert", "alerts_monitor", "alerts_type");
      assertThat(AlertsIntegrations.quickSearchTables()).hasSize(3);
   }
}
