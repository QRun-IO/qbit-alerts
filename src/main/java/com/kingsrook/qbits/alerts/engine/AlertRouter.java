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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import com.kingsrook.qbits.alerts.AlertAudienceResolver;
import com.kingsrook.qbits.alerts.AlertsRuntime;
import com.kingsrook.qbits.alerts.AlertsSecurity;
import com.kingsrook.qbits.alerts.engine.AlertsEngine.AlertTarget;
import com.kingsrook.qbits.alerts.model.AlertsAlert;
import com.kingsrook.qbits.alerts.model.AlertsChannel;
import com.kingsrook.qbits.alerts.model.AlertsDelivery;
import com.kingsrook.qbits.alerts.model.AlertsGroup;
import com.kingsrook.qbits.alerts.model.AlertsMute;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.ChannelType;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.DeliveryKind;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.DeliveryStatus;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.EventType;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.MemberSource;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.MuteEffect;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.ScopeType;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.SubscriberType;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.SuppressedReason;
import com.kingsrook.qbits.alerts.model.AlertsSubscription;
import com.kingsrook.qbits.alerts.model.AlertsTeam;
import com.kingsrook.qbits.alerts.model.AlertsTeamMember;
import com.kingsrook.qbits.alerts.model.AlertsType;
import com.kingsrook.qbits.alerts.model.AlertsUserPreference;
import com.kingsrook.qqq.backend.core.actions.collaboration.RecordFollowers;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper;
import com.kingsrook.qqq.backend.core.actions.permissions.TablePermissionSubType;
import com.kingsrook.qqq.backend.core.alerts.AlertSeverity;
import com.kingsrook.qqq.backend.core.context.CapturedContext;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.collaboration.FollowEvent;
import com.kingsrook.qqq.backend.core.model.metadata.collaboration.RecordFollower;
import com.kingsrook.qqq.backend.core.model.metadata.collaboration.RecordReference;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.modules.authentication.QAuthenticationModuleDispatcher;
import com.kingsrook.qqq.backend.core.modules.authentication.QAuthenticationModuleInterface;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Routing and fan-out (design section 5.4). For a notifiable event:
 ** 1. collect the enabled subscriptions whose scope matches the alert, whose
 **    minimum severity it meets, and whose events include this kind;
 ** 2. expand team subscriptions to their members; keep channel subscriptions;
 ** 3. check each person can see the alert (section 7.2);
 ** 4. apply, in order: the type's notify switch, mutes, flapping, the user's
 **    pause, quiet hours, channel minimum severity / enabled / address / rate limit, and a
 **    10-minute duplicate check;
 ** 5. write an alerts_delivery row per destination (SUPPRESSED rows keep
 **    their reason, so "why didn't I get it?" is answered by a table), and an
 **    ALERT notification recipient row per in-app recipient.
 ** In-app deliveries are complete when the recipient row is written; other
 ** channels are left PENDING for the delivery worker.
 *******************************************************************************/
public class AlertRouter
{
   private static final QLogger LOG = QLogger.getLogger(AlertRouter.class);

   private static final int DUPLICATE_WINDOW_MINUTES = 10;

   private final AlertsRuntime runtime;



   /*******************************************************************************
    ** One destination for one notification.
    *******************************************************************************/
   record Destination(String userId, QRecord channel, Integer subscriptionId, String minSeverity, QRecord preference, String deliveryMode)
   {
   }



   /*******************************************************************************
    ** Constructor
    *******************************************************************************/
   public AlertRouter(AlertsRuntime runtime)
   {
      this.runtime = runtime;
   }



   /*******************************************************************************
    ** The notification kind for a timeline event (null: not notifiable).
    *******************************************************************************/
   public static DeliveryKind kindFor(EventType eventType)
   {
      return switch(eventType)
      {
         case RAISED, REOPENED, SEVERITY_CHANGED, FLAPPING_STARTED -> DeliveryKind.RAISED;
         case RESOLVED -> DeliveryKind.RESOLVED;
         case ESCALATED -> DeliveryKind.ESCALATED;
         default -> null;
      };
   }



   /*******************************************************************************
    ** Fan an event out to its subscribers. Never throws: a routing failure is
    ** logged, and the alert itself is already recorded.
    *******************************************************************************/
   public void route(QRecord alert, EventType eventType, Integer eventId)
   {
      DeliveryKind kind = kindFor(eventType);
      if(alert == null || kind == null)
      {
         return;
      }

      try
      {
         deliver(alert, eventType, eventId, kind, destinations(AlertTarget.of(alert),
            AlertSeverity.fromLabel(alert.getValueString("severity"), AlertSeverity.WARNING), kind));
      }
      catch(Exception e)
      {
         LOG.warn("Error routing alert notifications", e, logPair("alertId", alert.getValue("id")), logPair("eventType", eventType));
      }
   }



   /*******************************************************************************
    ** A due reminder uses subscription event filters and the normal delivery checks.
    *******************************************************************************/
   public int remind(QRecord alert) throws QException
   {
      return (deliver(alert, null, null, DeliveryKind.REMINDER, destinations(AlertTarget.of(alert),
         AlertSeverity.fromLabel(alert.getValueString("severity"), AlertSeverity.WARNING), DeliveryKind.REMINDER)));
   }



   /*******************************************************************************
    ** A policy target uses the same channel, permission and suppression checks.
    ** Team members are read in bounded pages; no membership source is changed.
    *******************************************************************************/
   public void escalate(QRecord alert, QRecord step, Integer eventId) throws QException
   {
      String targetType = step.getValueString("targetType");
      if(SubscriberType.CHANNEL.name().equals(targetType))
      {
         QRecord channel = AlertsDb.byId(AlertsChannel.TABLE_NAME, step.getValue("channelId"));
         if(channel != null)
         {
            deliver(alert, EventType.ESCALATED, eventId, DeliveryKind.ESCALATED,
               List.of(new Destination(null, channel, null, null, null, "IMMEDIATE")));
         }
      }
      else if(SubscriberType.USER.name().equals(targetType))
      {
         escalateToUser(alert, step.getValueString("userId"), eventId);
      }
      else if(SubscriberType.TEAM.name().equals(targetType))
      {
         int lastId = 0;
         while(true)
         {
            List<QRecord> members = AlertsDb.query(AlertsTeamMember.TABLE_NAME, AlertsDb.eq("teamId", step.getValue("teamId"))
               .withCriteria("id", QCriteriaOperator.GREATER_THAN, lastId)
               .withOrderBy(new com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy("id", true)).withLimit(100));
            if(members.isEmpty())
            {
               break;
            }
            for(QRecord member : members)
            {
               escalateToUser(alert, member.getValueString("userId"), eventId);
               lastId = member.getValueInteger("id");
            }
         }
      }
   }



   /*******************************************************************************
    ** User and team targets use the user's normal destinations.
    *******************************************************************************/
   private void escalateToUser(QRecord alert, String userId, Integer eventId) throws QException
   {
      if(!StringUtils.hasContent(userId))
      {
         return;
      }
      QRecord preference = AlertsDb.first(AlertsUserPreference.TABLE_NAME, AlertsDb.eq("userId", userId));
      for(QRecord channel : userChannels(null, preference))
      {
         deliver(alert, EventType.ESCALATED, eventId, DeliveryKind.ESCALATED,
            List.of(new Destination(userId, channel, null, null, preference, "IMMEDIATE")));
      }
   }



   /*******************************************************************************
    ** Shared fan-out. Scheduled callers propagate failures so their transaction rolls back.
    *******************************************************************************/
   private int deliver(QRecord alert, EventType eventType, Integer eventId, DeliveryKind kind, List<Destination> destinations) throws QException
   {
      Instant       now      = runtime.now();
      AlertTarget   target   = AlertTarget.of(alert);
      QRecord       type     = AlertsDb.byId(AlertsType.TABLE_NAME, alert.getValue("alertTypeId"));
      AlertSeverity severity = AlertSeverity.fromLabel(alert.getValueString("severity"), AlertSeverity.WARNING);

      if(destinations.isEmpty())
      {
         return (0);
      }

      boolean silent    = type != null && Boolean.FALSE.equals(type.getValueBoolean("notify"));
      boolean muted     = isMuted(target, now, MuteEffect.SILENCE_NOTIFICATIONS);
      boolean flapping  = Boolean.TRUE.equals(alert.getValueBoolean("flapping")) && eventType != EventType.FLAPPING_STARTED;
      if(muted && !Boolean.TRUE.equals(alert.getValueBoolean("muted")))
      {
         AlertsDb.update(AlertsAlert.TABLE_NAME, new QRecord().withValue("id", alert.getValue("id")).withValue("muted", true));
         runtime.getEngine().event(alert.getValueInteger("id"), EventType.MUTED, null, null, null, "A mute silenced notifications", now);
      }

      Map<String, Optional<Boolean>> visibleByUser = new HashMap<>();
      int                  queued        = 0;
      for(Destination destination : destinations)
      {
         boolean digest = kind != DeliveryKind.REMINDER && kind != DeliveryKind.ESCALATED
            && List.of("DIGEST_HOURLY", "DIGEST_DAILY").contains(destination.deliveryMode() == null ? "IMMEDIATE" : destination.deliveryMode());
         SuppressedReason reason = null;
         String           channelType = destination.channel().getValueString("channelType");
         boolean          inApp       = ChannelType.IN_APP.name().equals(channelType);

         if(severity == AlertSeverity.INFO && !inApp && !AlertSeverity.INFO.name().equals(destination.minSeverity()))
         {
            continue;
         }
         if(!inApp && "alerts.channel.failing".equals(alert.getValueString("alertTypeName")))
         {
            ///////////////////////////////////////////////////////////////
            // a broken channel is only ever reported in-app (no loops) //
            ///////////////////////////////////////////////////////////////
            continue;
         }
         AlertSeverity channelMin = AlertSeverity.fromLabel(destination.channel().getValueString("minSeverity"), null);

         if(silent)
         {
            reason = SuppressedReason.TYPE_SILENT;
         }
         else if(muted)
         {
            reason = SuppressedReason.MUTED;
         }
         else if(flapping)
         {
            reason = SuppressedReason.FLAPPING;
         }
         else if(destination.userId() != null && !visibleByUser.computeIfAbsent(destination.userId(), userId -> knownVisibility(userId, alert))
            .orElse(inApp || (alert.getValue("visibilityPermission") == null && alert.getValue("subjectTableName") == null)))
         {
            reason = SuppressedReason.NOT_VISIBLE;
         }
         else if(destination.userId() != null && !inApp && isPaused(destination.preference(), now))
         {
            reason = SuppressedReason.USER_PAUSED;
         }
         else if(destination.userId() != null && !inApp && AlertTiming.isQuiet(destination.preference(), severity, now))
         {
            reason = SuppressedReason.QUIET_HOURS;
         }
         else if(channelMin != null && !severity.isAtLeast(channelMin) && !inApp)
         {
            reason = SuppressedReason.MIN_SEVERITY;
         }
         else if(Boolean.FALSE.equals(destination.channel().getValueBoolean("enabled")))
         {
            reason = SuppressedReason.CHANNEL_DISABLED;
         }
         else if(ChannelType.EMAIL.name().equals(channelType) && !StringUtils.hasContent(emailAddress(destination)))
         {
            reason = SuppressedReason.NO_ADDRESS;
         }
         else if(!inApp && isRateLimited(destination.channel(), now))
         {
            reason = SuppressedReason.RATE_LIMITED;
         }
         else if(kind != DeliveryKind.REMINDER && isDuplicate(alert, destination, digest ? DeliveryKind.DIGEST : kind, eventId, now))
         {
            reason = SuppressedReason.DUPLICATE;
         }

         QRecord notification = new QRecord()
            .withValue("alertId", alert.getValue("id"))
            .withValue("alertEventId", eventId)
            .withValue("subscriptionId", destination.subscriptionId())
            .withValue("channelId", destination.channel().getValue("id"))
            .withValue("recipientUserId", destination.userId())
            .withValue("recipientLabel", recipientLabel(destination))
            .withValue("kind", kind.name())
            .withValue("attempts", 0);

         if(reason != null)
         {
            notification.setValue("status", DeliveryStatus.SUPPRESSED.name());
            notification.setValue("suppressedReason", reason.name());
            AlertsDb.insert(AlertsDelivery.TABLE_NAME, notification);
         }
         else if(digest)
         {
            Instant end = AlertTiming.digestEnd(destination.deliveryMode(), destination.preference(), now);
            Instant start = AlertTiming.digestStart(destination.deliveryMode(), destination.preference(), end);
            String recipientKey = destination.userId() == null ? "alerts:channel-digest:" + destination.channel().getValue("id") : "alerts:digest:" + destination.userId();
            notification.withValue("kind", DeliveryKind.DIGEST.name()).withValue("status", DeliveryStatus.PENDING.name())
               .withValue("digestKey", recipientKey + ":" + start + ":" + destination.deliveryMode()).withValue("nextAttemptAt", end);
            AlertsDb.insert(AlertsDelivery.TABLE_NAME, notification);
         }
         else if(inApp)
         {
            notification.setValue("status", DeliveryStatus.SENT.name());
            notification.setValue("sentAt", now);
            QRecord inserted = AlertsDb.insert(AlertsDelivery.TABLE_NAME, notification);
            runtime.getNotifications().deliverAlert(destination.userId(), alert, now);
            LOG.debug("In-app alert delivered", logPair("deliveryId", inserted.getValue("id")));
            queued++;
         }
         else
         {
            notification.setValue("status", DeliveryStatus.PENDING.name());
            notification.setValue("nextAttemptAt", now);
            AlertsDb.insert(AlertsDelivery.TABLE_NAME, notification);
            queued++;
         }
      }

      if(queued > 0)
      {
         AlertsDb.update(AlertsAlert.TABLE_NAME, new QRecord().withValue("id", alert.getValue("id")).withValue("lastNotifiedAt", now));
         runtime.getEngine().event(alert.getValueInteger("id"), EventType.NOTIFIED, null, null, String.valueOf(queued),
            queued == 1 ? "1 notification" : queued + " notifications", now);
      }
      return (queued);
   }



   /*******************************************************************************
    ** Whether an active SUPPRESS_ALERTS maintenance window covers the target.
    *******************************************************************************/
   public boolean isSuppressedByMaintenance(AlertTarget target, Instant now) throws QException
   {
      return (isMuted(target, now, MuteEffect.SUPPRESS_ALERTS));
   }



   /*******************************************************************************
    ** Whether an enabled mute with this effect is active now and covers the
    ** target.
    *******************************************************************************/
   boolean isMuted(AlertTarget target, Instant now, MuteEffect effect) throws QException
   {
      List<QRecord> mutes = AlertsDb.query(AlertsMute.TABLE_NAME, AlertsDb.eq("enabled", true)
         .withCriteria("effect", QCriteriaOperator.EQUALS, effect.name())
         .withCriteria("startsAt", QCriteriaOperator.LESS_THAN_OR_EQUALS, now)
         .withCriteria("endsAt", QCriteriaOperator.GREATER_THAN, now));
      Map<Integer, List<String>> groupNames = new HashMap<>();
      for(QRecord mute : mutes)
      {
         if(scopeMatches(mute.getValueString("scopeType"), mute.getValueString("scopeValue"), mute.getValueString("scopeRecordId"), target, groupNames))
         {
            return (true);
         }
      }
      return (false);
   }



   /*******************************************************************************
    ** Every destination for an alert: subscribed users (directly or through a
    ** team) with their in-app and email channels (or the subscription's
    ** chosen channels), subscribed channels, and readable record followers.
    ** An alert that no subscription matches (at WARNING or above) goes to the
    ** members of its group's owner team, so an unowned problem is not silent.
    *******************************************************************************/
   List<Destination> destinations(AlertTarget target, AlertSeverity severity, DeliveryKind kind) throws QException
   {
      Map<Integer, List<String>> groupNames   = new HashMap<>();
      Map<String, Destination>   destinations = new LinkedHashMap<>();
      boolean                    subscribed   = false;
      for(QRecord subscription : AlertsDb.query(AlertsSubscription.TABLE_NAME, AlertsDb.eq("enabled", true)))
      {
         AlertSeverity min = AlertSeverity.fromLabel(subscription.getValueString("minSeverity"), AlertSeverity.WARNING);
         if(!severity.isAtLeast(min) || !includesKind(subscription.getValueString("events"), kind)
            || !scopeMatches(subscription.getValueString("scopeType"), subscription.getValueString("scopeValue"), subscription.getValueString("scopeRecordId"), target, groupNames))
         {
            continue;
         }

         subscribed = true;
         Integer subscriptionId = subscription.getValueInteger("id");
         String  minName        = subscription.getValueString("minSeverity");
         String  subscriberType = subscription.getValueString("subscriberType");
         if(SubscriberType.CHANNEL.name().equals(subscriberType))
         {
            QRecord channel = AlertsDb.byId(AlertsChannel.TABLE_NAME, subscription.getValue("channelId"));
            if(channel != null)
            {
               destinations.putIfAbsent("channel:" + channel.getValue("id"), new Destination(null, channel, subscriptionId, minName, null, subscription.getValueString("deliveryMode")));
            }
            continue;
         }

         Set<String> userIds = new LinkedHashSet<>();
         if(SubscriberType.TEAM.name().equals(subscriberType))
         {
            userIds.addAll(teamMembers(subscription.getValueInteger("teamId")));
         }
         else if(StringUtils.hasContent(subscription.getValueString("userId")))
         {
            userIds.add(subscription.getValueString("userId"));
         }

         for(String userId : userIds)
         {
            if(!StringUtils.hasContent(userId))
            {
               continue;
            }
            QRecord preference = AlertsDb.first(AlertsUserPreference.TABLE_NAME, AlertsDb.eq("userId", userId));
            for(QRecord channel : userChannels(subscription, preference))
            {
               destinations.putIfAbsent("user:" + userId + ":" + channel.getValue("id"), new Destination(userId, channel, subscriptionId, minName, preference, subscription.getValueString("deliveryMode")));
            }
         }
      }
      if(!subscribed && severity.isAtLeast(AlertSeverity.WARNING))
      {
         addOwnerTeam(destinations, target);
      }
      for(String recordKey : kind == DeliveryKind.REMINDER ? java.util.Set.<String>of() : target.recordKeys())
      {
         String[] parts = recordKey.split(":", 2);
         RecordReference reference = new RecordReference(parts[0], parts[1]);
         for(RecordFollower follower : RecordFollowers.get(runtime.getQInstance(), reference, FollowEvent.ALERT))
         {
            String userId = follower == null ? null : follower.getUserId();
            if(!StringUtils.hasContent(userId) || !canReadRecord(userId, reference))
            {
               continue;
            }
            QRecord preference = AlertsDb.first(AlertsUserPreference.TABLE_NAME, AlertsDb.eq("userId", userId));
            for(QRecord channel : userChannels(null, preference))
            {
               destinations.putIfAbsent("user:" + userId + ":" + channel.getValue("id"), new Destination(userId, channel, null, null, preference, "IMMEDIATE"));
            }
         }
      }
      return (new ArrayList<>(destinations.values()));
   }



   /*******************************************************************************
    ** The fallback for an alert nobody subscribed to: the members of the owner
    ** team of its group (or of the nearest parent group that has one), through
    ** their normal channels and the usual suppression checks.
    *******************************************************************************/
   private void addOwnerTeam(Map<String, Destination> destinations, AlertTarget target) throws QException
   {
      Integer teamId = ownerTeamId(target.groupId());
      if(teamId == null)
      {
         return;
      }
      for(String userId : teamMembers(teamId))
      {
         if(!StringUtils.hasContent(userId))
         {
            continue;
         }
         QRecord preference = AlertsDb.first(AlertsUserPreference.TABLE_NAME, AlertsDb.eq("userId", userId));
         for(QRecord channel : userChannels(null, preference))
         {
            destinations.putIfAbsent("user:" + userId + ":" + channel.getValue("id"), new Destination(userId, channel, null, null, preference, "IMMEDIATE"));
         }
      }
   }



   /*******************************************************************************
    ** The owner team of a group, or of the nearest parent that has one (null:
    ** none).
    *******************************************************************************/
   Integer ownerTeamId(Integer groupId) throws QException
   {
      Integer current = groupId;
      int     depth   = 0;
      while(current != null && depth++ < 10)
      {
         QRecord group = AlertsDb.byId(AlertsGroup.TABLE_NAME, current);
         if(group == null)
         {
            return (null);
         }
         if(group.getValueInteger("ownerTeamId") != null)
         {
            return (group.getValueInteger("ownerTeamId"));
         }
         current = group.getValueInteger("parentGroupId");
      }
      return (null);
   }



   /*******************************************************************************
    ** Explicit members plus the team's optional host-managed source. Missing
    ** or failed resolvers contribute nobody and leave an actionable log entry.
    *******************************************************************************/
   private Set<String> teamMembers(Integer teamId) throws QException
   {
      Set<String> userIds = new LinkedHashSet<>();
      QRecord team = AlertsDb.byId(AlertsTeam.TABLE_NAME, teamId);
      if(team == null)
      {
         return (userIds);
      }
      for(QRecord member : AlertsDb.query(AlertsTeamMember.TABLE_NAME, AlertsDb.eq("teamId", teamId).withLimit(Math.addExact(runtime.getConfig().getMaxAudienceSize(), 1))))
      {
         if(userIds.size() >= runtime.getConfig().getMaxAudienceSize())
         {
            LOG.warn("Team audience exceeds maxAudienceSize; extra members are not notified", logPair("teamId", teamId));
            break;
         }
         userIds.add(member.getValueString("userId"));
      }
      String source = team.getValueString("memberSource");
      if(MemberSource.ROLE.name().equals(source) || MemberSource.IDP_GROUP.name().equals(source))
      {
         AlertAudienceResolver resolver = runtime.getConfig().getAudienceResolver();
         if(resolver == null)
         {
            LOG.warn("Team source resolves to nobody: no host audience resolver is configured", logPair("teamId", teamId), logPair("source", source));
         }
         else
         {
            try
            {
               Set<String> resolved = resolver.resolve(source, team.getValueString("memberSourceValue"));
               if(resolved != null)
               {
                  if(resolved.size() > runtime.getConfig().getMaxAudienceSize()
                     || resolved.stream().filter(userId -> !userIds.contains(userId)).count() > runtime.getConfig().getMaxAudienceSize() - userIds.size())
                  {
                     throw (new QException("Team audience exceeds maxAudienceSize"));
                  }
                  userIds.addAll(resolved);
               }
            }
            catch(Exception e)
            {
               LOG.warn("Team source resolves to nobody: host audience resolver failed", logPair("teamId", teamId), logPair("source", source));
            }
         }
      }
      return (userIds);
   }



   /*******************************************************************************
    ** A user's channels for a subscription: the ones it names, else the user's
    ** own default channels (My Alert Settings), else in-app (unless turned off)
    ** and email (when enabled in their settings).
    *******************************************************************************/
   private List<QRecord> userChannels(QRecord subscription, QRecord preference) throws QException
   {
      List<QRecord> channels  = new ArrayList<>();
      String        overrides = subscription == null ? null : subscription.getValueString("channelOverrideIds");
      if(StringUtils.hasContent(overrides))
      {
         return (channelsById(overrides));
      }

      String defaults = preference == null ? null : preference.getValueString("defaultChannelIds");
      if(StringUtils.hasContent(defaults))
      {
         channels.addAll(channelsById(defaults));
         if(!channels.isEmpty())
         {
            return (channels);
         }
      }

      if(preference == null || !Boolean.FALSE.equals(preference.getValueBoolean("inAppEnabled")))
      {
         channels.add(runtime.getChannels().defaultChannel(ChannelType.IN_APP));
      }
      if(preference != null && Boolean.TRUE.equals(preference.getValueBoolean("emailEnabled")) && StringUtils.hasContent(preference.getValueString("emailAddress")))
      {
         channels.add(runtime.getChannels().defaultChannel(ChannelType.EMAIL));
      }
      return (channels);
   }



   /*******************************************************************************
    ** The channel rows for a list of ids ("3, 5"); ids that are not numbers or
    ** no longer exist are skipped.
    *******************************************************************************/
   private List<QRecord> channelsById(String ids) throws QException
   {
      List<QRecord> channels = new ArrayList<>();
      for(String id : ids.split("[,\\s]+"))
      {
         if(id.isBlank())
         {
            continue;
         }
         try
         {
            QRecord channel = AlertsDb.byId(AlertsChannel.TABLE_NAME, Integer.valueOf(id.trim()));
            if(channel != null)
            {
               channels.add(channel);
            }
         }
         catch(NumberFormatException e)
         {
            LOG.debug("Ignoring a channel id that is not a number", logPair("id", id));
         }
      }
      return (channels);
   }



   /*******************************************************************************
    ** Whether a subscription or mute scope covers the target.
    *******************************************************************************/
   boolean scopeMatches(String scopeType, String scopeValue, String scopeRecordId, AlertTarget target, Map<Integer, List<String>> groupNames) throws QException
   {
      ScopeType type = scopeType == null ? ScopeType.ALL : ScopeType.valueOf(scopeType);
      return switch(type)
      {
         case ALL -> true;
         case ALERT -> scopeValue != null && scopeValue.equals(target.alertKey());
         case ALERT_TYPE -> scopeValue != null && scopeValue.equals(target.typeName());
         case MONITOR -> scopeValue != null && target.monitorId() != null && scopeValue.equals(String.valueOf(target.monitorId()));
         case TABLE -> scopeValue != null && (scopeValue.equals(target.subjectTableName()) || target.recordKeys().stream().anyMatch(key -> key.startsWith(scopeValue + ":")));
         case RECORD -> scopeValue != null && scopeRecordId != null && target.recordKeys().contains(scopeValue + ":" + scopeRecordId);
         case ATTRIBUTE -> attributeMatches(scopeValue, target.attributes());
         case GROUP -> scopeValue != null && target.groupId() != null && groupNamesWithParents(target.groupId(), groupNames).contains(scopeValue);
      };
   }



   /*******************************************************************************
    ** The group's name and id, and its parents', for GROUP scopes.
    *******************************************************************************/
   private List<String> groupNamesWithParents(Integer groupId, Map<Integer, List<String>> cache) throws QException
   {
      List<String> cached = cache.get(groupId);
      if(cached != null)
      {
         return (cached);
      }
      List<String> names   = new ArrayList<>();
      Integer      current = groupId;
      int          depth   = 0;
      while(current != null && depth++ < 10)
      {
         QRecord group = AlertsDb.byId(AlertsGroup.TABLE_NAME, current);
         if(group == null)
         {
            break;
         }
         names.add(group.getValueString("name"));
         names.add(String.valueOf(group.getValue("id")));
         current = group.getValueInteger("parentGroupId");
      }
      cache.put(groupId, names);
      return (names);
   }



   /*******************************************************************************
    ** "key=value" against the alert's attributes.
    *******************************************************************************/
   private static boolean attributeMatches(String scopeValue, Map<String, String> attributes)
   {
      if(scopeValue == null || attributes == null || !scopeValue.contains("="))
      {
         return (false);
      }
      String[] parts = scopeValue.split("=", 2);
      return (parts[1].trim().equals(attributes.get(parts[0].trim())));
   }



   /*******************************************************************************
    ** Whether a subscription's events list includes this kind (empty: all).
    *******************************************************************************/
   private static boolean includesKind(String events, DeliveryKind kind)
   {
      if(!StringUtils.hasContent(events))
      {
         return (true);
      }
      for(String event : events.split("[,\\s]+"))
      {
         if(event.trim().equalsIgnoreCase(kind.name()))
         {
            return (true);
         }
      }
      return (false);
   }



   /*******************************************************************************
    ** Whether a user can see an alert (section 7.2), when that can be known:
    ** with a host whose authentication module builds sessions with permissions
    ** for a user id, the alert is read through that session's record locks.
    ** Otherwise (empty) the user's permissions are unknown, and the caller
    ** allows in-app (the bell re-checks the alert under the reader's session)
    ** but sends externally only alerts with no visibility permission and no
    ** subject table.
    *******************************************************************************/
   Optional<Boolean> knownVisibility(String userId, QRecord alert)
   {
      try
      {
         QSession session = automatedSession(userId);
         if(session != null && CollectionUtils.nullSafeHasContents(session.getPermissions()))
         {
            QInstance qInstance = runtime.getQInstance();
            AlertsSecurity.applyToSession(qInstance, session);
            boolean[] visible = new boolean[1];
            QContext.withTemporaryContext(new CapturedContext(qInstance, session), () ->
            {
               visible[0] = AlertsDb.byId(AlertsAlert.TABLE_NAME, alert.getValue("id")) != null;
            });
            return (Optional.of(visible[0]));
         }
      }
      catch(Exception e)
      {
         LOG.debug("Could not build a session to check alert visibility", e, logPair("userId", userId));
      }
      return (Optional.empty());
   }



   /*******************************************************************************
    ** Recheck queued digest content before sending it to a user.
    *******************************************************************************/
   public boolean canReceiveDigest(String userId, QRecord alert)
   {
      return (knownVisibility(userId, alert).orElse(alert.getValue("visibilityPermission") == null && alert.getValue("subjectTableName") == null));
   }



   /*******************************************************************************
    ** Followers must be able to read the source record, including its record locks.
    ** Reuse recipient sessions; unknown access fails closed before adding channels.
    *******************************************************************************/
   private boolean canReadRecord(String userId, RecordReference reference)
   {
      try
      {
         QSession session = automatedSession(userId);
         if(session == null || !CollectionUtils.nullSafeHasContents(session.getPermissions()))
         {
            return (false);
         }
         QInstance instance = runtime.getQInstance();
         AlertsSecurity.applyToSession(instance, session);
         boolean[] readable = new boolean[1];
         QContext.withTemporaryContext(new CapturedContext(instance, session), () ->
         {
            String primaryKey = instance.getTable(reference.tableName()).getPrimaryKeyField();
            readable[0] = PermissionsHelper.hasTablePermission(new AbstractActionInput(), reference.tableName(), TablePermissionSubType.READ)
               && AlertsDb.first(reference.tableName(), AlertsDb.eq(primaryKey, reference.primaryKey())) != null;
         });
         return (readable[0]);
      }
      catch(Exception e)
      {
         LOG.debug("Could not verify follower source-record access", e, logPair("userId", userId));
         return (false);
      }
   }



   /*******************************************************************************
    ** A session for a user, from the instance's authentication module (null
    ** when it can't make one).
    *******************************************************************************/
   private QSession automatedSession(String userId) throws QException
   {
      QInstance qInstance = runtime.getQInstance();
      if(qInstance.getAuthentication() == null)
      {
         return (null);
      }
      QAuthenticationModuleInterface module = new QAuthenticationModuleDispatcher().getQModule(qInstance.getAuthentication());
      return (module.createAutomatedSessionForUser(qInstance, userId));
   }



   /*******************************************************************************
    ** Whether the user paused notifications (vacation) until after now.
    *******************************************************************************/
   public static boolean isPaused(QRecord preference, Instant now)
   {
      return (preference != null && preference.getValueInstant("pausedUntil") != null && preference.getValueInstant("pausedUntil").isAfter(now));
   }



   /*******************************************************************************
    ** Whether a channel has reached its hourly limit.
    *******************************************************************************/
   private boolean isRateLimited(QRecord channel, Instant now) throws QException
   {
      Integer limit = channel.getValueInteger("rateLimitPerHour");
      if(limit == null || limit <= 0)
      {
         return (false);
      }
      int lastHour = AlertsDb.count(AlertsDelivery.TABLE_NAME, AlertsDb.eq("channelId", channel.getValue("id"))
         .withCriteria("status", QCriteriaOperator.IN, List.of(DeliveryStatus.PENDING.name(), DeliveryStatus.SENDING.name(), DeliveryStatus.SENT.name(), DeliveryStatus.RETRYING.name()))
         .withCriteria("createDate", QCriteriaOperator.GREATER_THAN, now.minus(1, ChronoUnit.HOURS)));
      return (lastHour >= limit);
   }



   /*******************************************************************************
    ** The same alert, channel, recipient and kind within 10 minutes.
    *******************************************************************************/
   private boolean isDuplicate(QRecord alert, Destination destination, DeliveryKind kind, Integer eventId, Instant now) throws QException
   {
      var filter = AlertsDb.eq("alertId", alert.getValue("id"))
         .withCriteria("channelId", QCriteriaOperator.EQUALS, destination.channel().getValue("id"))
         .withCriteria("kind", QCriteriaOperator.EQUALS, kind.name())
         .withCriteria("status", QCriteriaOperator.IN, List.of(DeliveryStatus.PENDING.name(), DeliveryStatus.SENDING.name(), DeliveryStatus.SENT.name(), DeliveryStatus.RETRYING.name()))
         .withCriteria("createDate", QCriteriaOperator.GREATER_THAN, now.minus(DUPLICATE_WINDOW_MINUTES, ChronoUnit.MINUTES));
      if(kind == DeliveryKind.DIGEST)
      {
         filter.withCriteria("alertEventId", QCriteriaOperator.EQUALS, eventId);
      }
      if(kind == DeliveryKind.ESCALATED)
      {
         filter = AlertsDb.eq("alertId", alert.getValue("id"))
            .withCriteria("channelId", QCriteriaOperator.EQUALS, destination.channel().getValue("id"))
            .withCriteria("alertEventId", QCriteriaOperator.EQUALS, eventId)
            .withCriteria("status", QCriteriaOperator.NOT_EQUALS, DeliveryStatus.SUPPRESSED.name());
      }
      if(destination.userId() != null)
      {
         filter.withCriteria("recipientUserId", QCriteriaOperator.EQUALS, destination.userId());
      }
      return (AlertsDb.count(AlertsDelivery.TABLE_NAME, filter) > 0);
   }



   /*******************************************************************************
    ** The email address for a destination.
    *******************************************************************************/
   static String emailAddress(Destination destination)
   {
      return (destination.preference() == null ? null : destination.preference().getValueString("emailAddress"));
   }



   /*******************************************************************************
    ** Who a notification went to, masked (j***@example.com).
    *******************************************************************************/
   static String recipientLabel(Destination destination)
   {
      String channelType = destination.channel().getValueString("channelType");
      if(destination.userId() == null)
      {
         return (destination.channel().getValueString("label"));
      }
      if(ChannelType.EMAIL.name().equals(channelType))
      {
         return (maskEmail(emailAddress(destination)));
      }
      QRecord preference = destination.preference();
      String  name       = preference == null ? null : preference.getValueString("userLabel");
      return ((StringUtils.hasContent(name) ? name : destination.userId()) + " (in app)");
   }



   /*******************************************************************************
    ** "jane@example.com" to "j***@example.com".
    *******************************************************************************/
   public static String maskEmail(String address)
   {
      if(address == null || !address.contains("@"))
      {
         return (address == null ? null : "***");
      }
      int at = address.indexOf('@');
      return ((at > 0 ? address.charAt(0) : "") + "***" + address.substring(at));
   }
}
