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

package com.kingsrook.qbits.alerts.model;


import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.kingsrook.qqq.backend.core.alerts.AlertMonitorKind;
import com.kingsrook.qqq.backend.core.alerts.AlertSeverity;
import com.kingsrook.qqq.backend.core.alerts.DetailLevel;
import com.kingsrook.qqq.backend.core.alerts.NotificationAudience;
import com.kingsrook.qqq.backend.core.alerts.NotificationCategory;
import com.kingsrook.qqq.backend.core.alerts.NotificationSeverity;
import com.kingsrook.qqq.backend.core.alerts.ResolveMode;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValue;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSourceType;


/*******************************************************************************
 ** The qbit's enumerated values, stored as strings, and their possible-value
 ** sources (named alerts_(name), labels in Title Case).
 *******************************************************************************/
public final class AlertsPossibleValues
{
   public static final String SEVERITY            = "alerts_severity";
   public static final String STATUS              = "alerts_status";
   public static final String RESOLVE_MODE        = "alerts_resolve_mode";
   public static final String DETAIL_LEVEL        = "alerts_detail_level";
   public static final String MONITOR_KIND        = "alerts_monitor_kind";
   public static final String MONITOR_STATUS      = "alerts_monitor_status";
   public static final String EVENT_TYPE          = "alerts_event_type";
   public static final String RESOLUTION          = "alerts_resolution";
   public static final String SUBSCRIBER_TYPE     = "alerts_subscriber_type";
   public static final String SCOPE_TYPE          = "alerts_scope_type";
   public static final String DELIVERY_MODE       = "alerts_delivery_mode";
   public static final String MEMBER_SOURCE       = "alerts_member_source";
   public static final String TEAM_ROLE           = "alerts_team_role";
   public static final String CHANNEL_TYPE        = "alerts_channel_type";
   public static final String HEALTH_STATUS       = "alerts_health_status";
   public static final String MUTE_EFFECT         = "alerts_mute_effect";
   public static final String DELIVERY_KIND       = "alerts_delivery_kind";
   public static final String DELIVERY_STATUS     = "alerts_delivery_status";
   public static final String SUPPRESSED_REASON   = "alerts_suppressed_reason";
   public static final String NOTIFICATION_SEVERITY = "alerts_notification_severity";
   public static final String NOTIFICATION_CATEGORY = "alerts_notification_category";
   public static final String NOTIFICATION_STATUS   = "alerts_notification_status";
   public static final String AUDIENCE_TYPE         = "alerts_audience_type";
   public static final String EMAIL_STATUS          = "alerts_email_status";
   public static final String EMAIL_MODE            = "alerts_email_mode";
   public static final String SLACK_STATUS          = "alerts_slack_status";



   /*******************************************************************************
    ** An alert's lifecycle state.
    *******************************************************************************/
   public enum Status
   {
      OPEN, ACKNOWLEDGED, SNOOZED, RESOLVED;



      /*******************************************************************************
       ** Every state but RESOLVED.
       *******************************************************************************/
      public static List<String> openNames()
      {
         return (List.of(OPEN.name(), ACKNOWLEDGED.name(), SNOOZED.name()));
      }
   }



   /*******************************************************************************
    ** A monitor's last evaluation.
    *******************************************************************************/
   public enum MonitorStatus
   {
      OK, FIRING, PENDING, CHECK_FAILED, SKIPPED_MUTED
   }



   /*******************************************************************************
    ** Timeline entries.
    *******************************************************************************/
   public enum EventType
   {
      RAISED, REPEATED, SEVERITY_CHANGED, ACKNOWLEDGED, SNOOZED, UNSNOOZED, ASSIGNED, COMMENTED, ESCALATED, NOTIFIED, MUTED, FLAPPING_STARTED,
      FLAPPING_ENDED, RESOLVED, REOPENED
   }



   /*******************************************************************************
    ** How an alert was resolved.
    *******************************************************************************/
   public enum Resolution
   {
      PRODUCER, MONITOR, QUIET_PERIOD, MANUAL, EXPIRED
   }



   /*******************************************************************************
    ** Who a subscription notifies.
    *******************************************************************************/
   public enum SubscriberType
   {
      USER, TEAM, CHANNEL
   }



   /*******************************************************************************
    ** What a subscription or mute covers.
    *******************************************************************************/
   public enum ScopeType
   {
      ALL, GROUP, ALERT_TYPE, MONITOR, TABLE, RECORD, ATTRIBUTE, ALERT
   }



   /*******************************************************************************
    ** When a subscription's notifications are sent.
    *******************************************************************************/
   public enum DeliveryMode
   {
      IMMEDIATE, DIGEST_HOURLY, DIGEST_DAILY
   }



   /*******************************************************************************
    ** Where a team's members come from.
    *******************************************************************************/
   public enum MemberSource
   {
      MANUAL, ROLE, IDP_GROUP
   }



   /*******************************************************************************
    ** A member's role in a team.
    *******************************************************************************/
   public enum TeamRole
   {
      MEMBER, LEAD
   }



   /*******************************************************************************
    ** Channel kinds (built-in: IN_APP, EMAIL, EMAIL_LIST; the rest arrive with
    ** their channel implementations).
    *******************************************************************************/
   public enum ChannelType
   {
      IN_APP, EMAIL, EMAIL_LIST, SLACK, TEAMS, WEBHOOK, PAGERDUTY, ALERTMANAGER
   }



   /*******************************************************************************
    ** Channel delivery health (the qbit-webhooks model).
    *******************************************************************************/
   public enum HealthStatus
   {
      HEALTHY, PROBATION, UNHEALTHY
   }



   /*******************************************************************************
    ** What a mute does.
    *******************************************************************************/
   public enum MuteEffect
   {
      SILENCE_NOTIFICATIONS, SUPPRESS_ALERTS
   }



   /*******************************************************************************
    ** Why a notification was sent.
    *******************************************************************************/
   public enum DeliveryKind
   {
      RAISED, ESCALATED, REMINDER, RESOLVED, DIGEST, TEST
   }



   /*******************************************************************************
    ** A notification's delivery state.
    *******************************************************************************/
   public enum DeliveryStatus
   {
      PENDING, SENDING, SENT, RETRYING, FAILED, DEAD, SUPPRESSED
   }



   /*******************************************************************************
    ** Why a notification was not sent.
    *******************************************************************************/
   public enum SuppressedReason
   {
      QUIET_HOURS, MUTED, FLAPPING, RATE_LIMITED, NOT_VISIBLE, CHANNEL_DISABLED, DUPLICATE, USER_PAUSED, TYPE_SILENT, NO_ADDRESS, MIN_SEVERITY
   }



   /*******************************************************************************
    ** Constants and builders only.
    *******************************************************************************/
   private AlertsPossibleValues()
   {
   }



   /*******************************************************************************
    ** Every possible-value source the qbit's fields use (the table-backed ones
    ** are built with the tables).
    *******************************************************************************/
   public static List<QPossibleValueSource> buildEnumSources()
   {
      return (List.of(
         enumSource(SEVERITY, AlertSeverity.values()),
         enumSource(STATUS, Status.values()),
         enumSource(RESOLVE_MODE, ResolveMode.values()),
         enumSource(DETAIL_LEVEL, DetailLevel.values()),
         enumSource(MONITOR_KIND, AlertMonitorKind.values()),
         enumSource(MONITOR_STATUS, MonitorStatus.values()),
         enumSource(EVENT_TYPE, EventType.values()),
         enumSource(RESOLUTION, new Resolution[] { Resolution.PRODUCER, Resolution.MONITOR, Resolution.QUIET_PERIOD, Resolution.MANUAL }),
         enumSource(SUBSCRIBER_TYPE, SubscriberType.values()),
         enumSource(SCOPE_TYPE, ScopeType.values()),
         enumSource(DELIVERY_MODE, DeliveryMode.values()),
         enumSource(MEMBER_SOURCE, MemberSource.values()),
         enumSource(TEAM_ROLE, TeamRole.values()),
         enumSource(CHANNEL_TYPE, ChannelType.values()),
         enumSource(HEALTH_STATUS, HealthStatus.values()),
         enumSource(MUTE_EFFECT, MuteEffect.values()),
         enumSource(DELIVERY_KIND, DeliveryKind.values()),
         enumSource(DELIVERY_STATUS, DeliveryStatus.values()),
         enumSource(SUPPRESSED_REASON, SuppressedReason.values()),
         enumSource(NOTIFICATION_SEVERITY, NotificationSeverity.values()),
         enumSource(NOTIFICATION_CATEGORY, NotificationCategory.values()),
         enumSource(NOTIFICATION_STATUS, NotificationStatus.values()),
         enumSource(AUDIENCE_TYPE, NotificationAudience.Type.values()),
         enumSource(EMAIL_STATUS, EmailStatus.values()),
         enumSource(EMAIL_MODE, EmailMode.values()),
         enumSource(SLACK_STATUS, SlackStatus.values())));
   }



   /*******************************************************************************
    ** A user notification's state.
    *******************************************************************************/
   public enum NotificationStatus
   {
      DRAFT, SCHEDULED, SENT, RETRACTED
   }



   /*******************************************************************************
    ** Whether (and how) a recipient was emailed.
    *******************************************************************************/
   public enum EmailStatus
   {
      NONE, PENDING, SENT, DIGESTED, SKIPPED, FAILED
   }



   /*******************************************************************************
    ** Whether (and how) a recipient was sent a Slack direct message.
    *******************************************************************************/
   public enum SlackStatus
   {
      NONE, PENDING, SENT, DIGESTED, SKIPPED, FAILED
   }



   /*******************************************************************************
    ** How a user wants a category by email (also used for Slack: same modes).
    *******************************************************************************/
   public enum EmailMode
   {
      OFF, IMMEDIATE, DAILY_DIGEST
   }



   /*******************************************************************************
    ** An enum possible-value source: ids are the constant names, labels are
    ** Title Case ("IN_APP" is "In App").
    *******************************************************************************/
   public static QPossibleValueSource enumSource(String name, Enum<?>[] values)
   {
      List<QPossibleValue<?>> possibleValues = new ArrayList<>();
      for(Enum<?> value : values)
      {
         possibleValues.add(new QPossibleValue<>(value.name(), label(value.name())));
      }
      return (new QPossibleValueSource()
         .withName(name)
         .withType(QPossibleValueSourceType.ENUM)
         .withEnumValues(possibleValues));
   }



   /*******************************************************************************
    ** "IN_APP" to "In App" (and "IDP_GROUP" to "IdP Group").
    *******************************************************************************/
   public static String label(String constant)
   {
      if(constant == null)
      {
         return (null);
      }
      StringBuilder label = new StringBuilder();
      for(String word : constant.split("_"))
      {
         if(word.isEmpty())
         {
            continue;
         }
         if(!label.isEmpty())
         {
            label.append(' ');
         }
         switch(word)
         {
            case "IDP" -> label.append("IdP");
            case "ESB" -> label.append("ESB");
            default -> label.append(word.charAt(0)).append(word.substring(1).toLowerCase(Locale.ROOT));
         }
      }
      return (label.toString());
   }
}
