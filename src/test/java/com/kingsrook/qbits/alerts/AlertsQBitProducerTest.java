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
import com.kingsrook.qbits.alerts.metadata.AlertsTableMetaDataHelper;
import com.kingsrook.qbits.alerts.processes.AlertsAction;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper;
import com.kingsrook.qqq.backend.core.alerts.AlertsInstanceMetaData;
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
    ** Team and member screens explain missing host setup only when needed.
    *******************************************************************************/
   @Test
   void testTeamResolverHelp() throws Exception
   {
      assertThat(AlertsTableMetaDataHelper.buildTeamTable(config).getField("memberSource").getHelpContents())
         .anyMatch(help -> help.getContent().contains("ROLE and IdP Group members need a host resolver; none is configured"));
      assertThat(AlertsTableMetaDataHelper.buildTeamMemberTable(config).getField("teamId").getHelpContents())
         .anyMatch(help -> help.getContent().contains("none is configured"));
      config.withAudienceResolver((source, value) -> Set.of());
      assertThat(AlertsTableMetaDataHelper.buildTeamTable(config).getField("memberSource").getHelpContents()).isNullOrEmpty();
      assertThat(AlertsTableMetaDataHelper.buildTeamMemberTable(config).getField("teamId").getHelpContents()).isNullOrEmpty();
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
         ///////////////////////////////////////////////////////////////////////////////
         // alerts_delivery was created as alerts_notification (changeset 13) and     //
         // renamed (16); the user-notification table is the later alerts_notification //
         ///////////////////////////////////////////////////////////////////////////////
         String created = "alerts_delivery".equals(tableName) ? "alerts_notification" : tableName;
         int    start   = "alerts_notification".equals(tableName) ? changelog.lastIndexOf("CREATE TABLE IF NOT EXISTS alerts_notification (")
            : changelog.indexOf("CREATE TABLE IF NOT EXISTS " + created + " (");
         String         body  = changelog.substring(start, changelog.indexOf(");", start));
         QTableMetaData table = qInstance.getTable(tableName);
         for(QFieldMetaData field : table.getFields().values())
         {
            String column = QInstanceEnricher.inferBackendName(field.getName());
            assertThat(body.contains("   " + column + " ") || changelog.contains("ALTER TABLE " + created + " ADD COLUMN " + column + " "))
               .as(tableName + "." + field.getName()).isTrue();
         }
      }
   }



   /*******************************************************************************
    ** Upgrade the old mute table using the shipped SQL and guards twice;
    ** existing rules survive and the new fields accept full alert keys.
    *******************************************************************************/
   @Test
   void testMuteSchemaUpgradeIsRepeatable() throws Exception
   {
      String changelog;
      try(InputStream in = getClass().getClassLoader().getResourceAsStream("db/liquibase/qbit-alerts/alerts.changelog.sql"))
      {
         changelog = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
      int start = changelog.indexOf("CREATE TABLE IF NOT EXISTS alerts_mute (");
      try(java.sql.Connection connection = com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager.getConnection(defineH2Backend());
         java.sql.Statement statement = connection.createStatement())
      {
         statement.execute("DROP TABLE alerts_mute");
         statement.execute(changelog.substring(start, changelog.indexOf(");", start) + 2));
         statement.execute("INSERT INTO alerts_mute (name, scope_type, scope_value, starts_at, ends_at) VALUES ('Existing', 'ALERT_TYPE', 'existing.type', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
         for(int pass = 0; pass < 2; pass++)
         {
            for(String number : List.of("21", "22"))
            {
               String block = changelog.substring(changelog.indexOf("-- changeset qbit-alerts:" + number));
               String guard = block.lines().filter(line -> line.startsWith("-- precondition-sql-check")).findFirst().orElseThrow();
               String sql = block.lines().filter(line -> line.startsWith("ALTER TABLE")).findFirst().orElseThrow();
               int expected = Integer.parseInt(guard.substring(guard.indexOf("expectedResult:") + 15, guard.indexOf(" SELECT")));
               try(java.sql.ResultSet result = statement.executeQuery(guard.substring(guard.indexOf("SELECT"))))
               {
                  assertThat(result.next()).isTrue();
                  if(result.getInt(1) != expected)
                  {
                     continue;
                  }
               }
               statement.execute(sql);
            }
         }
         try(java.sql.ResultSet result = statement.executeQuery("SELECT scope_type, scope_value, scope_record_id FROM alerts_mute"))
         {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("ALERT_TYPE");
            assertThat(result.getString(2)).isEqualTo("existing.type");
            assertThat(result.getString(3)).isNull();
         }
         String key = "k".repeat(520);
         statement.execute("UPDATE alerts_mute SET scope_value = '" + key + "', scope_record_id = '123'");
         try(java.sql.ResultSet result = statement.executeQuery("SELECT scope_value, scope_record_id FROM alerts_mute"))
         {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo(key);
            assertThat(result.getString(2)).isEqualTo("123");
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
      assertThat(config.getViewerPermissionNames()).doesNotContain(AlertsQBitConfig.PERMISSION_ADMIN, "alerts_channel.read", "alerts_delivery.read");
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


   /*******************************************************************************
    ** Policy records are admin-only, steps are children, and the job is hidden.
    *******************************************************************************/
   @Test
   void testEscalationMetadata() throws Exception
   {
      assertThat(AlertsAppMetaDataProducer.MAIN_TABLES).contains("alerts_escalation_policy").doesNotContain("alerts_escalation_step");
      assertThat(qInstance.getTable("alerts_escalation_policy").getSections()).anySatisfy(section ->
         assertThat(section.getWidgetName()).isEqualTo("alerts_escalation_policy_join_alerts_escalation_step"));
      assertThat(qInstance.getTable("alerts_type").getField("escalationPolicyId").getPossibleValueSourceName()).isEqualTo("alerts_escalation_policy");
      assertThat(qInstance.getTable("alerts_group").getField("escalationPolicyId").getPossibleValueSourceName()).isEqualTo("alerts_escalation_policy");
      assertThat(qInstance.getTable("alerts_escalation_step").getField("onlyIfStatus").getDefaultValue()).isEqualTo("OPEN");
      assertThat(qInstance.getPossibleValueSource("alerts_resolution").getEnumValues()).extracting(value -> String.valueOf(value.getId())).doesNotContain("EXPIRED");
      QProcessMetaData process = qInstance.getProcess(AlertsProcessesMetaDataProducer.ESCALATE_AND_REMIND);
      assertThat(process.getIsHidden()).isTrue();
      assertThat(process.getSchedule()).isNull();
      QProcessMetaData scheduled = AlertsProcessesMetaDataProducer.buildProcesses(new AlertsQBitConfig().withSchedulerName("scheduler"))
         .stream().filter(candidate -> candidate.getName().equals(AlertsProcessesMetaDataProducer.ESCALATE_AND_REMIND)).findFirst().orElseThrow();
      assertThat(scheduled.getSchedule().getSchedulerName()).isEqualTo("scheduler");
      assertThat(scheduled.getSchedule().getRepeatSeconds()).isEqualTo(60);
      loginAs(VIEWER);
      for(String table : List.of("alerts_escalation_policy", "alerts_escalation_step"))
      {
         assertThat(PermissionsHelper.hasTablePermission(new com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput(), table,
            com.kingsrook.qqq.backend.core.actions.permissions.TablePermissionSubType.READ)).isFalse();
      }
      loginAs(ADMIN);
      assertThat(PermissionsHelper.hasTablePermission(new com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput(), "alerts_escalation_policy",
         com.kingsrook.qqq.backend.core.actions.permissions.TablePermissionSubType.INSERT)).isTrue();
   }

}
