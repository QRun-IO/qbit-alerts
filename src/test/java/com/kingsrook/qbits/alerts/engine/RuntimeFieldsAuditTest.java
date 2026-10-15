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


import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.alerts.BaseAlertsTest;
import com.kingsrook.qbits.alerts.model.AlertsAlert;
import com.kingsrook.qbits.alerts.model.AlertsMonitor;
import com.kingsrook.qbits.alerts.model.AlertsType;
import com.kingsrook.qqq.backend.core.alerts.AlertSeverity;
import com.kingsrook.qqq.backend.core.alerts.AlertsInstanceMetaData;
import com.kingsrook.qqq.backend.core.alerts.QAlertTypeMetaData;
import com.kingsrook.qqq.backend.core.instances.QInstanceEnricher;
import com.kingsrook.qqq.backend.core.model.audits.AuditsMetaDataProvider;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSBackendMetaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Engine bookkeeping writes (monitor leases and run results, alert reminder
 ** and notification timers) create no audit rows; configuration edits and
 ** alert state changes still do.
 *******************************************************************************/
class RuntimeFieldsAuditTest extends BaseAlertsTest
{
   static final String TYPE = "test.widget.broken";



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   protected void customizeInstance(QInstance qInstance)
   {
      AlertsInstanceMetaData.ofOrWithNew(qInstance)
         .withAlertType(new QAlertTypeMetaData().withName(TYPE).withLabel("Widget broken").withGroupName("widgets").withSubjectTableName(WIDGET).withDefaultSeverity(AlertSeverity.WARNING));
   }



   /*******************************************************************************
    ** Core audit tables on the test backend.
    *******************************************************************************/
   @BeforeEach
   void auditTables() throws Exception
   {
      new AuditsMetaDataProvider().defineAll(qInstance, BACKEND_NAME, null);
      new QInstanceEnricher(qInstance).enrich();
      try(Connection connection = ConnectionManager.getConnection((RDBMSBackendMetaData) qInstance.getBackend(BACKEND_NAME)); Statement statement = connection.createStatement())
      {
         for(String table : List.of("auditDetail", "audit", "auditUser", "auditTable"))
         {
            statement.execute("DROP TABLE IF EXISTS " + table);
         }
         statement.execute("CREATE TABLE auditUser (id INTEGER AUTO_INCREMENT PRIMARY KEY, name VARCHAR(200), createDate TIMESTAMP, modifyDate TIMESTAMP)");
         statement.execute("CREATE TABLE auditTable (id INTEGER AUTO_INCREMENT PRIMARY KEY, name VARCHAR(200), label VARCHAR(200), createDate TIMESTAMP, modifyDate TIMESTAMP)");
         statement.execute("CREATE TABLE audit (id BIGINT AUTO_INCREMENT PRIMARY KEY, auditTableId INTEGER, auditUserId INTEGER, recordId INTEGER, message VARCHAR(250), timestamp TIMESTAMP)");
         statement.execute("CREATE TABLE auditDetail (id BIGINT AUTO_INCREMENT PRIMARY KEY, auditId BIGINT, message VARCHAR(250), fieldName VARCHAR(100), oldValue VARCHAR(250), newValue VARCHAR(250))");
      }
   }



   /*******************************************************************************
    ** Edited-record audits for one table.
    *******************************************************************************/
   private List<QRecord> edits(String tableName) throws Exception
   {
      List<QRecord> tables = where("auditTable", AlertsDb.eq("name", tableName));
      if(tables.isEmpty())
      {
         return (List.of());
      }
      return (where("audit", AlertsDb.eq("auditTableId", tables.get(0).getValue("id")).withCriteria("message", com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator.EQUALS, "Record was Edited")));
   }



   /*******************************************************************************
    ** Changed field names of one table's edit audits.
    *******************************************************************************/
   private List<String> editedFields(String tableName) throws Exception
   {
      List<Object> ids = edits(tableName).stream().map(r -> r.getValue("id")).map(Object.class::cast).toList();
      return (all("auditDetail").stream().filter(d -> ids.contains(d.getValue("auditId"))).map(d -> d.getValueString("fieldName")).toList());
   }



   /*******************************************************************************
    ** The classifier: bookkeeping only, or not.
    *******************************************************************************/
   @Test
   void classifiesBookkeepingOnlyUpdates()
   {
      assertThat(RuntimeFields.onlyRuntime(AlertsMonitor.TABLE_NAME, new QRecord().withValue("id", 1).withValue("claimedBy", "x").withValue("claimedUntil", Instant.now()))).isTrue();
      assertThat(RuntimeFields.onlyRuntime(AlertsMonitor.TABLE_NAME, new QRecord().withValue("id", 1).withValue("lastRunAt", Instant.now()).withValue("enabled", false))).isFalse();
      assertThat(RuntimeFields.onlyRuntime(AlertsAlert.TABLE_NAME, new QRecord().withValue("id", 1).withValue("timedNotificationCheckedAt", Instant.now()))).isTrue();
      assertThat(RuntimeFields.onlyRuntime(AlertsAlert.TABLE_NAME, new QRecord().withValue("id", 1).withValue("status", "ACKNOWLEDGED"))).isFalse();
      assertThat(RuntimeFields.onlyRuntime(WIDGET, new QRecord().withValue("id", 1).withValue("checkedAt", Instant.now()))).isFalse();
   }



   /*******************************************************************************
    ** Evaluations and reminder checks write no monitor or alert edit audits;
    ** an admin edit and an acknowledgement do.
    *******************************************************************************/
   @Test
   void evaluationsAreNotAuditedButEditsAndTransitionsAre() throws Exception
   {
      insertWidgets("BROKEN", "OK");
      runtime.getTypeSync().syncIfNeeded();
      QRecord type = runtime.asSystem(() -> AlertsDb.first(AlertsType.TABLE_NAME, AlertsDb.eq("name", TYPE)));
      Integer monitorId = runtime.asSystem(() -> AlertsDb.insert(AlertsMonitor.TABLE_NAME, new QRecord()
         .withValue("name", "Broken widgets").withValue("alertTypeId", type.getValue("id")).withValue("kind", "TABLE_QUERY").withValue("enabled", true)
         .withValue("definition", JsonUtils.toJson(Map.of("tableName", WIDGET, "filter", Map.of("criteria", List.of(Map.of("fieldName", "status", "operator", "EQUALS", "values", List.of("BROKEN")))))))
         .withValue("perRecord", false).withValue("scheduleSeconds", 60).withValue("forMinutes", 0).withValue("consecutiveFailures", 0)).getValueInteger("id"));

      for(int run = 0; run < 3; run++)
      {
         runtime.getEvaluator().evaluateDue();
         new EscalationAndReminder(runtime).runDue();
         clock.advance(java.time.Duration.ofMinutes(2));
      }
      assertThat(runtime.asSystem(() -> AlertsDb.byId(AlertsMonitor.TABLE_NAME, monitorId)).getValueInstant("lastRunAt")).isNotNull();
      assertThat(edits(AlertsMonitor.TABLE_NAME)).as("monitor runs are bookkeeping").isEmpty();
      assertThat(editedFields(AlertsAlert.TABLE_NAME)).as("reminder checks and ref counts are bookkeeping")
         .doesNotContain("timedNotificationCheckedAt", "recordRefCount", "lastNotifiedAt");

      runtime.asSystem(() -> AlertsDb.update(AlertsMonitor.TABLE_NAME, new QRecord().withValue("id", monitorId).withValue("name", "Broken widgets (renamed)")));
      runtime.asSystem(() -> AlertsDb.update(AlertsMonitor.TABLE_NAME, new QRecord().withValue("id", monitorId).withValue("enabled", false).withValue("nextRunAt", null)));
      assertThat(edits(AlertsMonitor.TABLE_NAME)).hasSize(2);
      assertThat(editedFields(AlertsMonitor.TABLE_NAME)).contains("name", "enabled");

      QRecord alert = where(AlertsAlert.TABLE_NAME, AlertsDb.eq("monitorId", monitorId)).get(0);
      runtime.getEngine().acknowledge(alert.getValueInteger("id"), ADMIN);
      assertThat(editedFields(AlertsAlert.TABLE_NAME)).contains("status");
   }
}
