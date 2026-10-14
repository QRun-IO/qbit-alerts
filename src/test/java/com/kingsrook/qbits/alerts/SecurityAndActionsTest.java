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


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.alerts.engine.AlertsDb;
import com.kingsrook.qbits.alerts.model.AlertsAlert;
import com.kingsrook.qbits.alerts.model.AlertsChannel;
import com.kingsrook.qbits.alerts.model.AlertsDelivery;
import com.kingsrook.qbits.alerts.model.AlertsEvent;
import com.kingsrook.qbits.alerts.model.AlertsMonitor;
import com.kingsrook.qbits.alerts.model.AlertsMute;
import com.kingsrook.qbits.alerts.model.AlertsSubscription;
import com.kingsrook.qbits.alerts.model.AlertsType;
import com.kingsrook.qbits.alerts.model.AlertsUserPreference;
import com.kingsrook.qbits.alerts.processes.AlertsAction;
import com.kingsrook.qqq.backend.core.actions.processes.RunProcessAction;
import com.kingsrook.qqq.backend.core.actions.tables.CountAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.alerts.AlertRecordRef;
import com.kingsrook.qqq.backend.core.alerts.AlertSeverity;
import com.kingsrook.qqq.backend.core.alerts.AlertSignal;
import com.kingsrook.qqq.backend.core.alerts.AlertsInstanceMetaData;
import com.kingsrook.qqq.backend.core.alerts.QAlertTypeMetaData;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLineInterface;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.StreamedETLWithFrontendProcess;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Visibility follows the underlying record and the user's permissions;
 ** personal rows are the user's own; the qbit's tables can only be changed
 ** through the engine; and every record action runs as a standard streamed
 ** ETL process with Review and Result summary lines.
 *******************************************************************************/
class SecurityAndActionsTest extends BaseAlertsTest
{
   static final String TYPE   = "test.widget.broken";
   static final String SECRET = "test.secret.leak";



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   protected void customizeInstance(QInstance qInstance)
   {
      AlertsInstanceMetaData.ofOrWithNew(qInstance)
         .withAlertType(new QAlertTypeMetaData().withName(TYPE).withLabel("Widget broken").withGroupName("widgets").withSubjectTableName(WIDGET).withDefaultSeverity(AlertSeverity.ERROR))
         .withAlertType(new QAlertTypeMetaData().withName(SECRET).withLabel("Secret leak").withVisibilityPermission("alerts.admin").withDefaultSeverity(AlertSeverity.CRITICAL));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private int visibleAlerts() throws QException
   {
      return (CountAction.execute(AlertsAlert.TABLE_NAME, new QQueryFilter()));
   }



   /*******************************************************************************
    ** A user sees an alert only with the type's permission and read on its
    ** subject table; counts agree with lists; the timeline follows its alert.
    *******************************************************************************/
   @Test
   void testVisibility() throws Exception
   {
      runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("w").withTitle("Widget broken"));
      runtime.getEngine().raise(new AlertSignal(SECRET).withDedupKey("s").withTitle("Secret"));
      runtime.getEngine().raise(new AlertSignal("test.system.plain").withDedupKey("p").withTitle("Plain"));

      loginAs(ADMIN);
      assertThat(visibleAlerts()).isEqualTo(3);

      loginAs(VIEWER);
      assertThat(visibleAlerts()).isEqualTo(2);
      assertThat(QueryAction.execute(AlertsAlert.TABLE_NAME, new QQueryFilter())).hasSize(2);
      assertThat(QueryAction.execute(AlertsEvent.TABLE_NAME, new QQueryFilter())).allMatch(event -> !"Secret".equals(event.getValueString("note")));

      loginAs(OUTSIDER);
      assertThat(QueryAction.execute(AlertsAlert.TABLE_NAME, new QQueryFilter())).extracting(r -> r.getValueString("title")).containsExactly("Plain");

      /////////////////////////////////////////////////////////////////
      // a session the host never gave keys to sees only ungated     //
      // alerts (never more than with the keys)                      //
      /////////////////////////////////////////////////////////////////
      com.kingsrook.qqq.backend.core.context.QContext.init(qInstance, BaseAlertsTest.TestSessionCustomizer.sessionFor(VIEWER));
      assertThat(QueryAction.execute(AlertsAlert.TABLE_NAME, new QQueryFilter())).extracting(r -> r.getValueString("title")).containsExactly("Plain");
   }



   /*******************************************************************************
    ** Personal rows: a viewer subscribes only themselves and sees only their
    ** own; team and channel subscriptions are for admins.
    *******************************************************************************/
   @Test
   void testPersonalRows() throws Exception
   {
      loginAs(VIEWER);
      List<QRecord> forSomeoneElse = InsertAction.executeForRecords(new InsertInput(AlertsSubscription.TABLE_NAME).withRecord(new QRecord()
         .withValue("scopeType", "ALL").withValue("userId", "someone-else").withValue("minSeverity", "ERROR")));
      assertThat(forSomeoneElse.get(0).getErrors()).isNotEmpty();
      List<QRecord> mine = InsertAction.executeForRecords(new InsertInput(AlertsSubscription.TABLE_NAME).withRecord(new QRecord()
         .withValue("scopeType", "ALL").withValue("userId", VIEWER).withValue("minSeverity", "ERROR")));
      assertThat(mine.get(0).getErrors()).isEmpty();
      assertThat(mine.get(0).getValueString("createdByUserId")).isEqualTo(VIEWER);

      List<QRecord> team = InsertAction.executeForRecords(new InsertInput(AlertsSubscription.TABLE_NAME).withRecord(new QRecord()
         .withValue("subscriberType", "TEAM").withValue("teamId", 1).withValue("scopeType", "ALL")));
      assertThat(team.get(0).getErrors()).isNotEmpty();

      List<QRecord> mute = InsertAction.executeForRecords(new InsertInput(AlertsMute.TABLE_NAME).withRecord(new QRecord()
         .withValue("name", "window").withValue("scopeType", "ALL").withValue("startsAt", clock.instant()).withValue("endsAt", clock.instant().plusSeconds(60)).withValue("effect", "SUPPRESS_ALERTS")));
      assertThat(mute.get(0).getErrors()).isNotEmpty();

      loginAs(MANAGER);
      assertThat(QueryAction.execute(AlertsSubscription.TABLE_NAME, new QQueryFilter())).isEmpty();
      loginAs(ADMIN);
      assertThat(QueryAction.execute(AlertsSubscription.TABLE_NAME, new QQueryFilter())).hasSize(1);
      List<QRecord> adminTeam = InsertAction.executeForRecords(new InsertInput(AlertsSubscription.TABLE_NAME).withRecord(new QRecord()
         .withValue("subscriberType", "CHANNEL").withValue("scopeType", "ALL")));
      assertThat(adminTeam.get(0).getErrors()).isNotEmpty();

      loginAs(VIEWER);
      List<QRecord> settings = InsertAction.executeForRecords(new InsertInput(AlertsUserPreference.TABLE_NAME).withRecord(new QRecord().withValue("userId", VIEWER).withValue("emailAddress", "v@example.com")));
      assertThat(settings.get(0).getValueString("userId")).isEqualTo(VIEWER);
   }



   /*******************************************************************************
    ** Alerts, timelines and notifications only change through the qbit.
    *******************************************************************************/
   @Test
   void testWriteGuard() throws Exception
   {
      QRecord alert = runtime.getEngine().raise(new AlertSignal("test.system.plain").withTitle("Plain"));
      loginAs(ADMIN);
      List<QRecord> updated = UpdateAction.executeForRecords(new UpdateInput(AlertsAlert.TABLE_NAME).withRecord(new QRecord().withValue("id", alert.getValue("id")).withValue("status", "RESOLVED")));
      assertThat(updated.get(0).getErrors()).isNotEmpty();
      List<QRecord> inserted = InsertAction.executeForRecords(new InsertInput(AlertsEvent.TABLE_NAME).withRecord(new QRecord().withValue("alertId", alert.getValue("id")).withValue("eventType", "COMMENTED")));
      assertThat(inserted.get(0).getErrors()).isNotEmpty();

      runtime.getTypeSync().syncIfNeeded();
      QRecord type = runtime.asSystem(() -> AlertsDb.first(AlertsType.TABLE_NAME, AlertsDb.eq("name", TYPE)));
      List<QRecord> tuned = UpdateAction.executeForRecords(new UpdateInput(AlertsType.TABLE_NAME).withRecord(new QRecord().withValue("id", type.getValue("id")).withValue("notify", false)));
      assertThat(tuned.get(0).getErrors()).isEmpty();
   }



   /*******************************************************************************
    ** Run an action as the current user and return the Result lines.
    *******************************************************************************/
   private List<String> run(AlertsAction action, List<Serializable> ids, Map<String, Serializable> values) throws QException
   {
      RunProcessInput input = new RunProcessInput()
         .withProcessName(action.getProcessName())
         .withFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.SKIP)
         .withCallback(new com.kingsrook.qqq.backend.core.actions.processes.QProcessCallback()
         {
            @Override
            public QQueryFilter getQueryFilter()
            {
               return (new QQueryFilter().withCriteria("id", com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator.IN, ids));
            }



            @Override
            public Map<String, Serializable> getFieldValues(List<com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData> fields)
            {
               return (values);
            }
         });
      values.forEach(input::addValue);
      RunProcessOutput output = new RunProcessAction().execute(input);
      if(output.getException().isPresent())
      {
         throw (new QException(output.getException().get()));
      }
      @SuppressWarnings("unchecked")
      List<ProcessSummaryLineInterface> lines = (List<ProcessSummaryLineInterface>) output.getValue(StreamedETLWithFrontendProcess.FIELD_PROCESS_SUMMARY);
      List<String> text = new ArrayList<>();
      for(ProcessSummaryLineInterface line : lines == null ? List.<ProcessSummaryLineInterface>of() : lines)
      {
         text.add(line.getStatus() + ": " + (line instanceof com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLine summary ? summary.getCount() + " " + summary.getMessage() : String.valueOf(line)));
      }
      return (text);
   }



   /*******************************************************************************
    ** The alert actions end to end.
    *******************************************************************************/
   @Test
   void testAlertActions() throws Exception
   {
      List<Integer> widgets = insertWidgets("BROKEN");
      QRecord one = runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("1").withTitle("One").withRecordRef(AlertRecordRef.of(WIDGET, widgets.get(0))));
      QRecord two = runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("2").withTitle("Two"));
      List<Serializable> both = List.of(one.getValueInteger("id"), two.getValueInteger("id"));

      loginAs(MANAGER);
      assertThat(run(AlertsAction.ACKNOWLEDGE, List.of(one.getValueInteger("id")), Map.of())).anyMatch(line -> line.startsWith("OK: 1"));
      List<String> again = run(AlertsAction.ACKNOWLEDGE, both, Map.of());
      assertThat(again).anyMatch(line -> line.startsWith("OK: 1")).anyMatch(line -> line.startsWith("INFO: 1"));

      run(AlertsAction.SNOOZE, List.of(two.getValueInteger("id")), Map.of(AlertsAction.FIELD_SNOOZE_HOURS, 2));
      assertThat(runtime.asSystem(() -> AlertsDb.byId(AlertsAlert.TABLE_NAME, two.getValue("id"))).getValueString("status")).isEqualTo("SNOOZED");

      run(AlertsAction.ASSIGN, List.of(one.getValueInteger("id")), Map.of(AlertsAction.FIELD_ASSIGN_USER, VIEWER));
      run(AlertsAction.COMMENT, List.of(one.getValueInteger("id")), Map.of(AlertsAction.FIELD_NOTE, "On it"));
      run(AlertsAction.RESOLVE, both, Map.of(AlertsAction.FIELD_NOTE, "Fixed the widgets"));
      assertThat(where(AlertsAlert.TABLE_NAME, new QQueryFilter())).allMatch(alert -> "RESOLVED".equals(alert.getValueString("status")));
      run(AlertsAction.REOPEN, List.of(one.getValueInteger("id")), Map.of(AlertsAction.FIELD_NOTE, "Back"));
      assertThat(runtime.asSystem(() -> AlertsDb.byId(AlertsAlert.TABLE_NAME, one.getValue("id"))).getValueString("status")).isEqualTo("OPEN");

      run(AlertsAction.MUTE_ALERT, List.of(one.getValueInteger("id")), Map.of(AlertsAction.FIELD_MUTE_MINUTES, 30, AlertsAction.FIELD_NOTE, "Deploying"));
      assertThat(all(AlertsMute.TABLE_NAME)).hasSize(1);
      assertThat(all(AlertsEvent.TABLE_NAME)).extracting(event -> event.getValueString("eventType")).contains("ACKNOWLEDGED", "SNOOZED", "ASSIGNED", "COMMENTED", "RESOLVED", "REOPENED");

      ///////////////////////////////////////////////////////////
      // a viewer can't run manage actions                    //
      ///////////////////////////////////////////////////////////
      assertThat(com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper.hasProcessPermission(new com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput(),
         AlertsAction.ACKNOWLEDGE.getProcessName())).isTrue();
      loginAs(VIEWER);
      assertThat(com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper.hasProcessPermission(new com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput(),
         AlertsAction.ACKNOWLEDGE.getProcessName())).isFalse();
      assertThat(com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper.hasProcessPermission(new com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput(),
         AlertsAction.COMMENT.getProcessName())).isTrue();
   }



   /*******************************************************************************
    ** Type, group and monitor actions; channel and notification actions.
    *******************************************************************************/
   @Test
   void testAdminActions() throws Exception
   {
      runtime.getTypeSync().syncIfNeeded();
      QRecord type = runtime.asSystem(() -> AlertsDb.first(AlertsType.TABLE_NAME, AlertsDb.eq("name", TYPE)));

      loginAs(VIEWER);
      run(AlertsAction.SUBSCRIBE_TYPE, List.of(type.getValueInteger("id")), Map.of(AlertsAction.FIELD_MIN_SEVERITY, "ERROR", AlertsAction.FIELD_DELIVERY_MODE, "IMMEDIATE"));
      List<String> second = run(AlertsAction.SUBSCRIBE_TYPE, List.of(type.getValueInteger("id")), Map.of(AlertsAction.FIELD_MIN_SEVERITY, "ERROR", AlertsAction.FIELD_DELIVERY_MODE, "IMMEDIATE"));
      assertThat(second).anyMatch(line -> line.startsWith("INFO: 1"));
      assertThat(all(AlertsSubscription.TABLE_NAME)).hasSize(1);
      run(AlertsAction.SUBSCRIBE_GROUP, List.of(type.getValueInteger("groupId")), Map.of(AlertsAction.FIELD_MIN_SEVERITY, "WARNING", AlertsAction.FIELD_DELIVERY_MODE, "IMMEDIATE"));
      assertThat(all(AlertsSubscription.TABLE_NAME)).hasSize(2);

      loginAs(ADMIN);
      run(AlertsAction.CREATE_MONITOR, List.of(type.getValueInteger("id")), Map.of());
      QRecord monitor = all(AlertsMonitor.TABLE_NAME).stream().filter(m -> !Boolean.TRUE.equals(m.getValueBoolean("seeded"))).findFirst().orElseThrow();
      assertThat(monitor.getValueBoolean("enabled")).isFalse();
      assertThat(monitor.getValueString("kind")).isEqualTo("TABLE_QUERY");

      runtime.asSystem(() -> AlertsDb.update(AlertsMonitor.TABLE_NAME, new QRecord().withValue("id", monitor.getValue("id"))
         .withValue("definition", JsonUtils.toJson(Map.of("tableName", WIDGET)))));
      insertWidgets("BROKEN");
      run(AlertsAction.ENABLE_MONITOR, List.of(monitor.getValueInteger("id")), Map.of());
      List<String> check = run(AlertsAction.RUN_CHECK, List.of(monitor.getValueInteger("id")), Map.of());
      assertThat(check).anyMatch(line -> line.contains("Opened 1 alert"));
      run(AlertsAction.DISABLE_MONITOR, List.of(monitor.getValueInteger("id")), Map.of());
      assertThat(runtime.asSystem(() -> AlertsDb.byId(AlertsMonitor.TABLE_NAME, monitor.getValue("id"))).getValueBoolean("enabled")).isFalse();
      run(AlertsAction.SUBSCRIBE_MONITOR, List.of(monitor.getValueInteger("id")), Map.of(AlertsAction.FIELD_MIN_SEVERITY, "WARNING", AlertsAction.FIELD_DELIVERY_MODE, "IMMEDIATE"));
      run(AlertsAction.MUTE_MONITOR, List.of(monitor.getValueInteger("id")), Map.of(AlertsAction.FIELD_MUTE_MINUTES, 10, AlertsAction.FIELD_NOTE, "Tuning"));
      run(AlertsAction.MUTE_TYPE, List.of(type.getValueInteger("id")), Map.of(AlertsAction.FIELD_MUTE_MINUTES, 10, AlertsAction.FIELD_NOTE, "Tuning"));
      run(AlertsAction.MUTE_GROUP, List.of(type.getValueInteger("groupId")), Map.of(AlertsAction.FIELD_MUTE_MINUTES, 10, AlertsAction.FIELD_NOTE, "Tuning"));
      assertThat(all(AlertsMute.TABLE_NAME)).hasSize(3);

      runtime.getChannels().ensureDefaults();
      QRecord email = runtime.asSystem(() -> AlertsDb.first(AlertsChannel.TABLE_NAME, AlertsDb.eq("name", "email")));
      List<String> test = run(AlertsAction.SEND_TEST, List.of(email.getValueInteger("id")), Map.of());
      assertThat(test).anyMatch(line -> line.startsWith("ERROR"));
      QRecord failed = all(AlertsDelivery.TABLE_NAME).stream().filter(n -> "TEST".equals(n.getValueString("kind"))).findFirst().orElseThrow();
      assertThat(failed.getValueString("status")).isEqualTo("FAILED");
      assertThat(run(AlertsAction.RETRY_DELIVERY, List.of(failed.getValueInteger("id")), Map.of())).anyMatch(line -> line.startsWith("OK: 1"));
      assertThat(runtime.asSystem(() -> AlertsDb.byId(AlertsDelivery.TABLE_NAME, failed.getValue("id"))).getValueString("status")).isEqualTo("PENDING");
   }
}
