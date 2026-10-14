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


import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import com.kingsrook.qbits.alerts.AlertsRuntime;
import com.kingsrook.qbits.alerts.model.AlertsAlert;
import com.kingsrook.qbits.alerts.model.AlertsEscalationPolicy;
import com.kingsrook.qbits.alerts.model.AlertsEscalationStep;
import com.kingsrook.qbits.alerts.model.AlertsEvent;
import com.kingsrook.qbits.alerts.model.AlertsGroup;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.EventType;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.MuteEffect;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.Status;
import com.kingsrook.qbits.alerts.model.AlertsType;
import com.kingsrook.qqq.backend.core.actions.QBackendTransaction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** Timed notifications for open, unacknowledged alerts. Progress and fan-out
 ** commit together per alert. Run on one scheduler node, like the other jobs.
 *******************************************************************************/
public class EscalationAndReminder
{
   private static final int PAGE_SIZE = 100;

   private final AlertsRuntime runtime;



   /*******************************************************************************
    ** Use the installed runtime, including its controllable clock.
    *******************************************************************************/
   public EscalationAndReminder(AlertsRuntime runtime)
   {
      this.runtime = runtime;
   }



   /*******************************************************************************
    ** Read bounded pages. A persisted checkpoint makes repeated ticks harmless,
    ** including attempts with no eligible recipients. Serialize this runtime's ticks.
    *******************************************************************************/
   public int runDue() throws QException
   {
      synchronized(runtime)
      {
         return (runtime.asSystem(() ->
         {
            int changed = 0;
            int lastId = 0;
            while(true)
            {
               List<QRecord> alerts = AlertsDb.query(AlertsAlert.TABLE_NAME, AlertsDb.eq("status", Status.OPEN.name())
                  .withCriteria("id", QCriteriaOperator.GREATER_THAN, lastId)
                  .withOrderBy(new QFilterOrderBy("id", true)).withLimit(PAGE_SIZE));
               if(alerts.isEmpty())
               {
                  break;
               }
               for(QRecord alert : alerts)
               {
                  lastId = alert.getValueInteger("id");
                  changed += process(lastId);
               }
            }
            return (changed);
         }));
      }
   }



   /*******************************************************************************
    ** One alert is an atomic unit, including in-app recipients and delivery rows.
    *******************************************************************************/
   private int process(Integer alertId) throws QException
   {
      QBackendTransaction previous = QContext.getQBackendTransaction();
      try(QBackendTransaction transaction = QBackendTransaction.openFor(new QueryInput(AlertsAlert.TABLE_NAME)))
      {
         QContext.setTransaction(transaction);
         try
         {
            int changed = apply(AlertsDb.byId(AlertsAlert.TABLE_NAME, alertId), runtime.now());
            transaction.commit();
            return (changed);
         }
         catch(Exception e)
         {
            transaction.rollback();
            throw (new QException("Could not apply timed notifications for alert " + alertId, e));
         }
      }
      finally
      {
         QContext.setTransaction(previous);
      }
   }



   /*******************************************************************************
    ** Acknowledge, snooze, resolve, either mute effect, and disabled types stop work.
    *******************************************************************************/
   private int apply(QRecord alert, Instant now) throws QException
   {
      if(alert == null || !Status.OPEN.name().equals(alert.getValueString("status")) || alert.getValue("acknowledgedAt") != null)
      {
         return (0);
      }
      Instant checked = alert.getValueInstant("timedNotificationCheckedAt");
      if(checked != null && !checked.truncatedTo(ChronoUnit.MINUTES).isBefore(now.truncatedTo(ChronoUnit.MINUTES)))
      {
         return (0);
      }
      QRecord type = AlertsDb.byId(AlertsType.TABLE_NAME, alert.getValue("alertTypeId"));
      if(type == null || Boolean.FALSE.equals(type.getValueBoolean("enabled")) || Boolean.FALSE.equals(type.getValueBoolean("notify")))
      {
         return (0);
      }
      AlertsEngine.AlertTarget target = AlertsEngine.AlertTarget.of(alert);
      if(Boolean.TRUE.equals(alert.getValueBoolean("muted")) || Boolean.TRUE.equals(alert.getValueBoolean("flapping"))
         || runtime.getRouter().isMuted(target, now, MuteEffect.SILENCE_NOTIFICATIONS)
         || runtime.getRouter().isSuppressedByMaintenance(target, now))
      {
         return (0);
      }

      int changed = escalate(alert, type, now) ? 1 : 0;
      QRecord fresh = AlertsDb.byId(AlertsAlert.TABLE_NAME, alert.getValue("id"));
      Integer interval = type.getValueInteger("reminderIntervalMinutes");
      if(interval == null && "CRITICAL".equals(fresh.getValueString("severity")))
      {
         interval = 60;
      }
      Instant raised = latest(fresh.getValueInstant("lastRaisedAt"), fresh.getValueInstant("firstRaisedAt"));
      Instant since = latest(fresh.getValueInstant("lastNotifiedAt"), raised);
      if(interval != null && interval > 0 && since != null && !since.plus(interval, ChronoUnit.MINUTES).isAfter(now))
      {
         if(runtime.getRouter().remind(fresh) > 0)
         {
            changed = 1;
         }
      }
      AlertsDb.update(AlertsAlert.TABLE_NAME, new QRecord().withValue("id", alert.getValue("id")).withValue("timedNotificationCheckedAt", now));
      return (changed);
   }



   /*******************************************************************************
    ** Type policy wins; otherwise use the alert's group policy. Steps are ordered
    ** by stepOrder and measured from the most recent reopen (else first raise).
    ** At most one step is taken per minute; missed steps are not burst-delivered.
    *******************************************************************************/
   private boolean escalate(QRecord alert, QRecord type, Instant now) throws QException
   {
      Integer policyId = type.getValueInteger("escalationPolicyId");
      if(policyId == null)
      {
         QRecord group = AlertsDb.byId(AlertsGroup.TABLE_NAME, alert.getValue("groupId"));
         policyId = group == null ? null : group.getValueInteger("escalationPolicyId");
      }
      QRecord policy = AlertsDb.byId(AlertsEscalationPolicy.TABLE_NAME, policyId);
      if(policy == null || !Boolean.TRUE.equals(policy.getValueBoolean("enabled")))
      {
         return (false);
      }
      QRecord reopened = AlertsDb.first(AlertsEvent.TABLE_NAME, AlertsDb.eq("alertId", alert.getValue("id"))
         .withCriteria("eventType", QCriteriaOperator.EQUALS, EventType.REOPENED.name()).withOrderBy(new QFilterOrderBy("id", false)));
      Instant start = reopened == null ? alert.getValueInstant("firstRaisedAt") : reopened.getValueInstant("at");
      int level = alert.getValueInteger("escalationLevel") == null ? 0 : alert.getValueInteger("escalationLevel");
      QRecord step = AlertsDb.first(AlertsEscalationStep.TABLE_NAME, AlertsDb.eq("policyId", policyId)
         .withCriteria("stepOrder", QCriteriaOperator.GREATER_THAN, level).withOrderBy(new QFilterOrderBy("stepOrder", true)));
      if(step == null)
      {
         step = repeatStep(alert, policy, reopened, level, now);
      }
      if(step == null || start == null || step.getValueInteger("afterMinutes") == null || step.getValueInteger("afterMinutes") < 0
         || !Status.OPEN.name().equals(step.getValueString("onlyIfStatus"))
         || start.plus(step.getValueInteger("afterMinutes"), ChronoUnit.MINUTES).isAfter(now))
      {
         return (false);
      }
      int nextLevel = step.getValueInteger("stepOrder");
      Integer eventId = runtime.getEngine().event(alert.getValueInteger("id"), EventType.ESCALATED, null,
         String.valueOf(level), String.valueOf(nextLevel), level == nextLevel ? "Repeated final escalation step" : "Escalation step " + nextLevel, now);
      runtime.getRouter().escalate(alert, step, eventId);
      AlertsDb.update(AlertsAlert.TABLE_NAME, new QRecord().withValue("id", alert.getValue("id")).withValue("escalationLevel", nextLevel));
      return (true);
   }



   /*******************************************************************************
    ** Repeat only the final step, capped by the number of same-level events in
    ** this open cycle. Repeat spacing uses escalation time, never reminder time.
    *******************************************************************************/
   private QRecord repeatStep(QRecord alert, QRecord policy, QRecord reopened, int level, Instant now) throws QException
   {
      Integer interval = policy.getValueInteger("repeatAfterMinutes");
      Integer maxRepeats = policy.getValueInteger("maxRepeats");
      if(level == 0 || interval == null || interval <= 0 || maxRepeats == null || maxRepeats <= 0)
      {
         return (null);
      }
      QQueryFilter cycle = AlertsDb.eq("alertId", alert.getValue("id"))
         .withCriteria("eventType", QCriteriaOperator.EQUALS, EventType.ESCALATED.name())
         .withCriteria("id", QCriteriaOperator.GREATER_THAN, reopened == null ? 0 : reopened.getValueInteger("id"));
      QRecord last = AlertsDb.first(AlertsEvent.TABLE_NAME, cycle.withOrderBy(new QFilterOrderBy("id", false)));
      if(last == null || last.getValueInstant("at").plus(interval, ChronoUnit.MINUTES).isAfter(now))
      {
         return (null);
      }
      int repeats = AlertsDb.count(AlertsEvent.TABLE_NAME, AlertsDb.eq("alertId", alert.getValue("id"))
         .withCriteria("eventType", QCriteriaOperator.EQUALS, EventType.ESCALATED.name())
         .withCriteria("id", QCriteriaOperator.GREATER_THAN, reopened == null ? 0 : reopened.getValueInteger("id"))
         .withCriteria("fromValue", QCriteriaOperator.EQUALS, String.valueOf(level))
         .withCriteria("toValue", QCriteriaOperator.EQUALS, String.valueOf(level)));
      return (repeats >= maxRepeats ? null : AlertsDb.first(AlertsEscalationStep.TABLE_NAME, AlertsDb.eq("policyId", policy.getValue("id"))
         .withCriteria("stepOrder", QCriteriaOperator.EQUALS, level)));
   }



   /*******************************************************************************
    ** Later of two optional timestamps.
    *******************************************************************************/
   private static Instant latest(Instant left, Instant right)
   {
      return (left == null ? right : right == null || left.isAfter(right) ? left : right);
   }
}
