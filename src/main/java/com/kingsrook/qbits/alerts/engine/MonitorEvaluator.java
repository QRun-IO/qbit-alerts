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


import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.kingsrook.qbits.alerts.AlertsRuntime;
import com.kingsrook.qbits.alerts.AlertsSecurity;
import com.kingsrook.qbits.alerts.engine.AlertsEngine.AlertTarget;
import com.kingsrook.qbits.alerts.model.AlertsAlert;
import com.kingsrook.qbits.alerts.model.AlertsHeartbeat;
import com.kingsrook.qbits.alerts.model.AlertsMonitor;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.MonitorStatus;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.Resolution;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.Status;
import com.kingsrook.qbits.alerts.model.AlertsType;
import com.kingsrook.qqq.backend.core.actions.customizers.QCodeLoader;
import com.kingsrook.qqq.backend.core.actions.tables.AggregateAction;
import com.kingsrook.qqq.backend.core.alerts.AlertLink;
import com.kingsrook.qqq.backend.core.alerts.AlertMonitorKind;
import com.kingsrook.qqq.backend.core.alerts.AlertRecordRef;
import com.kingsrook.qqq.backend.core.alerts.AlertSeverity;
import com.kingsrook.qqq.backend.core.alerts.AlertSignal;
import com.kingsrook.qqq.backend.core.context.CapturedContext;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QUserFacingException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.aggregate.Aggregate;
import com.kingsrook.qqq.backend.core.model.actions.tables.aggregate.AggregateInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.aggregate.AggregateOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.aggregate.AggregateResult;
import com.kingsrook.qqq.backend.core.model.actions.tables.aggregate.GroupBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qqq.backend.core.utils.ValueUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** The monitor engine (design section 5.3). One hidden scheduled process
 ** claims due monitors (enabled, nextRunAt passed, not claimed by another
 ** evaluator), evaluates each with a timeout as the system user, and applies
 ** the result: raise what fires, resolve this monitor's open alerts that no
 ** longer do (a full-state diff), and record lastStatus / lastValue /
 ** lastError. A monitor that throws is CHECK_FAILED; three failures in a row
 ** raise alerts.monitor.failing, because a broken check is itself an
 ** exception to manage.
 **
 ** Kinds evaluated here: TABLE_QUERY, AGGREGATE, HEARTBEAT and CODE.
 ** PROCESS_FAILED is driven by process runs (AlertsProcessListener);
 ** ESB_EVENT and EXTERNAL are event-driven (later phases).
 **
 ** "Run check now" uses preview (the Review step: what would open, update
 ** and resolve, nothing written) and then runNow (the Result).
 *******************************************************************************/
public class MonitorEvaluator
{
   private static final QLogger LOG = QLogger.getLogger(MonitorEvaluator.class);

   public static final String MONITOR_FAILING_TYPE = "alerts.monitor.failing";

   private static final int FAILURES_BEFORE_META_ALERT = 3;
   private static final int DEFAULT_SCHEDULE_SECONDS   = 300;
   private static final int CLAIM_SECONDS              = 120;

   private final AlertsRuntime   runtime;
   private final String          evaluatorId = "evaluator-" + UUID.randomUUID().toString().substring(0, 8);
   private final ExecutorService executor    = Executors.newVirtualThreadPerTaskExecutor();



   /*******************************************************************************
    ** What an evaluation found (and, in a preview, what applying it would do).
    **
    ** @param status       OK, FIRING, PENDING (holding for forMinutes),
    **                     CHECK_FAILED or SKIPPED_MUTED
    ** @param value        a short description of the measured value
    ** @param signals      what fires now
    ** @param wouldOpen    dedup keys that would open (or reopen) an alert
    ** @param wouldUpdate  dedup keys whose open alert would be refreshed
    ** @param wouldResolve open alerts of this monitor that would be resolved
    ** @param error        the error, when the check failed
    *******************************************************************************/
   public record Plan(MonitorStatus status, String value, List<AlertSignal> signals, List<String> wouldOpen, List<String> wouldUpdate, List<QRecord> wouldResolve, String error)
   {
   }



   /*******************************************************************************
    ** Constructor
    *******************************************************************************/
   public MonitorEvaluator(AlertsRuntime runtime)
   {
      this.runtime = runtime;
   }



   /*******************************************************************************
    ** Evaluate every due monitor. Returns how many were evaluated.
    *******************************************************************************/
   public int evaluateDue() throws QException
   {
      runtime.getTypeSync().syncIfNeeded();
      List<QRecord> due = runtime.asSystem(() ->
      {
         Instant now = runtime.now();
         List<QRecord> candidates = AlertsDb.query(AlertsMonitor.TABLE_NAME, AlertsDb.eq("enabled", true)
            .withCriteria("kind", QCriteriaOperator.IN, List.of(AlertMonitorKind.TABLE_QUERY.name(), AlertMonitorKind.AGGREGATE.name(), AlertMonitorKind.HEARTBEAT.name(), AlertMonitorKind.CODE.name()))
            .withOrderBy(new QFilterOrderBy("id")));
         List<QRecord> claimed = new ArrayList<>();
         for(QRecord monitor : candidates)
         {
            Instant next  = monitor.getValueInstant("nextRunAt");
            Instant until = monitor.getValueInstant("claimedUntil");
            if((next == null || !next.isAfter(now)) && (until == null || until.isBefore(now)))
            {
               AlertsDb.update(AlertsMonitor.TABLE_NAME, new QRecord().withValue("id", monitor.getValue("id")).withValue("claimedBy", evaluatorId)
                  .withValue("claimedUntil", now.plusSeconds(CLAIM_SECONDS)));
               claimed.add(monitor);
            }
         }
         return (claimed);
      });

      for(QRecord monitor : due)
      {
         evaluate(monitor.getValueInteger("id"), true);
      }
      runtime.getEngine().maintenance();
      return (due.size());
   }



   /*******************************************************************************
    ** What evaluating the monitor now would do, without writing alerts.
    *******************************************************************************/
   public Plan preview(Integer monitorId) throws QException
   {
      return (evaluate(monitorId, false));
   }



   /*******************************************************************************
    ** Evaluate the monitor now and apply the result.
    *******************************************************************************/
   public Plan runNow(Integer monitorId) throws QException
   {
      return (evaluate(monitorId, true));
   }



   /*******************************************************************************
    ** Evaluate one monitor, applying the result or only planning it.
    *******************************************************************************/
   Plan evaluate(Integer monitorId, boolean apply) throws QException
   {
      runtime.getTypeSync().syncIfNeeded();
      return (runtime.asSystem(() ->
      {
         QRecord monitor = AlertsDb.byId(AlertsMonitor.TABLE_NAME, monitorId);
         if(monitor == null)
         {
            throw (new QUserFacingException("The monitor was not found."));
         }
         QRecord type = AlertsDb.byId(AlertsType.TABLE_NAME, monitor.getValue("alertTypeId"));
         if(type == null)
         {
            throw (new QUserFacingException("The monitor's alert type was not found."));
         }

         Instant now     = runtime.now();
         long    started = System.currentTimeMillis();
         String  typeName = type.getValueString("name");

         AlertTarget target = new AlertTarget(type.getValueInteger("id"), typeName, type.getValueInteger("groupId"), monitorId, type.getValueString("subjectTableName"), Set.of(), Map.of(), null);
         if(runtime.getRouter().isSuppressedByMaintenance(target, now))
         {
            Plan plan = new Plan(MonitorStatus.SKIPPED_MUTED, "skipped: maintenance window", List.of(), List.of(), List.of(), List.of(), null);
            if(apply)
            {
               finish(monitor, plan, now, started);
            }
            return (plan);
         }

         List<AlertSignal> signals;
         try
         {
            signals = signalsWithTimeout(monitor, typeName, now);
         }
         catch(Exception e)
         {
            String error = rootMessage(e);
            Plan   plan  = new Plan(MonitorStatus.CHECK_FAILED, null, List.of(), List.of(), List.of(), List.of(), error);
            if(apply)
            {
               finish(monitor, plan, now, started);
            }
            return (plan);
         }

         AlertMonitorKind kind      = AlertMonitorKind.valueOf(monitor.getValueString("kind"));
         boolean          fullState = kind != AlertMonitorKind.PROCESS_FAILED && kind != AlertMonitorKind.ESB_EVENT && kind != AlertMonitorKind.EXTERNAL;
         String           value     = signals.isEmpty() ? lastValue(kind, monitor) : describe(signals);

         ////////////////////////////////////////////////////////////////////////
         // forMinutes: the condition must hold this long before it fires     //
         ////////////////////////////////////////////////////////////////////////
         Integer forMinutes = monitor.getValueInteger("forMinutes");
         Instant since      = monitor.getValueInstant("conditionSince");
         boolean pending    = false;
         if(!signals.isEmpty() && forMinutes != null && forMinutes > 0)
         {
            Instant start = since == null ? now : since;
            pending = now.isBefore(start.plus(forMinutes, ChronoUnit.MINUTES));
         }

         List<String>  wouldOpen    = new ArrayList<>();
         List<String>  wouldUpdate  = new ArrayList<>();
         List<QRecord> wouldResolve = new ArrayList<>();
         Set<String>   keys         = new HashSet<>();
         for(AlertSignal signal : signals)
         {
            String signalType = signal.getAlertTypeName() == null ? typeName : signal.getAlertTypeName();
            signal.setAlertTypeName(signalType);
            keys.add(AlertsEngine.openKey(signalType, signal.getEffectiveDedupKey()));
            if(AlertsDb.first(AlertsAlert.TABLE_NAME, AlertsDb.eq("openDedupKey", AlertsEngine.openKey(signalType, signal.getEffectiveDedupKey()))) != null)
            {
               wouldUpdate.add(signal.getEffectiveDedupKey());
            }
            else
            {
               wouldOpen.add(signal.getEffectiveDedupKey());
            }
         }
         if(fullState && !pending)
         {
            for(QRecord open : AlertsDb.query(AlertsAlert.TABLE_NAME, AlertsDb.eq("monitorId", monitorId).withCriteria("status", QCriteriaOperator.IN, Status.openNames())))
            {
               if(!keys.contains(open.getValueString("openDedupKey")))
               {
                  wouldResolve.add(open);
               }
            }
         }

         MonitorStatus status = signals.isEmpty() ? MonitorStatus.OK : (pending ? MonitorStatus.PENDING : MonitorStatus.FIRING);
         Plan          plan   = new Plan(status, value, signals, pending ? List.of() : wouldOpen, pending ? List.of() : wouldUpdate, wouldResolve, null);
         if(!apply)
         {
            return (plan);
         }

         if(!pending)
         {
            for(AlertSignal signal : signals)
            {
               runtime.getEngine().raise(signal, monitorId);
            }
         }
         for(QRecord open : wouldResolve)
         {
            runtime.getEngine().resolveById(open.getValueInteger("id"), Resolution.MONITOR, null, "The monitor's latest check was clean");
         }
         finish(monitor.withValue("conditionSince", signals.isEmpty() ? null : (since == null ? now : since)), plan, now, started);
         return (plan);
      }));
   }



   /*******************************************************************************
    ** A PROCESS_FAILED monitor's process failed: raise.
    *******************************************************************************/
   public void processFailed(String processName, String processLabel, String error) throws QException
   {
      for(QRecord monitor : processMonitors(processName))
      {
         QRecord type = runtime.asSystem(() -> AlertsDb.byId(AlertsType.TABLE_NAME, monitor.getValue("alertTypeId")));
         if(type == null)
         {
            continue;
         }
         runtime.getEngine().raise(new AlertSignal(type.getValueString("name"))
            .withDedupKey(processName)
            .withSeverity(AlertSeverity.fromLabel(monitor.getValueString("severity"), null))
            .withTitle((StringUtils.hasContent(processLabel) ? processLabel : processName) + " failed")
            .withSummary("The latest run failed.")
            .withDetails(error)
            .withAttribute("processName", processName), monitor.getValueInteger("id"));
      }
   }



   /*******************************************************************************
    ** A PROCESS_FAILED monitor's process completed: resolve.
    *******************************************************************************/
   public void processCompleted(String processName) throws QException
   {
      for(QRecord monitor : processMonitors(processName))
      {
         QRecord type = runtime.asSystem(() -> AlertsDb.byId(AlertsType.TABLE_NAME, monitor.getValue("alertTypeId")));
         if(type != null)
         {
            runtime.getEngine().resolve(type.getValueString("name"), processName, Resolution.MONITOR, null, "A later run completed");
         }
      }
   }



   /*******************************************************************************
    ** Enabled PROCESS_FAILED monitors for a process (definition processName,
    ** exact or a * pattern).
    *******************************************************************************/
   private List<QRecord> processMonitors(String processName) throws QException
   {
      List<QRecord> matches = new ArrayList<>();
      for(QRecord monitor : runtime.asSystem(() -> AlertsDb.query(AlertsMonitor.TABLE_NAME, AlertsDb.eq("enabled", true)
         .withCriteria("kind", QCriteriaOperator.EQUALS, AlertMonitorKind.PROCESS_FAILED.name()))))
      {
         String pattern = ValueUtils.getValueAsString(definition(monitor).get("processName"));
         if(pattern != null && processName.matches(pattern.replace(".", "\\.").replace("*", ".*")))
         {
            matches.add(monitor);
         }
      }
      return (matches);
   }



   /*******************************************************************************
    ** Run the kind's check with the configured timeout.
    *******************************************************************************/
   private List<AlertSignal> signalsWithTimeout(QRecord monitor, String typeName, Instant now) throws Exception
   {
      CapturedContext context = QContext.capture();
      Future<List<AlertSignal>> future = executor.submit(() ->
      {
         List<AlertSignal>[] result = new List[1];
         QContext.withTemporaryContext(context, () -> result[0] = signals(monitor, typeName, now));
         return (result[0]);
      });
      try
      {
         return (future.get(runtime.getConfig().getMonitorTimeoutSeconds(), TimeUnit.SECONDS));
      }
      catch(TimeoutException e)
      {
         future.cancel(true);
         throw (new QException("The check took longer than " + runtime.getConfig().getMonitorTimeoutSeconds() + " seconds."));
      }
   }



   /*******************************************************************************
    ** What fires now, by kind.
    *******************************************************************************/
   List<AlertSignal> signals(QRecord monitor, String typeName, Instant now) throws QException
   {
      AlertMonitorKind          kind       = AlertMonitorKind.valueOf(monitor.getValueString("kind"));
      Map<String, Serializable> definition = definition(monitor);
      return switch(kind)
      {
         case TABLE_QUERY -> tableQuery(monitor, typeName, definition);
         case AGGREGATE -> aggregate(monitor, typeName, definition);
         case HEARTBEAT -> heartbeat(monitor, typeName, definition, now);
         case CODE -> code(monitor, typeName, definition, now);
         case PROCESS_FAILED, ESB_EVENT, EXTERNAL -> List.of();
      };
   }



   /*******************************************************************************
    ** TABLE_QUERY: rows matching a filter pass a threshold (default: any).
    *******************************************************************************/
   private List<AlertSignal> tableQuery(QRecord monitor, String typeName, Map<String, Serializable> definition) throws QException
   {
      String         tableName = requireTable(definition);
      QQueryFilter   filter    = filter(definition);
      int            threshold = intValue(definition.get("threshold"), 0);
      QTableMetaData table     = runtime.getQInstance().getTable(tableName);
      int            count     = AlertsDb.count(tableName, filter.clone());
      if(count <= threshold)
      {
         return (List.of());
      }

      String  labelField = ValueUtils.getValueAsString(definition.get("labelField"));
      String  name       = monitor.getValueString("name");
      boolean perRecord  = Boolean.TRUE.equals(monitor.getValueBoolean("perRecord"));
      int     maxOpen    = runtime.getConfig().getMaxOpenPerMonitor();

      List<QRecord> rows = AlertsDb.query(tableName, filter.clone().withLimit(perRecord ? maxOpen + 1 : AlertSignal.MAX_RECORD_REFS));
      if(perRecord && count <= maxOpen)
      {
         List<AlertSignal> signals = new ArrayList<>();
         for(QRecord row : rows)
         {
            Serializable id    = row.getValue(table.getPrimaryKeyField());
            String       label = labelField == null ? null : row.getValueString(labelField);
            signals.add(base(monitor, typeName)
               .withDedupKey("record:" + id)
               .withTitle(name + ": " + (label != null && !label.isBlank() ? label : table.getLabel() + " " + id))
               .withRecordRef(AlertRecordRef.of(tableName, id).withLabel(label))
               .withLink(AlertLink.toRecord(tableName, id, "Open the record")));
         }
         return (signals);
      }

      List<AlertRecordRef> refs = new ArrayList<>();
      for(QRecord row : rows)
      {
         Serializable id = row.getValue(table.getPrimaryKeyField());
         refs.add(AlertRecordRef.of(tableName, id).withLabel(labelField == null ? null : row.getValueString(labelField)));
      }
      AlertSignal signal = base(monitor, typeName)
         .withDedupKey("monitor:" + monitor.getValue("id"))
         .withTitle(name + ": " + plural(count, table.getLabel()))
         .withSummary(perRecord ? "More than " + maxOpen + " records match, so they are grouped into one alert." : null)
         .withRecordRefs(refs)
         .withCount(count)
         .withLink(AlertLink.toTable(tableName, filter(definition), "Matching records"));
      return (List.of(signal));
   }



   /*******************************************************************************
    ** AGGREGATE: an aggregate (optionally per group) crosses a band.
    *******************************************************************************/
   private List<AlertSignal> aggregate(QRecord monitor, String typeName, Map<String, Serializable> definition) throws QException
   {
      String            tableName  = requireTable(definition);
      AggregateOperator operator   = AggregateOperator.valueOf(Objects.requireNonNullElse(ValueUtils.getValueAsString(definition.get("aggregate")), "COUNT").toUpperCase(Locale.ROOT));
      String            fieldName  = Objects.requireNonNullElse(ValueUtils.getValueAsString(definition.get("fieldName")), runtime.getQInstance().getTable(tableName).getPrimaryKeyField());
      String            groupField = ValueUtils.getValueAsString(definition.get("groupBy"));
      String            comparator = Objects.requireNonNullElse(ValueUtils.getValueAsString(definition.get("comparator")), "GT").toUpperCase(Locale.ROOT);

      Aggregate      aggregate = new Aggregate(fieldName, operator);
      AggregateInput input     = new AggregateInput(tableName).withFilter(filter(definition)).withAggregate(aggregate);
      GroupBy        groupBy   = null;
      if(groupField != null)
      {
         QFieldMetaData field = runtime.getQInstance().getTable(tableName).getField(groupField);
         groupBy = new GroupBy(field.getType(), groupField);
         input.withGroupBy(groupBy);
      }

      List<AlertSignal> signals = new ArrayList<>();
      for(AggregateResult result : new AggregateAction().execute(input).getResults())
      {
         BigDecimal value = ValueUtils.getValueAsBigDecimal(result.getAggregateValue(aggregate));
         if(value == null)
         {
            value = BigDecimal.ZERO;
         }
         AlertSeverity severity = band(value, comparator, definition);
         if(severity == null)
         {
            continue;
         }
         String group = groupBy == null ? null : String.valueOf(result.getGroupByValue(groupBy));
         signals.add(base(monitor, typeName)
            .withSeverity(severity)
            .withDedupKey(group == null ? "aggregate" : "group:" + group)
            .withTitle(monitor.getValueString("name") + (group == null ? "" : " (" + group + ")") + ": " + operator.name().toLowerCase(Locale.ROOT) + " " + value.stripTrailingZeros().toPlainString())
            .withAttribute("value", value.toPlainString())
            .withLink(AlertLink.toTable(tableName, filter(definition), "Records")));
      }
      return (signals);
   }



   /*******************************************************************************
    ** HEARTBEAT: nothing seen (a beat, a process success, a newest row) within
    ** the expected interval plus grace.
    *******************************************************************************/
   private List<AlertSignal> heartbeat(QRecord monitor, String typeName, Map<String, Serializable> definition, Instant now) throws QException
   {
      int expected = intValue(definition.get("expectedEverySeconds"), 0);
      int grace    = intValue(definition.get("graceSeconds"), 0);
      if(expected <= 0)
      {
         throw (new QException("A heartbeat monitor needs expectedEverySeconds."));
      }

      Instant last;
      String  what;
      String  heartbeatKey = ValueUtils.getValueAsString(definition.get("heartbeatKey"));
      String  processName  = ValueUtils.getValueAsString(definition.get("processName"));
      String  tableName    = ValueUtils.getValueAsString(definition.get("tableName"));
      if(heartbeatKey != null || processName != null)
      {
         String  key = heartbeatKey != null ? heartbeatKey : processHeartbeatKey(processName);
         QRecord row = AlertsDb.first(AlertsHeartbeat.TABLE_NAME, AlertsDb.eq("heartbeatKey", key));
         last = row == null ? null : row.getValueInstant("lastBeatAt");
         what = processName != null ? "a successful run of " + processName : "a heartbeat from " + key;
      }
      else if(tableName != null)
      {
         String  dateField = Objects.requireNonNullElse(ValueUtils.getValueAsString(definition.get("dateField")), "createDate");
         QRecord newest    = AlertsDb.first(tableName, filter(definition).withOrderBy(new QFilterOrderBy(dateField, false)));
         last = newest == null ? null : ValueUtils.getValueAsInstant(newest.getValue(dateField));
         what = "a new " + runtime.getQInstance().getTable(tableName).getLabel() + " row";
      }
      else
      {
         throw (new QException("A heartbeat monitor needs heartbeatKey, processName or tableName."));
      }

      Instant baseline = last != null ? last : monitor.getValueInstant("createDate");
      if(baseline == null || !now.isAfter(baseline.plusSeconds((long) expected + grace)))
      {
         return (List.of());
      }
      String age = last == null ? "yet" : "for " + com.kingsrook.qbits.alerts.delivery.AlertMessageRenderer.age(last, now);
      return (List.of(base(monitor, typeName)
         .withDedupKey("monitor:" + monitor.getValue("id"))
         .withTitle(monitor.getValueString("name") + ": no " + what.replaceFirst("^an? ", "") + " " + age)
         .withSummary("Expected " + what + " every " + com.kingsrook.qbits.alerts.delivery.AlertMessageRenderer.age(now.minusSeconds(expected), now) + ".")));
   }



   /*******************************************************************************
    ** CODE: an AlertCheckInterface returns what is failing.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   private List<AlertSignal> code(QRecord monitor, String typeName, Map<String, Serializable> definition, Instant now) throws QException
   {
      String className = ValueUtils.getValueAsString(definition.get("codeReference"));
      if(className == null)
      {
         throw (new QException("A code monitor needs codeReference (a class implementing AlertCheckInterface)."));
      }
      AlertCheckInterface check;
      try
      {
         check = QCodeLoader.getAdHoc(AlertCheckInterface.class, new QCodeReference(Class.forName(className)));
      }
      catch(ClassNotFoundException e)
      {
         throw (new QException("The check class was not found: " + className));
      }
      Map<String, Serializable> params = definition.get("params") instanceof Map<?, ?> map ? (Map<String, Serializable>) map : Map.of();
      List<AlertSignal>         result = check.check(new AlertCheckInterface.Context(monitor, typeName, params, now));
      List<AlertSignal>         signals = new ArrayList<>();
      for(AlertSignal signal : result == null ? List.<AlertSignal>of() : result)
      {
         if(signal != null)
         {
            if(signal.getSeverity() == null)
            {
               signal.setSeverity(AlertSeverity.fromLabel(monitor.getValueString("severity"), null));
            }
            signals.add(signal);
         }
      }
      return (signals);
   }



   /*******************************************************************************
    ** Record the run on the monitor (and the failing meta-alert).
    *******************************************************************************/
   private void finish(QRecord monitor, Plan plan, Instant now, long started) throws QException
   {
      Integer monitorId = monitor.getValueInteger("id");
      int     schedule  = monitor.getValueInteger("scheduleSeconds") == null ? DEFAULT_SCHEDULE_SECONDS : Math.max(10, monitor.getValueInteger("scheduleSeconds"));
      int     failures  = plan.status() == MonitorStatus.CHECK_FAILED ? (monitor.getValueInteger("consecutiveFailures") == null ? 0 : monitor.getValueInteger("consecutiveFailures")) + 1 : 0;

      QRecord update = new QRecord()
         .withValue("id", monitorId)
         .withValue("lastRunAt", now)
         .withValue("lastStatus", plan.status().name())
         .withValue("lastValue", truncate(plan.value(), 200))
         .withValue("lastError", truncate(plan.error(), 1900))
         .withValue("lastElapsedMs", (int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - started))
         .withValue("consecutiveFailures", failures)
         .withValue("nextRunAt", now.plusSeconds(schedule))
         .withValue("claimedBy", null)
         .withValue("claimedUntil", null);
      if(plan.status() != MonitorStatus.CHECK_FAILED)
      {
         update.setValue("conditionSince", monitor.getValue("conditionSince"));
      }
      AlertsDb.update(AlertsMonitor.TABLE_NAME, update);

      if(failures == FAILURES_BEFORE_META_ALERT)
      {
         LOG.warn("Alerts monitor is failing", logPair("monitor", monitor.getValueString("name")), logPair("error", plan.error()));
         runtime.getEngine().raise(new AlertSignal(MONITOR_FAILING_TYPE)
            .withDedupKey("monitor:" + monitorId)
            .withSeverity(AlertSeverity.ERROR)
            .withTitle("Monitor \"" + monitor.getValueString("name") + "\" cannot run")
            .withSummary(failures + " checks in a row failed.")
            .withDetails(plan.error())
            .withRecordRef(AlertRecordRef.of(AlertsMonitor.TABLE_NAME, monitorId))
            .withLink(AlertLink.toRecord(AlertsMonitor.TABLE_NAME, monitorId, "Open the monitor")));
      }
      else if(failures == 0 && monitor.getValueInteger("consecutiveFailures") != null && monitor.getValueInteger("consecutiveFailures") >= FAILURES_BEFORE_META_ALERT)
      {
         runtime.getEngine().resolve(MONITOR_FAILING_TYPE, "monitor:" + monitorId, Resolution.PRODUCER, null, "The monitor ran again");
      }
   }



   /*******************************************************************************
    ** A signal for the monitor's type and severity.
    *******************************************************************************/
   private static AlertSignal base(QRecord monitor, String typeName)
   {
      return (new AlertSignal(typeName).withSeverity(AlertSeverity.fromLabel(monitor.getValueString("severity"), null)));
   }



   /*******************************************************************************
    ** The monitor's definition JSON as a map (empty when blank).
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   static Map<String, Serializable> definition(QRecord monitor)
   {
      String json = monitor.getValueString("definition");
      if(!StringUtils.hasContent(json))
      {
         return (new LinkedHashMap<>());
      }
      try
      {
         return (JsonUtils.toObject(json, LinkedHashMap.class));
      }
      catch(Exception e)
      {
         throw (new IllegalArgumentException("The monitor's definition is not valid JSON: " + e.getMessage()));
      }
   }



   /*******************************************************************************
    ** The definition's filter (a QQueryFilter, as an object or a JSON string).
    *******************************************************************************/
   private static QQueryFilter filter(Map<String, Serializable> definition) throws QException
   {
      Object filter = definition.get("filter");
      if(filter == null)
      {
         return (new QQueryFilter());
      }
      try
      {
         String json = filter instanceof String string ? string : JsonUtils.toJson(filter);
         return (JsonUtils.toObject(json, QQueryFilter.class));
      }
      catch(Exception e)
      {
         throw (new QException("The monitor's filter is not a valid QQueryFilter: " + e.getMessage()));
      }
   }



   /*******************************************************************************
    ** The definition's tableName, which must be a table in the instance.
    *******************************************************************************/
   private String requireTable(Map<String, Serializable> definition) throws QException
   {
      String tableName = ValueUtils.getValueAsString(definition.get("tableName"));
      if(tableName == null || runtime.getQInstance().getTable(tableName) == null)
      {
         throw (new QException("The monitor's tableName is missing or not a table: " + tableName));
      }
      return (tableName);
   }



   /*******************************************************************************
    ** The severity band an aggregate value falls in (null: none).
    *******************************************************************************/
   private static AlertSeverity band(BigDecimal value, String comparator, Map<String, Serializable> definition)
   {
      List<Map.Entry<AlertSeverity, String>> bands = List.of(
         Map.entry(AlertSeverity.CRITICAL, "criticalThreshold"),
         Map.entry(AlertSeverity.ERROR, "errorThreshold"),
         Map.entry(AlertSeverity.WARNING, "warningThreshold"));
      for(Map.Entry<AlertSeverity, String> band : bands)
      {
         BigDecimal threshold = ValueUtils.getValueAsBigDecimal(definition.get(band.getValue()));
         if(threshold != null && compare(value, comparator, threshold))
         {
            return (band.getKey());
         }
      }
      return (null);
   }



   /*******************************************************************************
    ** value (comparator) threshold.
    *******************************************************************************/
   private static boolean compare(BigDecimal value, String comparator, BigDecimal threshold)
   {
      int c = value.compareTo(threshold);
      return switch(comparator)
      {
         case "GTE", ">=" -> c >= 0;
         case "LT", "<" -> c < 0;
         case "LTE", "<=" -> c <= 0;
         default -> c > 0;
      };
   }



   /*******************************************************************************
    ** The heartbeat key a process's successful runs beat.
    *******************************************************************************/
   public static String processHeartbeatKey(String processName)
   {
      String hyphenated = Objects.requireNonNullElse(processName, "")
         .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
         .replaceAll("[^A-Za-z0-9]+", "-")
         .toLowerCase(Locale.ROOT)
         .replaceAll("^-+|-+$", "");
      return ("process." + (hyphenated.isEmpty() ? "unnamed" : hyphenated));
   }



   /*******************************************************************************
    ** A short description of what fired.
    *******************************************************************************/
   private static String describe(List<AlertSignal> signals)
   {
      if(signals.size() == 1)
      {
         AlertSignal signal = signals.get(0);
         return (signal.getEffectiveCount() != null ? plural(signal.getEffectiveCount(), "match") : signal.getTitle());
      }
      return (signals.size() + " alerts");
   }



   /*******************************************************************************
    ** The value to show when nothing fired.
    *******************************************************************************/
   private static String lastValue(AlertMonitorKind kind, QRecord monitor)
   {
      return switch(kind)
      {
         case PROCESS_FAILED, ESB_EVENT, EXTERNAL -> "event-driven";
         default -> "clean";
      };
   }



   /*******************************************************************************
    ** "1 record" / "3 records" (labels are lower-cased).
    *******************************************************************************/
   static String plural(int count, String noun)
   {
      String lower = noun == null ? "row" : noun.toLowerCase(Locale.ROOT);
      return (count + " " + (count == 1 ? lower : (lower.endsWith("s") || lower.endsWith("h") ? lower + "es" : lower + "s")));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static int intValue(Object value, int fallback)
   {
      Integer integer = ValueUtils.getValueAsInteger(value);
      return (integer == null ? fallback : integer);
   }



   /*******************************************************************************
    ** The innermost message.
    *******************************************************************************/
   private static String rootMessage(Throwable throwable)
   {
      Throwable root = throwable;
      while(root.getCause() != null && root.getCause() != root)
      {
         root = root.getCause();
      }
      String message = root.getMessage();
      return (message == null ? root.getClass().getSimpleName() : message);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String truncate(String text, int max)
   {
      return (text == null || text.length() <= max ? text : text.substring(0, max - 1) + "…");
   }



   /*******************************************************************************
    ** The id this evaluator claims with.
    *******************************************************************************/
   public String getEvaluatorId()
   {
      return (evaluatorId);
   }



   /*******************************************************************************
    ** Whether the current session is the system user (for tests).
    *******************************************************************************/
   static boolean isSystem()
   {
      return (AlertsSecurity.isSystemUser());
   }
}
