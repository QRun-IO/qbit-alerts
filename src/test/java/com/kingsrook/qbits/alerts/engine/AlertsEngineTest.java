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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import com.kingsrook.qbits.alerts.BaseAlertsTest;
import com.kingsrook.qbits.alerts.model.AlertsAlert;
import com.kingsrook.qbits.alerts.model.AlertsEvent;
import com.kingsrook.qbits.alerts.model.AlertsHeartbeat;
import com.kingsrook.qbits.alerts.model.AlertsMute;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.EventType;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.Resolution;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.Status;
import com.kingsrook.qbits.alerts.model.AlertsRecordRef;
import com.kingsrook.qbits.alerts.model.AlertsType;
import com.kingsrook.qqq.backend.core.alerts.AlertLink;
import com.kingsrook.qqq.backend.core.alerts.AlertRecordRef;
import com.kingsrook.qqq.backend.core.alerts.AlertResolution;
import com.kingsrook.qqq.backend.core.alerts.AlertSeverity;
import com.kingsrook.qqq.backend.core.alerts.AlertSignal;
import com.kingsrook.qqq.backend.core.alerts.AlertsInstanceMetaData;
import com.kingsrook.qqq.backend.core.alerts.QAlertTypeMetaData;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QUserFacingException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** The state machine: dedup, refresh, severity changes, snooze, resolve,
 ** reopen, flapping, unregistered and disabled types, maintenance windows,
 ** heartbeats, and two writers racing on one key.
 *******************************************************************************/
class AlertsEngineTest extends BaseAlertsTest
{
   static final String TYPE = "test.widget.broken";



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   protected void customizeInstance(QInstance qInstance)
   {
      AlertsInstanceMetaData.ofOrWithNew(qInstance).withAlertType(new QAlertTypeMetaData()
         .withName(TYPE)
         .withLabel("Widget broken")
         .withGroupName("widgets")
         .withDefaultSeverity(AlertSeverity.WARNING)
         .withSubjectTableName(WIDGET)
         .withDescription("A widget is broken.")
         .withHowToFix("Fix the widget."));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private AlertSignal signal(String key)
   {
      return (new AlertSignal(TYPE).withDedupKey(key).withTitle("Widget " + key + " is broken"));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private List<String> events(Integer alertId) throws QException
   {
      List<String> types = new ArrayList<>();
      for(QRecord event : where(AlertsEvent.TABLE_NAME, AlertsDb.eq("alertId", alertId).withOrderBy(new com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy("id"))))
      {
         types.add(event.getValueString("eventType"));
      }
      return (types);
   }



   /*******************************************************************************
    ** A raise opens one alert; repeats update it, never duplicate it.
    *******************************************************************************/
   @Test
   void testRaiseAndDedup() throws Exception
   {
      List<Integer> widgets = insertWidgets("BROKEN");
      QRecord alert = runtime.getEngine().raise(signal("w1")
         .withSummary("It stopped.")
         .withDetails("stack trace here")
         .withRecordRef(AlertRecordRef.of(WIDGET, widgets.get(0)).withLabel("Widget BROKEN"))
         .withLink(AlertLink.toRecord(WIDGET, widgets.get(0), "Open the widget"))
         .withAttribute("site", "main"));

      assertThat(alert.getValueString("status")).isEqualTo(Status.OPEN.name());
      assertThat(alert.getValueString("severity")).isEqualTo("WARNING");
      assertThat(alert.getValueString("openDedupKey")).isEqualTo(TYPE + "|w1");
      assertThat(alert.getValueString("subjectTableName")).isEqualTo(WIDGET);
      assertThat(alert.getValueString("subjectRecordId")).isEqualTo(String.valueOf(widgets.get(0)));
      assertThat(alert.getValueInteger("recordRefCount")).isEqualTo(1);
      assertThat(alert.getValueString("links")).contains("Open the widget");
      assertThat(alert.getValueString("attributes")).contains("site");

      QRecord type = runtime.asSystem(() -> AlertsDb.first(AlertsType.TABLE_NAME, AlertsDb.eq("name", TYPE)));
      assertThat(type.getValueInteger("openCount")).isEqualTo(1);
      assertThat(type.getValueString("howToFix")).isEqualTo("Fix the widget.");

      clock.advance(Duration.ofMinutes(5));
      QRecord again = runtime.getEngine().raise(signal("w1"));
      assertThat(again.getValue("id")).isEqualTo(alert.getValue("id"));
      assertThat(again.getValueInteger("occurrenceCount")).isEqualTo(2);
      assertThat(all(AlertsAlert.TABLE_NAME)).hasSize(1);
      assertThat(events(alert.getValueInteger("id"))).startsWith(EventType.RAISED.name());

      ///////////////////////////////////////////////////////
      // a repeat an hour later records a REPEATED entry   //
      ///////////////////////////////////////////////////////
      clock.advance(Duration.ofMinutes(61));
      runtime.getEngine().raise(signal("w1"));
      assertThat(events(alert.getValueInteger("id"))).contains(EventType.REPEATED.name());

      runtime.getEngine().raise(signal("w2"));
      assertThat(all(AlertsAlert.TABLE_NAME)).hasSize(2);
   }



   /*******************************************************************************
    ** Severity up unsnoozes and is recorded; down is only recorded.
    *******************************************************************************/
   @Test
   void testSeverityChangesAndSnooze() throws Exception
   {
      QRecord alert = runtime.getEngine().raise(signal("s1"));
      Integer id    = alert.getValueInteger("id");

      assertThatThrownBy(() -> runtime.getEngine().snooze(id, null, ADMIN)).isInstanceOf(QUserFacingException.class);
      assertThatThrownBy(() -> runtime.getEngine().snooze(id, clock.instant().minusSeconds(5), ADMIN)).isInstanceOf(QUserFacingException.class);
      assertThat(runtime.getEngine().snooze(id, clock.instant().plus(Duration.ofHours(4)), ADMIN)).isTrue();
      assertThat(runtime.asSystem(() -> AlertsDb.byId(AlertsAlert.TABLE_NAME, id)).getValueString("status")).isEqualTo(Status.SNOOZED.name());

      QRecord raised = runtime.getEngine().raise(signal("s1").withSeverity(AlertSeverity.CRITICAL));
      assertThat(raised.getValueString("severity")).isEqualTo("CRITICAL");
      assertThat(raised.getValueString("status")).isEqualTo(Status.OPEN.name());
      assertThat(events(id)).contains(EventType.SEVERITY_CHANGED.name(), EventType.UNSNOOZED.name());

      QRecord lowered = runtime.getEngine().raise(signal("s1").withSeverity(AlertSeverity.INFO));
      assertThat(lowered.getValueString("severity")).isEqualTo("INFO");

      assertThat(runtime.getEngine().acknowledge(id, ADMIN)).isTrue();
      assertThat(runtime.getEngine().acknowledge(id, ADMIN)).isFalse();
      assertThat(runtime.getEngine().assign(id, VIEWER, null, ADMIN)).isTrue();
      runtime.getEngine().comment(id, ADMIN, "Looking at it");
      assertThatThrownBy(() -> runtime.getEngine().comment(id, ADMIN, " ")).isInstanceOf(QUserFacingException.class);
      assertThat(events(id)).contains(EventType.ACKNOWLEDGED.name(), EventType.ASSIGNED.name(), EventType.COMMENTED.name());

      ////////////////////////////////////////////////////////
      // an expired snooze returns to its acknowledged state //
      ////////////////////////////////////////////////////////
      runtime.getEngine().snooze(id, clock.instant().plus(Duration.ofMinutes(10)), ADMIN);
      clock.advance(Duration.ofMinutes(11));
      assertThat(runtime.getEngine().maintenance()).isGreaterThanOrEqualTo(1);
      assertThat(runtime.asSystem(() -> AlertsDb.byId(AlertsAlert.TABLE_NAME, id)).getValueString("status")).isEqualTo(Status.ACKNOWLEDGED.name());
   }



   /*******************************************************************************
    ** Resolve, reopen within the window, and a new alert after it.
    *******************************************************************************/
   @Test
   void testResolveReopenAndNewAfterWindow() throws Exception
   {
      List<Integer> widgets = insertWidgets("BROKEN");
      QRecord       alert   = runtime.getEngine().raise(signal("r1").withRecordRef(AlertRecordRef.of(WIDGET, widgets.get(0))));
      Integer       id      = alert.getValueInteger("id");

      assertThat(runtime.getEngine().resolve(TYPE, "nope", Resolution.PRODUCER, null, null)).isNull();
      QRecord resolved = runtime.getEngine().resolve(TYPE, "r1", Resolution.PRODUCER, null, "fixed");
      assertThat(resolved.getValueString("status")).isEqualTo(Status.RESOLVED.name());
      assertThat(resolved.getValue("openDedupKey")).isNull();
      assertThat(resolved.getValueString("resolution")).isEqualTo("PRODUCER");
      assertThat(where(AlertsRecordRef.TABLE_NAME, AlertsDb.eq("alertId", id)).get(0).getValueBoolean("active")).isFalse();

      clock.advance(Duration.ofMinutes(10));
      QRecord reopened = runtime.getEngine().raise(signal("r1"));
      assertThat(reopened.getValue("id")).isEqualTo(id);
      assertThat(reopened.getValueInteger("reopenCount")).isEqualTo(1);
      assertThat(events(id)).contains(EventType.REOPENED.name());

      runtime.getEngine().resolveById(id, Resolution.MANUAL, ADMIN, "done");
      assertThat(runtime.getEngine().reopenById(id, ADMIN, "not done")).isTrue();
      assertThat(runtime.getEngine().reopenById(id, ADMIN, "again")).isFalse();
      runtime.getEngine().resolveById(id, Resolution.MANUAL, ADMIN, "done");

      clock.advance(Duration.ofMinutes(31));
      QRecord fresh = runtime.getEngine().raise(signal("r1"));
      assertThat(fresh.getValue("id")).isNotEqualTo(id);
      assertThat(all(AlertsAlert.TABLE_NAME)).hasSize(2);
   }



   /*******************************************************************************
    ** Five open/resolve changes in an hour start flapping; calm ends it.
    *******************************************************************************/
   @Test
   void testFlapping() throws Exception
   {
      QRecord alert = runtime.getEngine().raise(signal("f1"));
      Integer id    = alert.getValueInteger("id");
      for(int i = 0; i < 3; i++)
      {
         clock.advance(Duration.ofMinutes(1));
         runtime.getEngine().resolve(TYPE, "f1", Resolution.PRODUCER, null, null);
         clock.advance(Duration.ofMinutes(1));
         runtime.getEngine().raise(signal("f1"));
      }
      QRecord flapping = runtime.asSystem(() -> AlertsDb.byId(AlertsAlert.TABLE_NAME, id));
      assertThat(flapping.getValueBoolean("flapping")).isTrue();
      assertThat(events(id)).contains(EventType.FLAPPING_STARTED.name());

      clock.advance(Duration.ofMinutes(61));
      runtime.getEngine().maintenance();
      assertThat(runtime.asSystem(() -> AlertsDb.byId(AlertsAlert.TABLE_NAME, id)).getValueBoolean("flapping")).isFalse();
      assertThat(events(id)).contains(EventType.FLAPPING_ENDED.name());
   }



   /*******************************************************************************
    ** Undeclared types go to "unregistered"; disabled types and maintenance
    ** windows drop the signal; a severity override wins.
    *******************************************************************************/
   @Test
   void testTypeRulesAndMaintenance() throws Exception
   {
      QRecord unregistered = runtime.getEngine().raise(new AlertSignal("nobody.declared.this"));
      assertThat(unregistered.getValueString("alertTypeName")).isEqualTo("nobody.declared.this");
      QRecord type = runtime.asSystem(() -> AlertsDb.byId(AlertsType.TABLE_NAME, unregistered.getValue("alertTypeId")));
      assertThat(type.getValueString("name")).isEqualTo(AlertTypeSync.UNREGISTERED);

      QRecord widgetType = runtime.asSystem(() -> AlertsDb.first(AlertsType.TABLE_NAME, AlertsDb.eq("name", TYPE)));
      runtime.asSystem(() -> AlertsDb.update(AlertsType.TABLE_NAME, new QRecord().withValue("id", widgetType.getValue("id")).withValue("severityOverride", "ERROR")));
      assertThat(runtime.getEngine().raise(signal("o1").withSeverity(AlertSeverity.INFO)).getValueString("severity")).isEqualTo("ERROR");

      runtime.asSystem(() -> AlertsDb.update(AlertsType.TABLE_NAME, new QRecord().withValue("id", widgetType.getValue("id")).withValue("enabled", false)));
      assertThat(runtime.getEngine().raise(signal("o2"))).isNull();
      runtime.asSystem(() -> AlertsDb.update(AlertsType.TABLE_NAME, new QRecord().withValue("id", widgetType.getValue("id")).withValue("enabled", true)));

      runtime.asSystem(() -> AlertsDb.insert(AlertsMute.TABLE_NAME, new QRecord().withValue("name", "Maintenance").withValue("scopeType", "GROUP").withValue("scopeValue", "widgets")
         .withValue("startsAt", clock.instant().minusSeconds(60)).withValue("endsAt", clock.instant().plusSeconds(3600)).withValue("effect", "SUPPRESS_ALERTS").withValue("enabled", true)));
      assertThat(runtime.getEngine().raise(signal("m1"))).isNull();
      clock.advance(Duration.ofHours(2));
      assertThat(runtime.getEngine().raise(signal("m1"))).isNotNull();

      assertThat(runtime.getEngine().raise(null)).isNull();
      assertThat(runtime.getEngine().resolve(null, null, Resolution.PRODUCER, null, null)).isNull();
   }



   /*******************************************************************************
    ** Quiet-period types resolve on their own.
    *******************************************************************************/
   @Test
   void testQuietPeriod() throws Exception
   {
      QRecord widgetType = runtime.asSystem(() ->
      {
         runtime.getTypeSync().syncIfNeeded();
         return (AlertsDb.first(AlertsType.TABLE_NAME, AlertsDb.eq("name", TYPE)));
      });
      runtime.asSystem(() -> AlertsDb.update(AlertsType.TABLE_NAME, new QRecord().withValue("id", widgetType.getValue("id")).withValue("resolveMode", "QUIET_PERIOD").withValue("quietPeriodMinutes", 60)));
      QRecord alert = runtime.getEngine().raise(signal("q1"));
      clock.advance(Duration.ofMinutes(30));
      runtime.getEngine().maintenance();
      assertThat(runtime.asSystem(() -> AlertsDb.byId(AlertsAlert.TABLE_NAME, alert.getValue("id"))).getValueString("status")).isEqualTo("OPEN");
      clock.advance(Duration.ofMinutes(31));
      runtime.getEngine().maintenance();
      QRecord after = runtime.asSystem(() -> AlertsDb.byId(AlertsAlert.TABLE_NAME, alert.getValue("id")));
      assertThat(after.getValueString("status")).isEqualTo("RESOLVED");
      assertThat(after.getValueString("resolution")).isEqualTo("QUIET_PERIOD");
   }



   /*******************************************************************************
    ** Heartbeats upsert one row per key.
    *******************************************************************************/
   @Test
   void testHeartbeat() throws Exception
   {
      runtime.getEngine().heartbeat("backups.nightly", null);
      clock.advance(Duration.ofMinutes(1));
      Instant later = clock.instant();
      runtime.getEngine().heartbeat("backups.nightly", later);
      runtime.getEngine().heartbeat(null, later);
      List<QRecord> beats = all(AlertsHeartbeat.TABLE_NAME);
      assertThat(beats).hasSize(1);
      assertThat(beats.get(0).getValueInteger("beatCount")).isEqualTo(2);
      assertThat(beats.get(0).getValueInstant("lastBeatAt")).isEqualTo(later);
   }



   /*******************************************************************************
    ** The sink processes producer calls (synchronously in tests).
    *******************************************************************************/
   @Test
   void testSink() throws Exception
   {
      runtime.getSink().raise(signal("k1"));
      runtime.getSink().heartbeat("sink.beat", null);
      runtime.getSink().resolve(new AlertResolution(TYPE, "k1", "gone"));
      runtime.getSink().raise(null);
      runtime.getSink().resolve(null);
      List<QRecord> alerts = all(AlertsAlert.TABLE_NAME);
      assertThat(alerts).hasSize(1);
      assertThat(alerts.get(0).getValueString("status")).isEqualTo("RESOLVED");
      assertThat(all(AlertsHeartbeat.TABLE_NAME)).hasSize(1);
   }



   /*******************************************************************************
    ** Two writers raising the same new key end with one open alert.
    *******************************************************************************/
   @Test
   void testRaceOnOneKey() throws Exception
   {
      runtime.getTypeSync().syncIfNeeded();
      ExecutorService      pool  = Executors.newFixedThreadPool(4);
      CountDownLatch       start = new CountDownLatch(1);
      List<Future<Object>> runs  = new ArrayList<>();
      for(int i = 0; i < 4; i++)
      {
         runs.add(pool.submit(() ->
         {
            QContext.init(qInstance, new com.kingsrook.qqq.backend.core.model.session.QSystemUserSession());
            start.await();
            return (runtime.getEngine().raise(signal("race")));
         }));
      }
      start.countDown();
      for(Future<Object> run : runs)
      {
         run.get();
      }
      pool.shutdown();
      List<QRecord> open = where(AlertsAlert.TABLE_NAME, new QQueryFilter().withCriteria("openDedupKey", QCriteriaOperator.EQUALS, TYPE + "|race"));
      assertThat(open).hasSize(1);
      assertThat(open.get(0).getValueInteger("occurrenceCount")).isGreaterThanOrEqualTo(1);
   }
}
