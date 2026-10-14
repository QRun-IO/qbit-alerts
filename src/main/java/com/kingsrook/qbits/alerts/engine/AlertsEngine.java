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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.kingsrook.qbits.alerts.AlertsQBitConfig;
import com.kingsrook.qbits.alerts.AlertsRuntime;
import com.kingsrook.qbits.alerts.model.AlertsAlert;
import com.kingsrook.qbits.alerts.model.AlertsEvent;
import com.kingsrook.qbits.alerts.model.AlertsHeartbeat;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.EventType;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.Resolution;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.Status;
import com.kingsrook.qbits.alerts.model.AlertsRecordRef;
import com.kingsrook.qbits.alerts.model.AlertsType;
import com.kingsrook.qqq.backend.core.alerts.AlertLink;
import com.kingsrook.qqq.backend.core.alerts.AlertRecordRef;
import com.kingsrook.qqq.backend.core.alerts.AlertSeverity;
import com.kingsrook.qqq.backend.core.alerts.AlertSignal;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QUserFacingException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qqq.backend.core.utils.ValueUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** The alert state machine (design section 5.1). Every transition writes its
 ** alerts_event, and notifiable events go to the router.
 **
 ** - Raise on an open (type, dedupKey) updates that alert: lastRaisedAt,
 **   occurrences, record refs. A severity increase records SEVERITY_CHANGED,
 **   unsnoozes and notifies again; a decrease only records it.
 ** - Raise on a key resolved less than reopenWindowMinutes ago reopens the
 **   same alert; later, a new alert is created (history stays on the old one).
 ** - Flapping: flapThreshold open/resolve changes within flapWindowMinutes
 **   set flapping (one notice; further open/resolve notifications are
 **   suppressed until it is stable for the window).
 ** - Acknowledge stops reminders; snooze hides it until a time (it returns
 **   early when the severity rises); resolve closes it from any open state.
 ** - Concurrency: openDedupKey (type|key while not resolved) is unique, so an
 **   insert that loses a race re-reads and updates the winner.
 *******************************************************************************/
public class AlertsEngine
{
   private static final QLogger LOG = QLogger.getLogger(AlertsEngine.class);

   private static final int REPEATED_EVENT_EVERY_MINUTES = 60;
   private static final int MAX_KEY_LENGTH               = 520;

   private final AlertsRuntime runtime;



   /*******************************************************************************
    ** Constructor
    *******************************************************************************/
   public AlertsEngine(AlertsRuntime runtime)
   {
      this.runtime = runtime;
   }



   /*******************************************************************************
    ** The unique open key of (type, dedupKey).
    *******************************************************************************/
   public static String openKey(String alertTypeName, String dedupKey)
   {
      String key = alertTypeName + "|" + dedupKey;
      return (key.length() > MAX_KEY_LENGTH ? key.substring(0, MAX_KEY_LENGTH) : key);
   }



   /*******************************************************************************
    ** Raise (or refresh) an alert from a producer signal.
    *******************************************************************************/
   public QRecord raise(AlertSignal signal) throws QException
   {
      return (raise(signal, null));
   }



   /*******************************************************************************
    ** Raise (or refresh) an alert, optionally on behalf of a monitor. Returns
    ** the alert row, or null when the type is disabled or a maintenance window
    ** suppresses it.
    *******************************************************************************/
   public QRecord raise(AlertSignal signal, Integer monitorId) throws QException
   {
      if(signal == null || !StringUtils.hasContent(signal.getAlertTypeName()))
      {
         return (null);
      }

      runtime.getTypeSync().syncIfNeeded();
      return (runtime.asSystem(() ->
      {
         Instant now  = signal.getOccurredAt() == null ? runtime.now() : signal.getOccurredAt();
         QRecord type = runtime.getTypeSync().typeFor(signal.getAlertTypeName());
         if(Boolean.FALSE.equals(type.getValueBoolean("enabled")))
         {
            return (null);
         }

         String        dedupKey = signal.getEffectiveDedupKey();
         AlertSeverity severity = effectiveSeverity(type, signal.getSeverity());
         AlertTarget   target   = AlertTarget.of(type, signal, monitorId);
         if(runtime.getRouter().isSuppressedByMaintenance(target, now))
         {
            LOG.info("Alert suppressed by a maintenance window", logPair("alertType", signal.getAlertTypeName()), logPair("dedupKey", dedupKey));
            return (null);
         }

         String  openKey = openKey(signal.getAlertTypeName(), dedupKey);
         QRecord open    = AlertsDb.first(AlertsAlert.TABLE_NAME, AlertsDb.eq("openDedupKey", openKey));
         QRecord alert;
         if(open != null)
         {
            alert = refresh(open, type, signal, severity, monitorId, now);
         }
         else
         {
            QRecord recent = recentlyResolved(signal.getAlertTypeName(), dedupKey, now);
            if(recent != null)
            {
               alert = reopen(recent, type, signal, severity, monitorId, now, null, "Raised again within the reopen window");
            }
            else
            {
               alert = create(type, signal, dedupKey, openKey, severity, monitorId, now);
            }
         }

         updateTypeCounts(type.getValueInteger("id"), now);
         return (alert);
      }));
   }



   /*******************************************************************************
    ** Resolve the open alert for (type, dedupKey), if there is one.
    *******************************************************************************/
   public QRecord resolve(String alertTypeName, String dedupKey, Resolution resolution, String userId, String note) throws QException
   {
      if(!StringUtils.hasContent(alertTypeName))
      {
         return (null);
      }
      String key = StringUtils.hasContent(dedupKey) ? dedupKey : alertTypeName;
      return (runtime.asSystem(() ->
      {
         QRecord open = AlertsDb.first(AlertsAlert.TABLE_NAME, AlertsDb.eq("openDedupKey", openKey(alertTypeName, key)));
         if(open == null)
         {
            return (null);
         }
         return (resolveAlert(open, resolution, userId, note));
      }));
   }



   /*******************************************************************************
    ** Resolve an alert by id (manual Resolve, monitor clear). A no-op when it is
    ** already resolved.
    *******************************************************************************/
   public QRecord resolveById(Integer alertId, Resolution resolution, String userId, String note) throws QException
   {
      return (runtime.asSystem(() ->
      {
         QRecord alert = AlertsDb.byId(AlertsAlert.TABLE_NAME, alertId);
         if(alert == null || Status.RESOLVED.name().equals(alert.getValueString("status")))
         {
            return (alert);
         }
         return (resolveAlert(alert, resolution, userId, note));
      }));
   }



   /*******************************************************************************
    ** Record a dead-man's switch beat.
    *******************************************************************************/
   public void heartbeat(String heartbeatKey, Instant at) throws QException
   {
      if(!StringUtils.hasContent(heartbeatKey))
      {
         return;
      }
      runtime.asSystem(() ->
      {
         Instant when     = at == null ? runtime.now() : at;
         QRecord existing = AlertsDb.first(AlertsHeartbeat.TABLE_NAME, AlertsDb.eq("heartbeatKey", heartbeatKey));
         if(existing == null)
         {
            AlertsDb.insert(AlertsHeartbeat.TABLE_NAME, new QRecord().withValue("heartbeatKey", heartbeatKey).withValue("lastBeatAt", when).withValue("beatCount", 1));
         }
         else
         {
            AlertsDb.update(AlertsHeartbeat.TABLE_NAME, new QRecord().withValue("id", existing.getValue("id")).withValue("lastBeatAt", when)
               .withValue("beatCount", ValueUtils.getValueAsInteger(existing.getValue("beatCount")) == null ? 1 : existing.getValueInteger("beatCount") + 1));
         }
         return (null);
      });
   }



   /*******************************************************************************
    ** Acknowledge an open alert. Returns false when it was already
    ** acknowledged or is not open.
    *******************************************************************************/
   public boolean acknowledge(Integer alertId, String userId) throws QException
   {
      return (runtime.asSystem(() ->
      {
         QRecord alert = AlertsDb.byId(AlertsAlert.TABLE_NAME, alertId);
         if(alert == null || !Status.OPEN.name().equals(alert.getValueString("status")))
         {
            return (false);
         }
         Instant now = runtime.now();
         AlertsDb.update(AlertsAlert.TABLE_NAME, new QRecord().withValue("id", alertId).withValue("status", Status.ACKNOWLEDGED.name())
            .withValue("acknowledgedByUserId", userId).withValue("acknowledgedAt", now));
         event(alertId, EventType.ACKNOWLEDGED, userId, Status.OPEN.name(), Status.ACKNOWLEDGED.name(), null, now);
         return (true);
      }));
   }



   /*******************************************************************************
    ** Snooze an open or acknowledged alert until a time.
    *******************************************************************************/
   public boolean snooze(Integer alertId, Instant until, String userId) throws QException
   {
      if(until == null)
      {
         throw (new QUserFacingException("Choose when the snooze ends."));
      }
      return (runtime.asSystem(() ->
      {
         QRecord alert = AlertsDb.byId(AlertsAlert.TABLE_NAME, alertId);
         if(alert == null || Status.RESOLVED.name().equals(alert.getValueString("status")))
         {
            return (false);
         }
         Instant now = runtime.now();
         if(!until.isAfter(now))
         {
            throw (new QUserFacingException("The snooze must end in the future."));
         }
         AlertsDb.update(AlertsAlert.TABLE_NAME, new QRecord().withValue("id", alertId).withValue("status", Status.SNOOZED.name()).withValue("snoozedUntil", until));
         event(alertId, EventType.SNOOZED, userId, alert.getValueString("status"), Status.SNOOZED.name(), "until " + until, now);
         return (true);
      }));
   }



   /*******************************************************************************
    ** Assign an alert to a user and/or a team.
    *******************************************************************************/
   public boolean assign(Integer alertId, String assigneeUserId, Integer teamId, String actorUserId) throws QException
   {
      return (runtime.asSystem(() ->
      {
         QRecord alert = AlertsDb.byId(AlertsAlert.TABLE_NAME, alertId);
         if(alert == null)
         {
            return (false);
         }
         AlertsDb.update(AlertsAlert.TABLE_NAME, new QRecord().withValue("id", alertId).withValue("assignedUserId", assigneeUserId).withValue("assignedTeamId", teamId));
         String to = assigneeUserId != null ? assigneeUserId : (teamId != null ? "team " + teamId : "nobody");
         event(alertId, EventType.ASSIGNED, actorUserId, alert.getValueString("assignedUserId"), to, null, runtime.now());
         return (true);
      }));
   }



   /*******************************************************************************
    ** Add a comment to an alert's timeline.
    *******************************************************************************/
   public void comment(Integer alertId, String userId, String note) throws QException
   {
      if(!StringUtils.hasContent(note))
      {
         throw (new QUserFacingException("Write a comment."));
      }
      runtime.asSystem(() ->
      {
         event(alertId, EventType.COMMENTED, userId, null, null, note, runtime.now());
         return (null);
      });
   }



   /*******************************************************************************
    ** Reopen a resolved alert by hand (a new alert is created instead if its
    ** key is open again already).
    *******************************************************************************/
   public boolean reopenById(Integer alertId, String userId, String note) throws QException
   {
      return (runtime.asSystem(() ->
      {
         QRecord alert = AlertsDb.byId(AlertsAlert.TABLE_NAME, alertId);
         if(alert == null || !Status.RESOLVED.name().equals(alert.getValueString("status")))
         {
            return (false);
         }
         String openKey = openKey(alert.getValueString("alertTypeName"), alert.getValueString("dedupKey"));
         if(AlertsDb.first(AlertsAlert.TABLE_NAME, AlertsDb.eq("openDedupKey", openKey)) != null)
         {
            return (false);
         }
         QRecord type = AlertsDb.byId(AlertsType.TABLE_NAME, alert.getValue("alertTypeId"));
         reopen(alert, type, null, AlertSeverity.valueOf(alert.getValueString("severity")), alert.getValueInteger("monitorId"), runtime.now(), userId, note);
         return (true);
      }));
   }



   /*******************************************************************************
    ** Periodic upkeep (run by the evaluator's tick): end expired snoozes,
    ** resolve quiet-period alerts, and end flapping that has calmed down.
    ** Returns how many alerts changed.
    *******************************************************************************/
   public int maintenance() throws QException
   {
      return (runtime.asSystem(() ->
      {
         Instant now     = runtime.now();
         int     changed = 0;

         for(QRecord alert : AlertsDb.query(AlertsAlert.TABLE_NAME, AlertsDb.eq("status", Status.SNOOZED.name())
            .withCriteria("snoozedUntil", QCriteriaOperator.LESS_THAN_OR_EQUALS, now)))
         {
            unsnooze(alert, now, "The snooze ended");
            changed++;
         }

         for(QRecord type : AlertsDb.query(AlertsType.TABLE_NAME, AlertsDb.eq("resolveMode", "QUIET_PERIOD").withCriteria("quietPeriodMinutes", QCriteriaOperator.IS_NOT_BLANK)))
         {
            Instant cutoff = now.minus(type.getValueInteger("quietPeriodMinutes"), ChronoUnit.MINUTES);
            for(QRecord alert : AlertsDb.query(AlertsAlert.TABLE_NAME, AlertsDb.eq("alertTypeId", type.getValue("id"))
               .withCriteria("status", QCriteriaOperator.IN, Status.openNames())
               .withCriteria("lastRaisedAt", QCriteriaOperator.LESS_THAN, cutoff)))
            {
               resolveAlert(alert, Resolution.QUIET_PERIOD, null, "No new occurrences for " + type.getValueInteger("quietPeriodMinutes") + " minutes");
               changed++;
            }
         }

         Instant calm = now.minus(runtime.getConfig().getFlapWindowMinutes(), ChronoUnit.MINUTES);
         for(QRecord alert : AlertsDb.query(AlertsAlert.TABLE_NAME, AlertsDb.eq("flapping", true)))
         {
            int recent = AlertsDb.count(AlertsEvent.TABLE_NAME, AlertsDb.eq("alertId", alert.getValue("id"))
               .withCriteria("eventType", QCriteriaOperator.IN, List.of(EventType.REOPENED.name(), EventType.RESOLVED.name()))
               .withCriteria("at", QCriteriaOperator.GREATER_THAN, calm));
            if(recent == 0)
            {
               AlertsDb.update(AlertsAlert.TABLE_NAME, new QRecord().withValue("id", alert.getValue("id")).withValue("flapping", false));
               event(alert.getValueInteger("id"), EventType.FLAPPING_ENDED, null, null, null, "Stable for " + runtime.getConfig().getFlapWindowMinutes() + " minutes", now);
               changed++;
            }
         }
         return (changed);
      }));
   }



   /*******************************************************************************
    ** Write a timeline entry; returns its id.
    *******************************************************************************/
   public Integer event(Integer alertId, EventType eventType, String userId, String from, String to, String note, Instant at) throws QException
   {
      QRecord inserted = AlertsDb.insert(AlertsEvent.TABLE_NAME, new QRecord()
         .withValue("alertId", alertId)
         .withValue("eventType", eventType.name())
         .withValue("at", at)
         .withValue("userId", userId)
         .withValue("fromValue", from)
         .withValue("toValue", to)
         .withValue("note", note));
      return (inserted.getValueInteger("id"));
   }



   /*******************************************************************************
    ** The severity to use: the admin's override, else the signal's, else the
    ** type's default.
    *******************************************************************************/
   static AlertSeverity effectiveSeverity(QRecord type, AlertSeverity signalSeverity)
   {
      AlertSeverity override = AlertSeverity.fromLabel(type.getValueString("severityOverride"), null);
      if(override != null)
      {
         return (override);
      }
      if(signalSeverity != null)
      {
         return (signalSeverity);
      }
      return (AlertSeverity.fromLabel(type.getValueString("defaultSeverity"), AlertSeverity.WARNING));
   }



   /*******************************************************************************
    ** A new alert.
    *******************************************************************************/
   private QRecord create(QRecord type, AlertSignal signal, String dedupKey, String openKey, AlertSeverity severity, Integer monitorId, Instant now) throws QException
   {
      QRecord record = new QRecord()
         .withValue("alertTypeId", type.getValue("id"))
         .withValue("alertTypeName", signal.getAlertTypeName())
         .withValue("monitorId", monitorId)
         .withValue("groupId", type.getValue("groupId"))
         .withValue("dedupKey", dedupKey)
         .withValue("openDedupKey", openKey)
         .withValue("status", Status.OPEN.name())
         .withValue("severity", severity.name())
         .withValue("firstRaisedAt", now)
         .withValue("lastRaisedAt", now)
         .withValue("occurrenceCount", 1)
         .withValue("reopenCount", 0)
         .withValue("flapping", false)
         .withValue("muted", false)
         .withValue("escalationLevel", 0)
         .withValue("subjectTableName", type.getValueString("subjectTableName"))
         .withValue("visibilityPermission", type.getValueString("visibilityPermission"))
         .withValue("sourceName", type.getValueString("sourceName"));
      applySignalText(record, type, signal);

      QRecord alert;
      try
      {
         alert = AlertsDb.insert(AlertsAlert.TABLE_NAME, record);
      }
      catch(QException e)
      {
         ////////////////////////////////////////////////////////////////////
         // another replica (or thread) opened the same key first: update  //
         // the winner instead (the unique openDedupKey makes this safe)   //
         ////////////////////////////////////////////////////////////////////
         QRecord winner = AlertsDb.first(AlertsAlert.TABLE_NAME, AlertsDb.eq("openDedupKey", openKey));
         if(winner == null)
         {
            throw (e);
         }
         return (refresh(winner, type, signal, severity, monitorId, now));
      }

      Integer alertId = alert.getValueInteger("id");
      syncRecordRefs(alertId, signal, monitorId != null);
      Integer eventId = event(alertId, EventType.RAISED, null, null, severity.name(), signal.getTitle(), now);
      QRecord fresh   = AlertsDb.byId(AlertsAlert.TABLE_NAME, alertId);
      runtime.getRouter().route(fresh, EventType.RAISED, eventId);
      return (fresh);
   }



   /*******************************************************************************
    ** A raise on an alert that is already open.
    *******************************************************************************/
   private QRecord refresh(QRecord open, QRecord type, AlertSignal signal, AlertSeverity severity, Integer monitorId, Instant now) throws QException
   {
      Integer       alertId  = open.getValueInteger("id");
      AlertSeverity previous = AlertSeverity.fromLabel(open.getValueString("severity"), AlertSeverity.WARNING);

      QRecord update = new QRecord()
         .withValue("id", alertId)
         .withValue("lastRaisedAt", now)
         .withValue("occurrenceCount", (open.getValueInteger("occurrenceCount") == null ? 1 : open.getValueInteger("occurrenceCount")) + 1);
      if(monitorId != null && open.getValue("monitorId") == null)
      {
         update.setValue("monitorId", monitorId);
      }
      applySignalText(update, type, signal);

      boolean raisedSeverity = severity.compareTo(previous) > 0;
      if(severity != previous)
      {
         update.setValue("severity", severity.name());
      }
      if(raisedSeverity && Status.SNOOZED.name().equals(open.getValueString("status")))
      {
         update.setValue("status", Status.OPEN.name());
         update.setValue("snoozedUntil", null);
      }
      AlertsDb.update(AlertsAlert.TABLE_NAME, update);
      syncRecordRefs(alertId, signal, monitorId != null);

      if(severity != previous)
      {
         Integer eventId = event(alertId, EventType.SEVERITY_CHANGED, null, previous.name(), severity.name(), null, now);
         if(raisedSeverity)
         {
            if(Status.SNOOZED.name().equals(open.getValueString("status")))
            {
               event(alertId, EventType.UNSNOOZED, null, Status.SNOOZED.name(), Status.OPEN.name(), "The severity rose", now);
            }
            runtime.getRouter().route(AlertsDb.byId(AlertsAlert.TABLE_NAME, alertId), EventType.SEVERITY_CHANGED, eventId);
         }
      }
      else if(shouldRecordRepeat(alertId, now))
      {
         event(alertId, EventType.REPEATED, null, null, String.valueOf(update.getValueInteger("occurrenceCount")), null, now);
      }
      return (AlertsDb.byId(AlertsAlert.TABLE_NAME, alertId));
   }



   /*******************************************************************************
    ** Reopen a resolved alert (raised again within the window, or by hand).
    *******************************************************************************/
   private QRecord reopen(QRecord resolved, QRecord type, AlertSignal signal, AlertSeverity severity, Integer monitorId, Instant now, String userId, String note) throws QException
   {
      Integer alertId = resolved.getValueInteger("id");
      QRecord update = new QRecord()
         .withValue("id", alertId)
         .withValue("status", Status.OPEN.name())
         .withValue("openDedupKey", openKey(resolved.getValueString("alertTypeName"), resolved.getValueString("dedupKey")))
         .withValue("severity", severity.name())
         .withValue("lastRaisedAt", now)
         .withValue("occurrenceCount", (resolved.getValueInteger("occurrenceCount") == null ? 0 : resolved.getValueInteger("occurrenceCount")) + 1)
         .withValue("reopenCount", (resolved.getValueInteger("reopenCount") == null ? 0 : resolved.getValueInteger("reopenCount")) + 1)
         .withValue("resolvedAt", null)
         .withValue("resolvedByUserId", null)
         .withValue("resolution", null)
         .withValue("resolutionNote", null)
         .withValue("acknowledgedAt", null)
         .withValue("acknowledgedByUserId", null)
         .withValue("snoozedUntil", null);
      if(monitorId != null)
      {
         update.setValue("monitorId", monitorId);
      }
      if(signal != null)
      {
         applySignalText(update, type, signal);
      }
      AlertsDb.update(AlertsAlert.TABLE_NAME, update);
      if(signal != null)
      {
         syncRecordRefs(alertId, signal, monitorId != null);
      }

      Integer eventId = event(alertId, EventType.REOPENED, userId, Status.RESOLVED.name(), Status.OPEN.name(), note, now);
      boolean flapStarted = checkFlapping(alertId, now);
      if(!flapStarted)
      {
         runtime.getRouter().route(AlertsDb.byId(AlertsAlert.TABLE_NAME, alertId), EventType.REOPENED, eventId);
      }
      return (AlertsDb.byId(AlertsAlert.TABLE_NAME, alertId));
   }



   /*******************************************************************************
    ** Close an open alert.
    *******************************************************************************/
   private QRecord resolveAlert(QRecord alert, Resolution resolution, String userId, String note) throws QException
   {
      Integer alertId = alert.getValueInteger("id");
      Instant now     = runtime.now();
      AlertsDb.update(AlertsAlert.TABLE_NAME, new QRecord()
         .withValue("id", alertId)
         .withValue("status", Status.RESOLVED.name())
         .withValue("openDedupKey", null)
         .withValue("resolvedAt", now)
         .withValue("resolvedByUserId", userId)
         .withValue("resolution", resolution == null ? Resolution.MANUAL.name() : resolution.name())
         .withValue("resolutionNote", note)
         .withValue("snoozedUntil", null));

      for(QRecord ref : AlertsDb.query(AlertsRecordRef.TABLE_NAME, AlertsDb.eq("alertId", alertId).withCriteria("active", QCriteriaOperator.EQUALS, true)))
      {
         AlertsDb.update(AlertsRecordRef.TABLE_NAME, new QRecord().withValue("id", ref.getValue("id")).withValue("active", false));
      }

      Integer eventId = event(alertId, EventType.RESOLVED, userId, alert.getValueString("status"), Status.RESOLVED.name(), note, now);
      boolean flapStarted = checkFlapping(alertId, now);
      if(!flapStarted)
      {
         runtime.getRouter().route(AlertsDb.byId(AlertsAlert.TABLE_NAME, alertId), EventType.RESOLVED, eventId);
      }
      updateTypeCounts(alert.getValueInteger("alertTypeId"), null);
      return (AlertsDb.byId(AlertsAlert.TABLE_NAME, alertId));
   }



   /*******************************************************************************
    ** End a snooze.
    *******************************************************************************/
   private void unsnooze(QRecord alert, Instant now, String note) throws QException
   {
      String back = alert.getValue("acknowledgedAt") != null ? Status.ACKNOWLEDGED.name() : Status.OPEN.name();
      AlertsDb.update(AlertsAlert.TABLE_NAME, new QRecord().withValue("id", alert.getValue("id")).withValue("status", back).withValue("snoozedUntil", null));
      event(alert.getValueInteger("id"), EventType.UNSNOOZED, null, Status.SNOOZED.name(), back, note, now);
   }



   /*******************************************************************************
    ** Start flapping when the alert has changed between open and resolved
    ** flapThreshold times within the window. Returns true when it just
    ** started (the router then sends one flapping notice instead).
    *******************************************************************************/
   private boolean checkFlapping(Integer alertId, Instant now) throws QException
   {
      AlertsQBitConfig config = runtime.getConfig();
      QRecord          alert  = AlertsDb.byId(AlertsAlert.TABLE_NAME, alertId);
      if(Boolean.TRUE.equals(alert.getValueBoolean("flapping")))
      {
         return (false);
      }
      int changes = AlertsDb.count(AlertsEvent.TABLE_NAME, AlertsDb.eq("alertId", alertId)
         .withCriteria("eventType", QCriteriaOperator.IN, List.of(EventType.REOPENED.name(), EventType.RESOLVED.name()))
         .withCriteria("at", QCriteriaOperator.GREATER_THAN, now.minus(config.getFlapWindowMinutes(), ChronoUnit.MINUTES)));
      if(changes < config.getFlapThreshold())
      {
         return (false);
      }
      AlertsDb.update(AlertsAlert.TABLE_NAME, new QRecord().withValue("id", alertId).withValue("flapping", true));
      Integer eventId = event(alertId, EventType.FLAPPING_STARTED, null, null, null, changes + " open/resolve changes in " + config.getFlapWindowMinutes() + " minutes", now);
      runtime.getRouter().route(AlertsDb.byId(AlertsAlert.TABLE_NAME, alertId), EventType.FLAPPING_STARTED, eventId);
      return (true);
   }



   /*******************************************************************************
    ** The most recent alert for (type, key) resolved within the reopen window.
    *******************************************************************************/
   private QRecord recentlyResolved(String alertTypeName, String dedupKey, Instant now) throws QException
   {
      Instant cutoff = now.minus(runtime.getConfig().getReopenWindowMinutes(), ChronoUnit.MINUTES);
      return (AlertsDb.first(AlertsAlert.TABLE_NAME, AlertsDb.eq("alertTypeName", alertTypeName)
         .withCriteria("dedupKey", QCriteriaOperator.EQUALS, dedupKey)
         .withCriteria("status", QCriteriaOperator.EQUALS, Status.RESOLVED.name())
         .withCriteria("resolvedAt", QCriteriaOperator.GREATER_THAN_OR_EQUALS, cutoff)
         .withOrderBy(new QFilterOrderBy("resolvedAt", false))));
   }



   /*******************************************************************************
    ** Record a REPEATED entry at most once an hour per alert.
    *******************************************************************************/
   private boolean shouldRecordRepeat(Integer alertId, Instant now) throws QException
   {
      QRecord last = AlertsDb.first(AlertsEvent.TABLE_NAME, AlertsDb.eq("alertId", alertId)
         .withCriteria("eventType", QCriteriaOperator.IN, List.of(EventType.REPEATED.name(), EventType.RAISED.name(), EventType.REOPENED.name()))
         .withOrderBy(new QFilterOrderBy("at", false)));
      return (last == null || last.getValueInstant("at") == null || last.getValueInstant("at").isBefore(now.minus(REPEATED_EVENT_EVERY_MINUTES, ChronoUnit.MINUTES)));
   }



   /*******************************************************************************
    ** Copy the signal's text, counts, links and attributes onto an alert row.
    *******************************************************************************/
   private void applySignalText(QRecord record, QRecord type, AlertSignal signal)
   {
      String title = StringUtils.hasContent(signal.getTitle()) ? signal.getTitle() : type.getValueString("label");
      record.setValue("title", title);
      record.setValue("summary", signal.getSummary());
      record.setValue("details", signal.getDetails());
      record.setValue("itemCount", signal.getEffectiveCount());
      record.setValue("links", signal.getLinks().isEmpty() ? null : JsonUtils.toJson(linksAsMaps(signal.getLinks())));
      record.setValue("attributes", signal.getAttributes().isEmpty() ? null : JsonUtils.toJson(signal.getAttributes()));

      String subjectTable = type.getValueString("subjectTableName");
      if(subjectTable != null)
      {
         List<AlertRecordRef> subjectRefs = signal.getRecordRefs().stream().filter(ref -> subjectTable.equals(ref.getTableName())).toList();
         record.setValue("subjectRecordId", subjectRefs.size() == 1 ? String.valueOf(subjectRefs.get(0).getPrimaryKey()) : null);
      }
   }



   /*******************************************************************************
    ** Links as plain maps (the filter as JSON), for storage.
    *******************************************************************************/
   static List<Map<String, Object>> linksAsMaps(List<AlertLink> links)
   {
      List<Map<String, Object>> maps = new ArrayList<>();
      for(AlertLink link : links)
      {
         Map<String, Object> map = new LinkedHashMap<>();
         map.put("label", link.getLabel());
         map.put("tableName", link.getTableName());
         map.put("recordId", link.getRecordId() == null ? null : String.valueOf(link.getRecordId()));
         map.put("filter", link.getFilter() == null ? null : JsonUtils.toJson(link.getFilter()));
         map.put("path", link.getPath());
         map.put("url", link.getUrl());
         map.values().removeIf(Objects::isNull);
         maps.add(map);
      }
      return (maps);
   }



   /*******************************************************************************
    ** Add the signal's record refs to the alert (marking them active). For a
    ** monitor (a full evaluation), refs it no longer returns become inactive;
    ** a producer's refs are merged. recordRefCount is the active count.
    *******************************************************************************/
   private void syncRecordRefs(Integer alertId, AlertSignal signal, boolean fullState) throws QException
   {
      List<QRecord>        existing = AlertsDb.query(AlertsRecordRef.TABLE_NAME, AlertsDb.eq("alertId", alertId));
      Map<String, QRecord> byKey    = new HashMap<>();
      for(QRecord ref : existing)
      {
         byKey.put(ref.getValueString("tableName") + ":" + ref.getValueString("recordId"), ref);
      }

      Set<String> current = new LinkedHashSet<>();
      for(AlertRecordRef ref : signal.getRecordRefs())
      {
         String  recordId = String.valueOf(ref.getPrimaryKey());
         String  key      = ref.getTableName() + ":" + recordId;
         QRecord row      = byKey.get(key);
         current.add(key);
         if(row == null)
         {
            QRecord inserted = AlertsDb.insert(AlertsRecordRef.TABLE_NAME, new QRecord().withValue("alertId", alertId).withValue("tableName", ref.getTableName())
               .withValue("recordId", recordId).withValue("label", ref.getLabel()).withValue("active", true));
            byKey.put(key, inserted);
         }
         else if(!Boolean.TRUE.equals(row.getValueBoolean("active")) || (ref.getLabel() != null && !ref.getLabel().equals(row.getValueString("label"))))
         {
            AlertsDb.update(AlertsRecordRef.TABLE_NAME, new QRecord().withValue("id", row.getValue("id")).withValue("active", true).withValue("label", ref.getLabel() == null ? row.getValueString("label") : ref.getLabel()));
            row.setValue("active", true);
         }
      }

      if(fullState)
      {
         for(Map.Entry<String, QRecord> entry : byKey.entrySet())
         {
            if(!current.contains(entry.getKey()) && Boolean.TRUE.equals(entry.getValue().getValueBoolean("active")))
            {
               AlertsDb.update(AlertsRecordRef.TABLE_NAME, new QRecord().withValue("id", entry.getValue().getValue("id")).withValue("active", false));
               entry.getValue().setValue("active", false);
            }
         }
      }

      int active = AlertsDb.count(AlertsRecordRef.TABLE_NAME, AlertsDb.eq("alertId", alertId).withCriteria("active", QCriteriaOperator.EQUALS, true));
      AlertsDb.update(AlertsAlert.TABLE_NAME, new QRecord().withValue("id", alertId).withValue("recordRefCount", active));
   }



   /*******************************************************************************
    ** Refresh a type's denormalized open count (and last-raised time).
    *******************************************************************************/
   private void updateTypeCounts(Integer typeId, Instant lastRaisedAt) throws QException
   {
      if(typeId == null)
      {
         return;
      }
      int     open   = AlertsDb.count(AlertsAlert.TABLE_NAME, AlertsDb.eq("alertTypeId", typeId).withCriteria(new QFilterCriteria("status", QCriteriaOperator.IN, Status.openNames())));
      QRecord update = new QRecord().withValue("id", typeId).withValue("openCount", open);
      if(lastRaisedAt != null)
      {
         update.setValue("lastRaisedAt", lastRaisedAt);
      }
      AlertsDb.update(AlertsType.TABLE_NAME, update);
   }



   /*******************************************************************************
    ** What a subscription or mute scope is matched against.
    *******************************************************************************/
   public record AlertTarget(Integer typeId, String typeName, Integer groupId, Integer monitorId, String subjectTableName, Set<String> recordKeys, Map<String, String> attributes)
   {
      /***************************************************************************
       ** From a type row and a signal (before the alert exists).
       ***************************************************************************/
      static AlertTarget of(QRecord type, AlertSignal signal, Integer monitorId)
      {
         Set<String> keys = new LinkedHashSet<>();
         for(AlertRecordRef ref : signal.getRecordRefs())
         {
            keys.add(ref.getTableName() + ":" + ref.getPrimaryKey());
         }
         return (new AlertTarget(type.getValueInteger("id"), signal.getAlertTypeName(), type.getValueInteger("groupId"), monitorId,
            type.getValueString("subjectTableName"), keys, signal.getAttributes()));
      }



      /***************************************************************************
       ** From an alert row (its active record refs read from the table).
       ***************************************************************************/
      public static AlertTarget of(QRecord alert) throws QException
      {
         Set<String> keys = new LinkedHashSet<>();
         for(QRecord ref : AlertsDb.query(AlertsRecordRef.TABLE_NAME, AlertsDb.eq("alertId", alert.getValue("id")).withCriteria("active", QCriteriaOperator.EQUALS, true)))
         {
            keys.add(ref.getValueString("tableName") + ":" + ref.getValueString("recordId"));
         }
         Map<String, String> attributes = new HashMap<>();
         String              json       = alert.getValueString("attributes");
         if(StringUtils.hasContent(json))
         {
            try
            {
               Map<String, Serializable> parsed = JsonUtils.toObject(json, Map.class);
               parsed.forEach((key, value) -> attributes.put(key, value == null ? null : String.valueOf(value)));
            }
            catch(java.io.IOException e)
            {
               LOG.debug("Unreadable alert attributes", e);
            }
         }
         return (new AlertTarget(alert.getValueInteger("alertTypeId"), alert.getValueString("alertTypeName"), alert.getValueInteger("groupId"), alert.getValueInteger("monitorId"),
            alert.getValueString("subjectTableName"), keys, attributes));
      }
   }
}
