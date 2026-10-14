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


import java.time.Duration;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.alerts.BaseAlertsTest;
import com.kingsrook.qbits.alerts.TestSteps;
import com.kingsrook.qbits.alerts.model.AlertsAlert;
import com.kingsrook.qbits.alerts.model.AlertsMonitor;
import com.kingsrook.qbits.alerts.model.AlertsMute;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.MonitorStatus;
import com.kingsrook.qbits.alerts.model.AlertsType;
import com.kingsrook.qqq.backend.core.actions.processes.RunProcessAction;
import com.kingsrook.qqq.backend.core.alerts.AlertDefaultMonitor;
import com.kingsrook.qqq.backend.core.alerts.AlertMonitorKind;
import com.kingsrook.qqq.backend.core.alerts.AlertSeverity;
import com.kingsrook.qqq.backend.core.alerts.AlertSignal;
import com.kingsrook.qqq.backend.core.alerts.AlertsInstanceMetaData;
import com.kingsrook.qqq.backend.core.alerts.QAlertTypeMetaData;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** The monitor kinds, the full-state diff, forMinutes, CHECK_FAILED and its
 ** meta-alert, previews, maintenance windows, seeded monitors and process
 ** failures.
 *******************************************************************************/
class MonitorEvaluatorTest extends BaseAlertsTest
{
   static final String TYPE   = "test.widget.broken";
   static final String SEEDED = "test.beat.missed";



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   protected void customizeInstance(QInstance qInstance)
   {
      AlertsInstanceMetaData.ofOrWithNew(qInstance)
         .withAlertType(new QAlertTypeMetaData().withName(TYPE).withLabel("Widget broken").withGroupName("widgets").withSubjectTableName(WIDGET).withDefaultSeverity(AlertSeverity.WARNING))
         .withAlertType(new QAlertTypeMetaData().withName(SEEDED).withLabel("Beat missed").withDefaultSeverity(AlertSeverity.ERROR)
            .withDefaultMonitor(AlertDefaultMonitor.heartbeat("Seeded beat", "test.beat", 3600, 60)));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @BeforeEach
   void reset()
   {
      TestSteps.fail = false;
      TestSteps.checkSignals = new java.util.ArrayList<>();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private Integer monitor(String kind, Map<String, Object> definition, boolean perRecord, int forMinutes) throws QException
   {
      runtime.getTypeSync().syncIfNeeded();
      QRecord type = runtime.asSystem(() -> AlertsDb.first(AlertsType.TABLE_NAME, AlertsDb.eq("name", TYPE)));
      return (runtime.asSystem(() -> AlertsDb.insert(AlertsMonitor.TABLE_NAME, new QRecord()
         .withValue("name", kind + " monitor").withValue("alertTypeId", type.getValue("id")).withValue("kind", kind).withValue("enabled", true)
         .withValue("definition", JsonUtils.toJson(definition)).withValue("perRecord", perRecord).withValue("scheduleSeconds", 60).withValue("forMinutes", forMinutes)
         .withValue("consecutiveFailures", 0)).getValueInteger("id")));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private List<QRecord> openFor(Integer monitorId) throws QException
   {
      return (where(AlertsAlert.TABLE_NAME, AlertsDb.eq("monitorId", monitorId).withCriteria("status", QCriteriaOperator.IN, List.of("OPEN", "ACKNOWLEDGED", "SNOOZED"))));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Map<String, Object> brokenFilter()
   {
      return (Map.of("criteria", List.of(Map.of("fieldName", "status", "operator", "EQUALS", "values", List.of("BROKEN")))));
   }



   /*******************************************************************************
    ** TABLE_QUERY, aggregate mode: one alert with record refs and a count;
    ** resolved when the check is clean (full-state diff).
    *******************************************************************************/
   @Test
   void testTableQueryAggregate() throws Exception
   {
      insertWidgets("BROKEN", "BROKEN", "OK");
      Integer monitorId = monitor("TABLE_QUERY", Map.of("tableName", WIDGET, "filter", brokenFilter(), "labelField", "name"), false, 0);

      MonitorEvaluator.Plan preview = runtime.getEvaluator().preview(monitorId);
      assertThat(preview.status()).isEqualTo(MonitorStatus.FIRING);
      assertThat(preview.wouldOpen()).hasSize(1);
      assertThat(openFor(monitorId)).isEmpty();

      assertThat(runtime.getEvaluator().evaluateDue()).isEqualTo(2);
      List<QRecord> open = openFor(monitorId);
      assertThat(open).hasSize(1);
      assertThat(open.get(0).getValueInteger("itemCount")).isEqualTo(2);
      assertThat(open.get(0).getValueInteger("recordRefCount")).isEqualTo(2);
      assertThat(open.get(0).getValueString("title")).isEqualTo("TABLE_QUERY monitor: 2 widgets");
      QRecord monitor = runtime.asSystem(() -> AlertsDb.byId(AlertsMonitor.TABLE_NAME, monitorId));
      assertThat(monitor.getValueString("lastStatus")).isEqualTo("FIRING");
      assertThat(monitor.getValueInstant("nextRunAt")).isEqualTo(clock.instant().plusSeconds(60));

      ///////////////////////////////////////////////////////
      // not due yet: nothing evaluated                    //
      ///////////////////////////////////////////////////////
      assertThat(runtime.getEvaluator().evaluateDue()).isEqualTo(0);

      runtime.asSystem(() -> AlertsDb.update(WIDGET, new QRecord().withValue("id", 1).withValue("status", "OK")));
      runtime.asSystem(() -> AlertsDb.update(WIDGET, new QRecord().withValue("id", 2).withValue("status", "OK")));
      MonitorEvaluator.Plan clean = runtime.getEvaluator().runNow(monitorId);
      assertThat(clean.status()).isEqualTo(MonitorStatus.OK);
      assertThat(clean.wouldResolve()).hasSize(1);
      assertThat(openFor(monitorId)).isEmpty();
      assertThat(where(AlertsAlert.TABLE_NAME, AlertsDb.eq("monitorId", monitorId)).get(0).getValueString("resolution")).isEqualTo("MONITOR");
   }



   /*******************************************************************************
    ** TABLE_QUERY per record; beyond the cap it falls back to one alert.
    *******************************************************************************/
   @Test
   void testTableQueryPerRecordAndCap() throws Exception
   {
      config.withMaxOpenPerMonitor(2);
      insertWidgets("BROKEN", "BROKEN");
      Integer monitorId = monitor("TABLE_QUERY", Map.of("tableName", WIDGET, "filter", brokenFilter()), true, 0);
      runtime.getEvaluator().runNow(monitorId);
      assertThat(openFor(monitorId)).hasSize(2).allMatch(alert -> alert.getValueString("dedupKey").startsWith("record:"));

      insertWidgets("BROKEN");
      runtime.getEvaluator().runNow(monitorId);
      List<QRecord> open = openFor(monitorId);
      assertThat(open).hasSize(1);
      assertThat(open.get(0).getValueString("summary")).contains("grouped into one alert");
   }



   /*******************************************************************************
    ** AGGREGATE bands, with and without group-by.
    *******************************************************************************/
   @Test
   void testAggregate() throws Exception
   {
      insertWidgets("BROKEN", "BROKEN", "BROKEN", "OK");
      Integer monitorId = monitor("AGGREGATE", Map.of("tableName", WIDGET, "aggregate", "COUNT", "fieldName", "id", "comparator", "GTE",
         "warningThreshold", 2, "errorThreshold", 3, "criticalThreshold", 10), false, 0);
      runtime.getEvaluator().runNow(monitorId);
      List<QRecord> open = openFor(monitorId);
      assertThat(open).hasSize(1);
      assertThat(open.get(0).getValueString("severity")).isEqualTo("ERROR");

      Integer grouped = monitor("AGGREGATE", Map.of("tableName", WIDGET, "aggregate", "SUM", "fieldName", "price", "groupBy", "status", "warningThreshold", 15), false, 0);
      runtime.getEvaluator().runNow(grouped);
      assertThat(openFor(grouped)).hasSize(1).allMatch(alert -> alert.getValueString("dedupKey").equals("group:BROKEN"));
   }



   /*******************************************************************************
    ** HEARTBEAT on a key, a process and a table's newest row; the seeded one.
    *******************************************************************************/
   @Test
   void testHeartbeats() throws Exception
   {
      Integer byKey = monitor("HEARTBEAT", Map.of("heartbeatKey", "job.a", "expectedEverySeconds", 600, "graceSeconds", 60), false, 0);
      runtime.getEngine().heartbeat("job.a", clock.instant());
      runtime.getEvaluator().runNow(byKey);
      assertThat(openFor(byKey)).isEmpty();

      clock.advance(Duration.ofMinutes(12));
      runtime.getEvaluator().runNow(byKey);
      assertThat(openFor(byKey)).hasSize(1);
      runtime.getEngine().heartbeat("job.a", null);
      runtime.getEvaluator().runNow(byKey);
      assertThat(openFor(byKey)).isEmpty();

      insertWidgets("OK");
      Integer byTable = monitor("HEARTBEAT", Map.of("tableName", WIDGET, "dateField", "checkedAt", "expectedEverySeconds", 3600), false, 0);
      runtime.getEvaluator().runNow(byTable);
      assertThat(openFor(byTable)).isEmpty();
      clock.advance(Duration.ofHours(2));
      runtime.getEvaluator().runNow(byTable);
      assertThat(openFor(byTable)).hasSize(1);
      assertThat(openFor(byTable).get(0).getValueString("title")).contains("no new Widget row");

      Integer byProcess = monitor("HEARTBEAT", Map.of("processName", "nightlyThing", "expectedEverySeconds", 3600), false, 0);
      AlertsProcessListener.clearCache();
      new RunProcessAction().execute(new RunProcessInput().withProcessName("nightlyThing"));
      clock.advance(Duration.ofHours(2));
      runtime.getEvaluator().runNow(byProcess);
      assertThat(openFor(byProcess)).hasSize(1);
      new RunProcessAction().execute(new RunProcessInput().withProcessName("nightlyThing"));
      runtime.getEvaluator().runNow(byProcess);
      assertThat(openFor(byProcess)).isEmpty();

      QRecord seeded = runtime.asSystem(() -> AlertsDb.first(AlertsMonitor.TABLE_NAME, AlertsDb.eq("seeded", true)));
      assertThat(seeded.getValueString("name")).isEqualTo("Seeded beat");
      assertThat(seeded.getValueString("kind")).isEqualTo(AlertMonitorKind.HEARTBEAT.name());
   }



   /*******************************************************************************
    ** CODE checks: what they return fires; what they stop returning resolves.
    ** A check that keeps failing raises the meta-alert, resolved when it runs.
    *******************************************************************************/
   @Test
   void testCodeAndCheckFailed() throws Exception
   {
      Integer monitorId = monitor("CODE", Map.of("codeReference", TestSteps.Check.class.getName()), false, 0);
      TestSteps.checkSignals = List.of(new AlertSignal(null).withDedupKey("dlq:orders").withTitle("Dead letters on orders"),
         new AlertSignal(null).withDedupKey("dlq:emails").withTitle("Dead letters on emails"));
      runtime.getEvaluator().runNow(monitorId);
      assertThat(openFor(monitorId)).hasSize(2);

      TestSteps.checkSignals = List.of(new AlertSignal(null).withDedupKey("dlq:orders").withTitle("Dead letters on orders"));
      MonitorEvaluator.Plan plan = runtime.getEvaluator().runNow(monitorId);
      assertThat(plan.wouldUpdate()).containsExactly("dlq:orders");
      assertThat(plan.wouldResolve()).hasSize(1);
      assertThat(openFor(monitorId)).hasSize(1);

      Integer broken = monitor("CODE", Map.of("codeReference", TestSteps.BrokenCheck.class.getName()), false, 0);
      for(int i = 0; i < 3; i++)
      {
         MonitorEvaluator.Plan failed = runtime.getEvaluator().runNow(broken);
         assertThat(failed.status()).isEqualTo(MonitorStatus.CHECK_FAILED);
         assertThat(failed.error()).contains("broker unreachable");
      }
      QRecord meta = runtime.asSystem(() -> AlertsDb.first(AlertsAlert.TABLE_NAME, AlertsDb.eq("alertTypeName", MonitorEvaluator.MONITOR_FAILING_TYPE)));
      assertThat(meta.getValueString("status")).isEqualTo("OPEN");

      runtime.asSystem(() -> AlertsDb.update(AlertsMonitor.TABLE_NAME, new QRecord().withValue("id", broken).withValue("definition", JsonUtils.toJson(Map.of("codeReference", TestSteps.Check.class.getName())))));
      TestSteps.checkSignals = List.of();
      runtime.getEvaluator().runNow(broken);
      assertThat(runtime.asSystem(() -> AlertsDb.byId(AlertsAlert.TABLE_NAME, meta.getValue("id"))).getValueString("status")).isEqualTo("RESOLVED");

      Integer missing = monitor("CODE", Map.of("codeReference", "com.example.Nope"), false, 0);
      assertThat(runtime.getEvaluator().runNow(missing).error()).contains("not found");
      Integer noTable = monitor("TABLE_QUERY", Map.of("tableName", "nope"), false, 0);
      assertThat(runtime.getEvaluator().runNow(noTable).error()).contains("not a table");
      assertThatThrownBy(() -> runtime.getEvaluator().runNow(99999)).hasMessageContaining("not found");
   }



   /*******************************************************************************
    ** forMinutes holds the alert back; a maintenance window skips the check.
    *******************************************************************************/
   @Test
   void testForMinutesAndMaintenance() throws Exception
   {
      insertWidgets("BROKEN");
      Integer monitorId = monitor("TABLE_QUERY", Map.of("tableName", WIDGET, "filter", brokenFilter()), false, 10);
      assertThat(runtime.getEvaluator().runNow(monitorId).status()).isEqualTo(MonitorStatus.PENDING);
      assertThat(openFor(monitorId)).isEmpty();
      clock.advance(Duration.ofMinutes(11));
      assertThat(runtime.getEvaluator().runNow(monitorId).status()).isEqualTo(MonitorStatus.FIRING);
      assertThat(openFor(monitorId)).hasSize(1);

      runtime.asSystem(() -> AlertsDb.insert(AlertsMute.TABLE_NAME, new QRecord().withValue("name", "window").withValue("scopeType", "MONITOR").withValue("scopeValue", String.valueOf(monitorId))
         .withValue("startsAt", clock.instant().minusSeconds(1)).withValue("endsAt", clock.instant().plusSeconds(600)).withValue("effect", "SUPPRESS_ALERTS").withValue("enabled", true)));
      assertThat(runtime.getEvaluator().runNow(monitorId).status()).isEqualTo(MonitorStatus.SKIPPED_MUTED);
   }



   /*******************************************************************************
    ** PROCESS_FAILED: a failed run raises, the next success resolves.
    *******************************************************************************/
   @Test
   void testProcessFailed() throws Exception
   {
      Integer monitorId = monitor("PROCESS_FAILED", Map.of("processName", "nightly*"), false, 0);
      AlertsProcessListener.clearCache();

      TestSteps.fail = true;
      assertThatThrownBy(() -> new RunProcessAction().execute(new RunProcessInput().withProcessName("nightlyThing")));
      List<QRecord> open = openFor(monitorId);
      assertThat(open).hasSize(1);
      assertThat(open.get(0).getValueString("title")).isEqualTo("Nightly thing failed");
      assertThat(open.get(0).getValueString("details")).contains("disk full");

      TestSteps.fail = false;
      new RunProcessAction().execute(new RunProcessInput().withProcessName("nightlyThing"));
      assertThat(openFor(monitorId)).isEmpty();
      assertThat(runtime.getEvaluator().runNow(monitorId).value()).isEqualTo("event-driven");
   }
}
