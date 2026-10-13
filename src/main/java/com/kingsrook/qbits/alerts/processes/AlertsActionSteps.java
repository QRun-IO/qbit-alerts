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

package com.kingsrook.qbits.alerts.processes;


import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.kingsrook.qbits.alerts.AlertsRuntime;
import com.kingsrook.qbits.alerts.AlertsSecurity;
import com.kingsrook.qbits.alerts.engine.AlertsDb;
import com.kingsrook.qbits.alerts.engine.MonitorEvaluator;
import com.kingsrook.qbits.alerts.model.AlertsMonitor;
import com.kingsrook.qbits.alerts.model.AlertsMute;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.DeliveryStatus;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.MonitorStatus;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.MuteEffect;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.Resolution;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.ScopeType;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.SubscriberType;
import com.kingsrook.qbits.alerts.model.AlertsSubscription;
import com.kingsrook.qbits.alerts.notifications.AlertsAudienceResolver;
import com.kingsrook.qbits.alerts.notifications.NotificationService;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QUserFacingException;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLine;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLineInterface;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunBackendStepOutput;
import com.kingsrook.qqq.backend.core.model.actions.processes.Status;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractLoadStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.AbstractTransformStep;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.ProcessSummaryProviderInterface;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** The transform (Review) and load (Result) steps shared by every Alerts
 ** record action. The records come from the standard extract, read in the
 ** user's session, so an action only ever sees rows the user can read; the
 ** changes are then made by the engine (which writes the timeline). Review
 ** says what will happen and what will be skipped; Result says what did.
 *******************************************************************************/
public final class AlertsActionSteps
{
   static final int AUDIENCE_SAMPLE = 10;


   /*******************************************************************************
    ** Steps only.
    *******************************************************************************/
   private AlertsActionSteps()
   {
   }



   /*******************************************************************************
    ** Review: sort the records into "will" and "skipped (why)".
    *******************************************************************************/
   public static class Transform extends AbstractTransformStep implements ProcessSummaryProviderInterface
   {
      private ProcessSummaryLine will;
      private ProcessSummaryLine skipped;
      private final List<ProcessSummaryLine> extra = new ArrayList<>();



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public void runOnePage(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         AlertsAction action = action(input);
         initLines(action);
         for(QRecord record : input.getRecords())
         {
            String skipReason = skipReason(action, record);
            if(skipReason == null)
            {
               will.incrementCountAndAddPrimaryKey(record.getValue("id"));
               output.addRecord(record);
               review(action, record);
            }
            else
            {
               skipped.incrementCountAndAddPrimaryKey(record.getValue("id"));
               skipped.setMessage(skipReason);
            }
         }
      }



      /*******************************************************************************
       ** The Review lines.
       *******************************************************************************/
      @Override
      public ArrayList<ProcessSummaryLineInterface> getProcessSummary(RunBackendStepOutput output, boolean isForResultScreen)
      {
         ArrayList<ProcessSummaryLineInterface> lines = new ArrayList<>();
         if(will == null)
         {
            return (lines);
         }
         will.addSelfToListIfAnyCount(lines);
         skipped.addSelfToListIfAnyCount(lines);
         lines.addAll(extra);
         return (lines);
      }



      /*******************************************************************************
       ** The skipped line, repeated on the Result screen.
       *******************************************************************************/
      ProcessSummaryLine skippedLine()
      {
         return (skipped);
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      private void initLines(AlertsAction action)
      {
         if(will != null)
         {
            return;
         }
         String noun = noun(action);
         will = line(Status.OK, noun + " will be " + pastVerb(action), plural(noun) + " will be " + pastVerb(action), noun + " was " + pastVerb(action), plural(noun) + " were " + pastVerb(action));
         skipped = line(Status.INFO, noun + " will be skipped", plural(noun) + " will be skipped", noun + " was skipped", plural(noun) + " were skipped");
      }



      /*******************************************************************************
       ** Extra Review detail for one record (Run check now's preview, the
       ** Resolve warning).
       *******************************************************************************/
      private void review(AlertsAction action, QRecord record) throws QException
      {
         if(action == AlertsAction.PREVIEW_AUDIENCE || action == AlertsAction.SEND_NOTIFICATION)
         {
            extra.addAll(audienceLines(runtime().getNotifications().preview(record), action == AlertsAction.PREVIEW_AUDIENCE));
         }
         else if(action == AlertsAction.RUN_CHECK)
         {
            MonitorEvaluator.Plan plan = runtime().getEvaluator().preview(record.getValueInteger("id"));
            extra.addAll(planLines(plan, false));
         }
         else if(action == AlertsAction.RESOLVE && record.getValue("monitorId") != null)
         {
            QRecord monitor = runtime().asSystem(() -> AlertsDb.byId(AlertsMonitor.TABLE_NAME, record.getValue("monitorId")));
            if(monitor != null && MonitorStatus.FIRING.name().equals(monitor.getValueString("lastStatus")) && Boolean.TRUE.equals(monitor.getValueBoolean("enabled")))
            {
               extra.add(new ProcessSummaryLine(Status.WARNING, null, "The monitor \"" + monitor.getValueString("name") + "\" is still firing, so its alert will reopen at the next check."));
            }
         }
      }
   }



   /*******************************************************************************
    ** Result: apply the action to each record.
    *******************************************************************************/
   public static class Load extends AbstractLoadStep implements ProcessSummaryProviderInterface
   {
      private ProcessSummaryLine done;
      private ProcessSummaryLine failed;
      private final List<ProcessSummaryLine> extra = new ArrayList<>();



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public void runOnePage(RunBackendStepInput input, RunBackendStepOutput output) throws QException
      {
         AlertsAction action = action(input);
         if(done == null)
         {
            String noun = noun(action);
            done = line(Status.OK, noun + " will be " + pastVerb(action), plural(noun) + " will be " + pastVerb(action), noun + " was " + pastVerb(action), plural(noun) + " were " + pastVerb(action));
            failed = line(Status.ERROR, noun + " will fail", plural(noun) + " will fail", noun + " could not be changed", plural(noun) + " could not be changed");
         }

         String actor = AlertsSecurity.userId(QContext.getQSession());
         for(QRecord record : input.getRecords())
         {
            try
            {
               if(apply(action, record, input, actor))
               {
                  done.incrementCountAndAddPrimaryKey(record.getValue("id"));
               }
               else
               {
                  failed.incrementCountAndAddPrimaryKey(record.getValue("id"));
               }
            }
            catch(QUserFacingException e)
            {
               failed.incrementCountAndAddPrimaryKey(record.getValue("id"));
               failed.setMessage(e.getMessage());
            }
         }
      }



      /*******************************************************************************
       ** The Result lines.
       *******************************************************************************/
      @Override
      public ArrayList<ProcessSummaryLineInterface> getProcessSummary(RunBackendStepOutput output, boolean isForResultScreen)
      {
         ArrayList<ProcessSummaryLineInterface> lines = new ArrayList<>();
         if(done == null)
         {
            return (lines);
         }
         done.addSelfToListIfAnyCount(lines);
         failed.addSelfToListIfAnyCount(lines);
         if(getTransformStep() instanceof Transform transform && transform.skippedLine() != null)
         {
            transform.skippedLine().addSelfToListIfAnyCount(lines);
         }
         lines.addAll(extra);
         return (lines);
      }



      /*******************************************************************************
       ** Do the action for one record; false when nothing changed.
       *******************************************************************************/
      private boolean apply(AlertsAction action, QRecord record, RunBackendStepInput input, String actor) throws QException
      {
         AlertsRuntime runtime = runtime();
         Integer       id      = record.getValueInteger("id");
         String        note    = input.getValueString(AlertsAction.FIELD_NOTE);
         Instant       now     = runtime.now();
         return switch(action)
         {
            case ACKNOWLEDGE -> runtime.getEngine().acknowledge(id, actor);
            case SNOOZE ->
            {
               Integer hours = input.getValueInteger(AlertsAction.FIELD_SNOOZE_HOURS);
               yield (runtime.getEngine().snooze(id, now.plus(hours == null || hours < 1 ? 4 : hours, ChronoUnit.HOURS), actor));
            }
            case RESOLVE ->
            {
               if(!StringUtils.hasContent(note))
               {
                  throw (new QUserFacingException("A resolution note is required."));
               }
               runtime.getEngine().resolveById(id, Resolution.MANUAL, actor, note);
               yield (true);
            }
            case REOPEN -> runtime.getEngine().reopenById(id, actor, note);
            case ASSIGN -> runtime.getEngine().assign(id, input.getValueString(AlertsAction.FIELD_ASSIGN_USER), input.getValueInteger(AlertsAction.FIELD_ASSIGN_TEAM), actor);
            case COMMENT ->
            {
               runtime.getEngine().comment(id, actor, note);
               yield (true);
            }
            case SUBSCRIBE_TYPE -> subscribe(ScopeType.ALERT_TYPE, record.getValueString("name"), input);
            case SUBSCRIBE_GROUP -> subscribe(ScopeType.GROUP, record.getValueString("name"), input);
            case SUBSCRIBE_MONITOR -> subscribe(ScopeType.MONITOR, String.valueOf(id), input);
            case MUTE_TYPE -> mute(ScopeType.ALERT_TYPE, record.getValueString("name"), "type " + record.getValueString("label"), input, actor);
            case MUTE_GROUP -> mute(ScopeType.GROUP, record.getValueString("name"), "group " + record.getValueString("label"), input, actor);
            case MUTE_MONITOR -> mute(ScopeType.MONITOR, String.valueOf(id), "monitor " + record.getValueString("name"), input, actor);
            case MUTE_ALERT -> mute(ScopeType.ALERT_TYPE, record.getValueString("alertTypeName"), "alert type " + record.getValueString("alertTypeName"), input, actor);
            case RUN_CHECK ->
            {
               MonitorEvaluator.Plan plan = runtime.getEvaluator().runNow(id);
               extra.addAll(planLines(plan, true));
               yield (plan.status() != MonitorStatus.CHECK_FAILED);
            }
            case ENABLE_MONITOR, DISABLE_MONITOR -> runtime.asSystem(() ->
            {
               AlertsDb.update(AlertsMonitor.TABLE_NAME, new QRecord().withValue("id", id).withValue("enabled", action == AlertsAction.ENABLE_MONITOR)
                  .withValue("nextRunAt", action == AlertsAction.ENABLE_MONITOR ? now : null));
               return (true);
            });
            case SEND_TEST ->
            {
               QRecord notification = runtime.getDeliveryWorker().sendTest(id, actor);
               String  status       = notification.getValueString("status");
               if(!DeliveryStatus.SENT.name().equals(status))
               {
                  extra.add(new ProcessSummaryLine(Status.ERROR, null, "The test did not send: " + Objects.toString(notification.getValueString("lastError"), status)));
                  yield (false);
               }
               yield (true);
            }
            case RETRY_DELIVERY -> runtime.getDeliveryWorker().retry(List.of(id)) > 0;
            case CREATE_MONITOR -> createMonitor(record);
            case PREVIEW_AUDIENCE ->
            {
               extra.addAll(audienceLines(runtime.getNotifications().preview(record), true));
               yield (true);
            }
            case SEND_NOTIFICATION ->
            {
               NotificationService.SendResult result = runtime.getNotifications().send(id);
               extra.add(new ProcessSummaryLine(Status.INFO, null, "\"" + record.getValueString("title") + "\" reached " + count(result.added(), "new recipient")
                  + " (" + count(result.total(), "recipient") + " in all)."));
               yield (true);
            }
            case SCHEDULE_NOTIFICATION ->
            {
               runtime.getNotifications().schedule(id);
               yield (true);
            }
            case RETRACT_NOTIFICATION ->
            {
               runtime.getNotifications().retract(id);
               yield (true);
            }
         };
      }
   }



   /*******************************************************************************
    ** Why a record is skipped (null: it isn't).
    *******************************************************************************/
   static String skipReason(AlertsAction action, QRecord record) throws QException
   {
      String status = record.getValueString("status");
      return switch(action)
      {
         case ACKNOWLEDGE -> AlertsPossibleValues.Status.OPEN.name().equals(status) ? null : "Only open, unacknowledged alerts can be acknowledged.";
         case SNOOZE, RESOLVE -> AlertsPossibleValues.Status.RESOLVED.name().equals(status) ? "Resolved alerts are skipped." : null;
         case REOPEN -> AlertsPossibleValues.Status.RESOLVED.name().equals(status) ? null : "Only resolved alerts can be reopened.";
         case ENABLE_MONITOR -> Boolean.TRUE.equals(record.getValueBoolean("enabled")) ? "Already enabled." : null;
         case DISABLE_MONITOR -> Boolean.FALSE.equals(record.getValueBoolean("enabled")) ? "Already disabled." : null;
         case RETRY_DELIVERY -> List.of(DeliveryStatus.FAILED.name(), DeliveryStatus.DEAD.name()).contains(status) ? null : "Only failed or dead notifications can be retried.";
         case PREVIEW_AUDIENCE, SEND_NOTIFICATION -> NotificationService.STATUS_RETRACTED.equals(status) ? "Retracted notifications can't be sent." : null;
         case SCHEDULE_NOTIFICATION -> NotificationService.STATUS_DRAFT.equals(status) ? null : "Only drafts can be scheduled.";
         case RETRACT_NOTIFICATION -> NotificationService.STATUS_SENT.equals(status) || NotificationService.STATUS_SCHEDULED.equals(status) ? null : "Only sent or scheduled notifications can be retracted.";
         case SUBSCRIBE_TYPE -> alreadySubscribed(ScopeType.ALERT_TYPE, record.getValueString("name"));
         case SUBSCRIBE_GROUP -> alreadySubscribed(ScopeType.GROUP, record.getValueString("name"));
         case SUBSCRIBE_MONITOR -> alreadySubscribed(ScopeType.MONITOR, String.valueOf(record.getValue("id")));
         default -> null;
      };
   }



   /*******************************************************************************
    ** "You already subscribed" (null when not).
    *******************************************************************************/
   private static String alreadySubscribed(ScopeType scopeType, String scopeValue) throws QException
   {
      String userId = AlertsSecurity.userId(QContext.getQSession());
      if(userId == null)
      {
         return ("Your session has no user, so it can't subscribe.");
      }
      QRecord existing = runtime().asSystem(() -> AlertsDb.first(AlertsSubscription.TABLE_NAME, AlertsDb.eq("userId", userId)
         .withCriteria("scopeType", QCriteriaOperator.EQUALS, scopeType.name())
         .withCriteria("scopeValue", QCriteriaOperator.EQUALS, scopeValue)));
      return (existing == null ? null : "You are already subscribed (subscription " + existing.getValue("id") + ").");
   }



   /*******************************************************************************
    ** Create the user's own subscription (through the table, so its rules apply).
    *******************************************************************************/
   private static boolean subscribe(ScopeType scopeType, String scopeValue, RunBackendStepInput input) throws QException
   {
      List<QRecord> inserted = InsertAction.executeForRecords(new InsertInput(AlertsSubscription.TABLE_NAME).withRecord(new QRecord()
         .withValue("subscriberType", SubscriberType.USER.name())
         .withValue("userId", AlertsSecurity.userId(QContext.getQSession()))
         .withValue("scopeType", scopeType.name())
         .withValue("scopeValue", scopeValue)
         .withValue("minSeverity", StringUtils.hasContent(input.getValueString(AlertsAction.FIELD_MIN_SEVERITY)) ? input.getValueString(AlertsAction.FIELD_MIN_SEVERITY) : "WARNING")
         .withValue("deliveryMode", StringUtils.hasContent(input.getValueString(AlertsAction.FIELD_DELIVERY_MODE)) ? input.getValueString(AlertsAction.FIELD_DELIVERY_MODE) : "IMMEDIATE")
         .withValue("events", "RAISED,ESCALATED,REMINDER,RESOLVED")
         .withValue("enabled", true)));
      throwIfErrors(inserted);
      return (true);
   }



   /*******************************************************************************
    ** Create a silence-notifications mute from now (through the table).
    *******************************************************************************/
   private static boolean mute(ScopeType scopeType, String scopeValue, String what, RunBackendStepInput input, String actor) throws QException
   {
      Integer minutes = input.getValueInteger(AlertsAction.FIELD_MUTE_MINUTES);
      String  reason  = input.getValueString(AlertsAction.FIELD_NOTE);
      if(!StringUtils.hasContent(reason))
      {
         throw (new QUserFacingException("A reason is required."));
      }
      Instant       now      = runtime().now();
      List<QRecord> inserted = InsertAction.executeForRecords(new InsertInput(AlertsMute.TABLE_NAME).withRecord(new QRecord()
         .withValue("name", "Mute " + what)
         .withValue("reason", reason)
         .withValue("scopeType", scopeType.name())
         .withValue("scopeValue", scopeValue)
         .withValue("startsAt", now)
         .withValue("endsAt", now.plus(minutes == null || minutes < 1 ? 60 : minutes, ChronoUnit.MINUTES))
         .withValue("effect", MuteEffect.SILENCE_NOTIFICATIONS.name())
         .withValue("enabled", true)));
      throwIfErrors(inserted);
      return (true);
   }



   /*******************************************************************************
    ** A TABLE_QUERY monitor for the type's subject table (disabled, to be
    ** finished by the admin), or a heartbeat monitor when it has none.
    *******************************************************************************/
   private static boolean createMonitor(QRecord type) throws QException
   {
      String subject = type.getValueString("subjectTableName");
      String kind    = subject == null ? "HEARTBEAT" : "TABLE_QUERY";
      String json    = subject == null
         ? JsonUtils.toJson(Map.of("heartbeatKey", type.getValueString("name"), "expectedEverySeconds", 86400, "graceSeconds", 3600))
         : JsonUtils.toJson(Map.of("tableName", subject, "filter", Map.of("criteria", List.of()), "threshold", 0));
      runtime().asSystem(() -> AlertsDb.insert(AlertsMonitor.TABLE_NAME, new QRecord()
         .withValue("name", type.getValueString("label") + " monitor")
         .withValue("alertTypeId", type.getValue("id"))
         .withValue("kind", kind)
         .withValue("enabled", false)
         .withValue("seeded", false)
         .withValue("description", "Created from the alert type. Edit the definition, then enable it.")
         .withValue("definition", json)
         .withValue("perRecord", false)
         .withValue("scheduleSeconds", 300)
         .withValue("forMinutes", 0)
         .withValue("consecutiveFailures", 0)));
      return (true);
   }



   /*******************************************************************************
    ** Lines describing a monitor evaluation (Review: would; Result: did).
    *******************************************************************************/
   static List<ProcessSummaryLine> planLines(MonitorEvaluator.Plan plan, boolean applied)
   {
      List<ProcessSummaryLine> lines = new ArrayList<>();
      if(plan.status() == MonitorStatus.CHECK_FAILED)
      {
         lines.add(new ProcessSummaryLine(Status.ERROR, null, "The check failed: " + plan.error()));
         return (lines);
      }
      if(plan.status() == MonitorStatus.SKIPPED_MUTED)
      {
         lines.add(new ProcessSummaryLine(Status.INFO, null, "A maintenance window covers this monitor, so it was not checked."));
         return (lines);
      }
      String verb = applied ? "" : "Would ";
      lines.add(new ProcessSummaryLine(Status.INFO, null, "Result: " + plan.value()));
      if(plan.status() == MonitorStatus.PENDING)
      {
         lines.add(new ProcessSummaryLine(Status.WARNING, null, "The condition holds, but not yet for the monitor's \"must hold for\" time, so no alert " + (applied ? "was" : "would be") + " opened."));
      }
      lines.add(new ProcessSummaryLine(plan.wouldOpen().isEmpty() ? Status.INFO : Status.WARNING, null, (applied ? "Opened " : verb + "open ") + count(plan.wouldOpen().size(), "alert") + "."));
      lines.add(new ProcessSummaryLine(Status.INFO, null, (applied ? "Updated " : verb + "update ") + count(plan.wouldUpdate().size(), "open alert") + "."));
      lines.add(new ProcessSummaryLine(Status.OK, null, (applied ? "Resolved " : verb + "resolve ") + count(plan.wouldResolve().size(), "alert") + "."));
      return (lines);
   }



   /*******************************************************************************
    ** Review lines for an audience: who it reaches, a sample, and warnings.
    *******************************************************************************/
   static List<ProcessSummaryLine> audienceLines(AlertsAudienceResolver.Resolved resolved, boolean preview)
   {
      List<ProcessSummaryLine> lines = new ArrayList<>();
      int                      size  = resolved.userIds().size();
      lines.add(new ProcessSummaryLine(size == 0 ? Status.WARNING : Status.INFO, null, (preview ? "Would reach " : "Will reach up to ") + count(size, "user") + "."));
      if(size > 0)
      {
         List<String> sample = resolved.userIds().stream().limit(AUDIENCE_SAMPLE).map(userId -> resolved.labels().getOrDefault(userId, userId)).toList();
         lines.add(new ProcessSummaryLine(Status.INFO, null, "For example: " + String.join(", ", sample) + (size > sample.size() ? ", and " + (size - sample.size()) + " more." : ".")));
      }
      for(String warning : resolved.warnings())
      {
         lines.add(new ProcessSummaryLine(Status.WARNING, null, warning));
      }
      return (lines);
   }



   /*******************************************************************************
    ** The action this process run is.
    *******************************************************************************/
   static AlertsAction action(RunBackendStepInput input) throws QException
   {
      AlertsAction action = AlertsAction.forProcess(input.getProcessName());
      if(action == null)
      {
         throw (new QException("Not an Alerts action: " + input.getProcessName()));
      }
      return (action);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static AlertsRuntime runtime() throws QException
   {
      return (AlertsRuntime.get());
   }



   /*******************************************************************************
    ** What the action's records are called.
    *******************************************************************************/
   static String noun(AlertsAction action)
   {
      return switch(action.getTableName())
      {
         case "alerts_alert" -> "alert";
         case "alerts_type" -> "alert type";
         case "alerts_group" -> "group";
         case "alerts_monitor" -> "monitor";
         case "alerts_channel" -> "channel";
         case "alerts_delivery" -> "delivery";
         case "alerts_notification" -> "notification";
         default -> "record";
      };
   }



   /*******************************************************************************
    ** The participle for the action ("acknowledged", "snoozed", ...).
    *******************************************************************************/
   static String pastVerb(AlertsAction action)
   {
      return switch(action)
      {
         case ACKNOWLEDGE -> "acknowledged";
         case SNOOZE -> "snoozed";
         case RESOLVE -> "resolved";
         case REOPEN -> "reopened";
         case ASSIGN -> "assigned";
         case COMMENT -> "commented on";
         case SUBSCRIBE_TYPE, SUBSCRIBE_GROUP, SUBSCRIBE_MONITOR -> "subscribed to";
         case MUTE_ALERT, MUTE_TYPE, MUTE_GROUP, MUTE_MONITOR -> "muted";
         case RUN_CHECK -> "checked";
         case ENABLE_MONITOR -> "enabled";
         case DISABLE_MONITOR -> "disabled";
         case SEND_TEST -> "tested";
         case RETRY_DELIVERY -> "queued again";
         case CREATE_MONITOR -> "given a new (disabled) monitor";
         case PREVIEW_AUDIENCE -> "previewed";
         case SEND_NOTIFICATION -> "sent";
         case SCHEDULE_NOTIFICATION -> "scheduled";
         case RETRACT_NOTIFICATION -> "retracted";
      };
   }



   /*******************************************************************************
    ** "alert" to "alerts", "alert type" to "alert types".
    *******************************************************************************/
   static String plural(String noun)
   {
      return (noun + "s");
   }



   /*******************************************************************************
    ** "1 alert", "3 alerts".
    *******************************************************************************/
   static String count(int n, String noun)
   {
      return (n + " " + (n == 1 ? noun : noun + "s"));
   }



   /*******************************************************************************
    ** A counted summary line.
    *******************************************************************************/
   static ProcessSummaryLine line(Status status, String singularFuture, String pluralFuture, String singularPast, String pluralPast)
   {
      ProcessSummaryLine line = new ProcessSummaryLine(status, 0, null);
      line.setSingularFutureMessage(singularFuture);
      line.setPluralFutureMessage(pluralFuture);
      line.setSingularPastMessage(singularPast);
      line.setPluralPastMessage(pluralPast);
      return (line);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void throwIfErrors(List<QRecord> records) throws QUserFacingException
   {
      for(QRecord record : records)
      {
         if(CollectionUtils.nullSafeHasContents(record.getErrors()))
         {
            throw (new QUserFacingException(record.getErrors().get(0).getMessage()));
         }
      }
   }
}
