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

package com.kingsrook.qbits.alerts.metadata;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.alerts.AlertsQBitConfig;
import com.kingsrook.qbits.alerts.AlertsSecurity;
import com.kingsrook.qbits.alerts.customizers.AlertsSubscriptionCustomizer;
import com.kingsrook.qbits.alerts.customizers.AlertsWriteGuardCustomizer;
import com.kingsrook.qbits.alerts.model.AlertsAlert;
import com.kingsrook.qbits.alerts.model.AlertsChannel;
import com.kingsrook.qbits.alerts.model.AlertsEvent;
import com.kingsrook.qbits.alerts.model.AlertsGroup;
import com.kingsrook.qbits.alerts.model.AlertsHeartbeat;
import com.kingsrook.qbits.alerts.model.AlertsInboxItem;
import com.kingsrook.qbits.alerts.model.AlertsMonitor;
import com.kingsrook.qbits.alerts.model.AlertsMute;
import com.kingsrook.qbits.alerts.model.AlertsNotification;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues;
import com.kingsrook.qbits.alerts.model.AlertsRecordRef;
import com.kingsrook.qbits.alerts.model.AlertsSubscription;
import com.kingsrook.qbits.alerts.model.AlertsTeam;
import com.kingsrook.qbits.alerts.model.AlertsTeamMember;
import com.kingsrook.qbits.alerts.model.AlertsType;
import com.kingsrook.qbits.alerts.model.AlertsUserPreference;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizers;
import com.kingsrook.qqq.backend.core.actions.dashboard.widgets.ChildRecordListRenderer;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.instances.QInstanceEnricher;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.data.QRecordEntity;
import com.kingsrook.qqq.backend.core.model.metadata.audits.AuditLevel;
import com.kingsrook.qqq.backend.core.model.metadata.audits.QAuditRules;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaDataInterface;
import com.kingsrook.qqq.backend.core.model.metadata.fields.AdornmentType;
import com.kingsrook.qqq.backend.core.model.metadata.fields.FieldAdornment;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.joins.JoinOn;
import com.kingsrook.qqq.backend.core.model.metadata.joins.JoinType;
import com.kingsrook.qqq.backend.core.model.metadata.joins.QJoinMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.model.metadata.security.RecordSecurityLock;
import com.kingsrook.qqq.backend.core.model.metadata.tables.Capability;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QFieldSection;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.Tier;
import com.kingsrook.qqq.backend.core.model.metadata.tables.UniqueKey;


/*******************************************************************************
 ** Builds the Alerts tables, joins, possible-value sources and record-view
 ** child lists. Every table is an ordinary QQQ table (query, view, export,
 ** audit, API) secured by QQQ mechanisms rather than by hiding it:
 ** - Permissions: QQQ's per-table read / insert / edit / delete permissions
 **   (AlertsQBitConfig lists them per role).
 ** - Record security locks (AlertsSecurity): an alert, its timeline, records
 **   and inbox items are visible only when the session holds the type's
 **   visibility permission (if any) and can read its subject table (if any).
 **   Subscriptions, settings and inbox items are the user's own (admins hold
 **   the all-access key).
 ** - Tables the qbit writes (alerts, timeline, records, notifications, inbox
 **   items, heartbeats) have no insert or delete capability; people change
 **   alerts through record actions, never by editing rows.
 *******************************************************************************/
public final class AlertsTableMetaDataHelper
{
   public static final String JOIN_ALERT_EVENT        = "alerts_alert_join_alerts_event";
   public static final String JOIN_ALERT_RECORD_REF   = "alerts_alert_join_alerts_record_ref";
   public static final String JOIN_ALERT_NOTIFICATION = "alerts_alert_join_alerts_notification";
   public static final String JOIN_ALERT_INBOX_ITEM   = "alerts_alert_join_alerts_inbox_item";
   public static final String JOIN_TYPE_ALERT         = "alerts_type_join_alerts_alert";
   public static final String JOIN_TYPE_MONITOR       = "alerts_type_join_alerts_monitor";
   public static final String JOIN_GROUP_TYPE         = "alerts_group_join_alerts_type";
   public static final String JOIN_MONITOR_ALERT      = "alerts_monitor_join_alerts_alert";
   public static final String JOIN_TEAM_MEMBER        = "alerts_team_join_alerts_team_member";
   public static final String JOIN_CHANNEL_NOTIFICATION = "alerts_channel_join_alerts_notification";



   /*******************************************************************************
    ** Builders only.
    *******************************************************************************/
   private AlertsTableMetaDataHelper()
   {
   }



   /*******************************************************************************
    ** alerts_alert
    *******************************************************************************/
   public static QTableMetaData buildAlertTable(AlertsQBitConfig config) throws QException
   {
      QTableMetaData table = text(base(config, AlertsAlert.TABLE_NAME, "Alerts", "notifications_active", AlertsAlert.class), "details", "links", "attributes")
         .withRecordLabelFormat("%s")
         .withRecordLabelFields("title")
         .withRecordSecurityLock(AlertsSecurity.visibilityLock(null))
         .withAuditRules(new QAuditRules().withAuditLevel(AuditLevel.FIELD))
         .withSection(new QFieldSection("identity", new QIcon("badge"), Tier.T1, List.of("id", "title", "severity", "status", "alertTypeId", "groupId")))
         .withSection(new QFieldSection("problem", "Problem", new QIcon("report"), Tier.T2, List.of("summary", "details", "itemCount", "recordRefCount", "subjectTableName", "subjectRecordId", "sourceName")))
         .withSection(new QFieldSection("handling", "Handling", new QIcon("assignment_ind"), Tier.T2, List.of("assignedUserId", "assignedTeamId", "acknowledgedByUserId", "acknowledgedAt",
            "snoozedUntil", "muted", "resolvedAt", "resolvedByUserId", "resolution", "resolutionNote")))
         .withSection(widgetSection(JOIN_ALERT_EVENT, "Timeline", "history"))
         .withSection(widgetSection(JOIN_ALERT_RECORD_REF, "Records", "link"))
         .withSection(widgetSection(JOIN_ALERT_NOTIFICATION, "Notifications", "send"))
         .withSection(new QFieldSection("occurrence", "Occurrences", new QIcon("repeat"), Tier.T3, List.of("firstRaisedAt", "lastRaisedAt", "occurrenceCount", "reopenCount", "flapping",
            "lastNotifiedAt", "monitorId", "dedupKey", "alertTypeName", "visibilityPermission", "openDedupKey", "escalationLevel", "links", "attributes", "createDate", "modifyDate")));
      return (readOnly(table));
   }



   /*******************************************************************************
    ** alerts_type
    *******************************************************************************/
   public static QTableMetaData buildTypeTable(AlertsQBitConfig config) throws QException
   {
      QTableMetaData table = text(base(config, AlertsType.TABLE_NAME, "Alert Types", "category", AlertsType.class), "description", "howToFix")
         .withRecordLabelFormat("%s")
         .withRecordLabelFields("label")
         .withUniqueKey(new UniqueKey("name"))
         .withAuditRules(new QAuditRules().withAuditLevel(AuditLevel.FIELD))
         .withSection(new QFieldSection("identity", new QIcon("badge"), Tier.T1, List.of("id", "label", "name", "groupId", "sourceName", "openCount")))
         .withSection(new QFieldSection("about", "About", new QIcon("info"), Tier.T2, List.of("description", "howToFix", "subjectTableName", "visibilityPermission")))
         .withSection(new QFieldSection("tuning", "Tuning", new QIcon("tune"), Tier.T2, List.of("enabled", "notify", "defaultSeverity", "severityOverride", "resolveMode",
            "quietPeriodMinutes", "reminderIntervalMinutes", "externalDetailCeiling")))
         .withSection(widgetSection(JOIN_TYPE_MONITOR, "Monitors", "monitor_heart"))
         .withSection(widgetSection(JOIN_TYPE_ALERT, "Alerts", "notifications_active"))
         .withSection(new QFieldSection("system", new QIcon("calendar_month"), Tier.T3, List.of("installed", "lastRaisedAt", "createDate", "modifyDate")));
      table.withoutCapabilities(Capability.TABLE_INSERT, Capability.TABLE_DELETE);
      return (guard(table));
   }



   /*******************************************************************************
    ** alerts_group
    *******************************************************************************/
   public static QTableMetaData buildGroupTable(AlertsQBitConfig config) throws QException
   {
      QTableMetaData table = text(base(config, AlertsGroup.TABLE_NAME, "Alert Groups", "folder", AlertsGroup.class), "description")
         .withRecordLabelFormat("%s")
         .withRecordLabelFields("label")
         .withUniqueKey(new UniqueKey("name"))
         .withAuditRules(new QAuditRules().withAuditLevel(AuditLevel.FIELD))
         .withSection(new QFieldSection("identity", new QIcon("badge"), Tier.T1, List.of("id", "label", "name", "parentGroupId")))
         .withSection(new QFieldSection("details", new QIcon("description"), Tier.T2, List.of("description", "ownerTeamId", "icon", "sortOrder")))
         .withSection(widgetSection(JOIN_GROUP_TYPE, "Alert Types", "category"))
         .withSection(new QFieldSection("system", new QIcon("calendar_month"), Tier.T3, List.of("createDate", "modifyDate")));
      return (table);
   }



   /*******************************************************************************
    ** alerts_monitor
    *******************************************************************************/
   public static QTableMetaData buildMonitorTable(AlertsQBitConfig config) throws QException
   {
      QTableMetaData table = text(base(config, AlertsMonitor.TABLE_NAME, "Monitors", "monitor_heart", AlertsMonitor.class), "description", "definition")
         .withRecordLabelFormat("%s")
         .withRecordLabelFields("name")
         .withAuditRules(new QAuditRules().withAuditLevel(AuditLevel.FIELD))
         .withSection(new QFieldSection("identity", new QIcon("badge"), Tier.T1, List.of("id", "name", "alertTypeId", "kind", "enabled", "lastStatus")))
         .withSection(new QFieldSection("check", "Check", new QIcon("rule"), Tier.T2, List.of("description", "definition", "perRecord", "severity", "scheduleSeconds", "forMinutes")))
         .withSection(new QFieldSection("lastRun", "Last Run", new QIcon("schedule"), Tier.T2, List.of("lastRunAt", "lastValue", "lastError", "lastElapsedMs", "consecutiveFailures", "nextRunAt")))
         .withSection(widgetSection(JOIN_MONITOR_ALERT, "Alerts", "notifications_active"))
         .withSection(new QFieldSection("system", new QIcon("calendar_month"), Tier.T3, List.of("seeded", "sourceName", "conditionSince", "claimedBy", "claimedUntil", "createDate", "modifyDate")));
      table.getField("definition").withFieldAdornment(new FieldAdornment(AdornmentType.CODE_EDITOR).withValue(AdornmentType.CodeEditorValues.languageMode("json")));
      table.getField("enabled").setDefaultValue(true);
      table.getField("perRecord").setDefaultValue(false);
      table.getField("forMinutes").setDefaultValue(0);
      table.getField("scheduleSeconds").setDefaultValue(300);
      return (guard(table));
   }



   /*******************************************************************************
    ** alerts_record_ref
    *******************************************************************************/
   public static QTableMetaData buildRecordRefTable(AlertsQBitConfig config) throws QException
   {
      QTableMetaData table = base(config, AlertsRecordRef.TABLE_NAME, "Alert Records", "link", AlertsRecordRef.class)
         .withRecordLabelFormat("%s %s")
         .withRecordLabelFields("tableName", "recordId")
         .withRecordSecurityLock(AlertsSecurity.visibilityLock(JOIN_ALERT_RECORD_REF))
         .withSection(new QFieldSection("identity", new QIcon("badge"), Tier.T1, List.of("id", "alertId", "tableName", "recordId", "label", "active")))
         .withSection(new QFieldSection("system", new QIcon("calendar_month"), Tier.T3, List.of("createDate", "modifyDate")));
      return (readOnly(table));
   }



   /*******************************************************************************
    ** alerts_event
    *******************************************************************************/
   public static QTableMetaData buildEventTable(AlertsQBitConfig config) throws QException
   {
      QTableMetaData table = base(config, AlertsEvent.TABLE_NAME, "Alert Timeline", "history", AlertsEvent.class)
         .withRecordLabelFormat("%s %s")
         .withRecordLabelFields("eventType", "at")
         .withRecordSecurityLock(AlertsSecurity.visibilityLock(JOIN_ALERT_EVENT))
         .withSection(new QFieldSection("identity", new QIcon("badge"), Tier.T1, List.of("id", "alertId", "eventType", "at", "userId")))
         .withSection(new QFieldSection("details", new QIcon("description"), Tier.T2, List.of("fromValue", "toValue", "note")))
         .withSection(new QFieldSection("system", new QIcon("calendar_month"), Tier.T3, List.of("createDate", "modifyDate")));
      return (readOnly(table));
   }



   /*******************************************************************************
    ** alerts_subscription
    *******************************************************************************/
   public static QTableMetaData buildSubscriptionTable(AlertsQBitConfig config) throws QException
   {
      QTableMetaData table = base(config, AlertsSubscription.TABLE_NAME, "Subscriptions", "notifications", AlertsSubscription.class)
         .withRecordLabelFormat("%s %s %s")
         .withRecordLabelFields("subscriberType", "scopeType", "scopeValue")
         .withRecordSecurityLock(AlertsSecurity.ownerLock("userId"))
         .withAuditRules(new QAuditRules().withAuditLevel(AuditLevel.FIELD))
         .withSection(new QFieldSection("identity", new QIcon("badge"), Tier.T1, List.of("id", "subscriberType", "userId", "teamId", "channelId", "enabled")))
         .withSection(new QFieldSection("scope", "What", new QIcon("filter_alt"), Tier.T2, List.of("scopeType", "scopeValue", "scopeRecordId", "minSeverity", "events")))
         .withSection(new QFieldSection("delivery", "How", new QIcon("send"), Tier.T2, List.of("deliveryMode", "channelOverrideIds")))
         .withSection(new QFieldSection("system", new QIcon("calendar_month"), Tier.T3, List.of("createdByUserId", "createDate", "modifyDate")));
      table.getField("subscriberType").setDefaultValue(AlertsPossibleValues.SubscriberType.USER.name());
      table.getField("scopeType").setDefaultValue(AlertsPossibleValues.ScopeType.ALL.name());
      table.getField("minSeverity").setDefaultValue("WARNING");
      table.getField("deliveryMode").setDefaultValue(AlertsPossibleValues.DeliveryMode.IMMEDIATE.name());
      table.getField("events").setDefaultValue("RAISED,ESCALATED,REMINDER,RESOLVED");
      table.getField("enabled").setDefaultValue(true);
      table.getField("userId").setIsEditable(false);
      return (customize(table, AlertsSubscriptionCustomizer.class));
   }



   /*******************************************************************************
    ** alerts_user_preference
    *******************************************************************************/
   public static QTableMetaData buildUserPreferenceTable(AlertsQBitConfig config) throws QException
   {
      QTableMetaData table = base(config, AlertsUserPreference.TABLE_NAME, "My Alert Settings", "manage_accounts", AlertsUserPreference.class)
         .withRecordLabelFormat("%s")
         .withRecordLabelFields("userLabel")
         .withUniqueKey(new UniqueKey("userId"))
         .withRecordSecurityLock(AlertsSecurity.ownerLock("userId"))
         .withAuditRules(new QAuditRules().withAuditLevel(AuditLevel.FIELD))
         .withSection(new QFieldSection("identity", new QIcon("badge"), Tier.T1, List.of("id", "userLabel", "emailAddress", "userId")))
         .withSection(new QFieldSection("delivery", "Delivery", new QIcon("send"), Tier.T2, List.of("inAppEnabled", "emailEnabled", "pausedUntil", "defaultChannelIds")))
         .withSection(new QFieldSection("quiet", "Quiet Hours and Digests", new QIcon("bedtime"), Tier.T2, List.of("timeZone", "quietHoursStart", "quietHoursEnd", "quietDays",
            "quietBypassSeverity", "digestHour")))
         .withSection(new QFieldSection("system", new QIcon("calendar_month"), Tier.T3, List.of("createDate", "modifyDate")));
      table.getField("emailEnabled").setDefaultValue(true);
      table.getField("inAppEnabled").setDefaultValue(true);
      table.getField("quietBypassSeverity").setDefaultValue("CRITICAL");
      table.getField("digestHour").setDefaultValue(8);
      return (customize(table, AlertsSubscriptionCustomizer.class));
   }



   /*******************************************************************************
    ** alerts_team
    *******************************************************************************/
   public static QTableMetaData buildTeamTable(AlertsQBitConfig config) throws QException
   {
      QTableMetaData table = text(base(config, AlertsTeam.TABLE_NAME, "Teams", "groups", AlertsTeam.class), "description")
         .withRecordLabelFormat("%s")
         .withRecordLabelFields("label")
         .withUniqueKey(new UniqueKey("name"))
         .withAuditRules(new QAuditRules().withAuditLevel(AuditLevel.FIELD))
         .withSection(new QFieldSection("identity", new QIcon("badge"), Tier.T1, List.of("id", "label", "name", "memberSource", "memberSourceValue")))
         .withSection(new QFieldSection("details", new QIcon("description"), Tier.T2, List.of("description")))
         .withSection(widgetSection(JOIN_TEAM_MEMBER, "Members", "person"))
         .withSection(new QFieldSection("system", new QIcon("calendar_month"), Tier.T3, List.of("createDate", "modifyDate")));
      table.getField("memberSource").setDefaultValue(AlertsPossibleValues.MemberSource.MANUAL.name());
      return (table);
   }



   /*******************************************************************************
    ** alerts_team_member
    *******************************************************************************/
   public static QTableMetaData buildTeamMemberTable(AlertsQBitConfig config) throws QException
   {
      QTableMetaData table = base(config, AlertsTeamMember.TABLE_NAME, "Team Members", "person", AlertsTeamMember.class)
         .withRecordLabelFormat("%s %s")
         .withRecordLabelFields("teamId", "userId")
         .withUniqueKey(new UniqueKey("teamId", "userId"))
         .withAuditRules(new QAuditRules().withAuditLevel(AuditLevel.FIELD))
         .withSection(new QFieldSection("identity", new QIcon("badge"), Tier.T1, List.of("id", "teamId", "userId", "role")))
         .withSection(new QFieldSection("system", new QIcon("calendar_month"), Tier.T3, List.of("createDate", "modifyDate")));
      table.getField("role").setDefaultValue(AlertsPossibleValues.TeamRole.MEMBER.name());
      return (table);
   }



   /*******************************************************************************
    ** alerts_channel
    *******************************************************************************/
   public static QTableMetaData buildChannelTable(AlertsQBitConfig config) throws QException
   {
      QTableMetaData table = text(base(config, AlertsChannel.TABLE_NAME, "Channels", "campaign", AlertsChannel.class), "settings")
         .withRecordLabelFormat("%s")
         .withRecordLabelFields("label")
         .withUniqueKey(new UniqueKey("name"))
         .withAuditRules(new QAuditRules().withAuditLevel(AuditLevel.FIELD))
         .withSection(new QFieldSection("identity", new QIcon("badge"), Tier.T1, List.of("id", "label", "name", "channelType", "enabled", "healthStatus")))
         .withSection(new QFieldSection("settings", "Settings", new QIcon("tune"), Tier.T2, List.of("settings", "secretRef", "detailLevel", "minSeverity", "rateLimitPerHour")))
         .withSection(new QFieldSection("health", "Health", new QIcon("monitor_heart"), Tier.T2, List.of("lastSuccessAt", "lastFailureAt", "consecutiveFailures", "lastError")))
         .withSection(widgetSection(JOIN_CHANNEL_NOTIFICATION, "Notifications", "send"))
         .withSection(new QFieldSection("system", new QIcon("calendar_month"), Tier.T3, List.of("createDate", "modifyDate")));
      table.getField("settings").withFieldAdornment(new FieldAdornment(AdornmentType.CODE_EDITOR).withValue(AdornmentType.CodeEditorValues.languageMode("json")));
      table.getField("enabled").setDefaultValue(true);
      table.getField("detailLevel").setDefaultValue("MINIMAL");
      table.getField("minSeverity").setDefaultValue("WARNING");
      table.getField("rateLimitPerHour").setDefaultValue(60);
      table.getField("healthStatus").setDefaultValue(AlertsPossibleValues.HealthStatus.HEALTHY.name());
      return (guard(table));
   }



   /*******************************************************************************
    ** alerts_mute
    *******************************************************************************/
   public static QTableMetaData buildMuteTable(AlertsQBitConfig config) throws QException
   {
      QTableMetaData table = base(config, AlertsMute.TABLE_NAME, "Mutes and Maintenance", "notifications_off", AlertsMute.class)
         .withRecordLabelFormat("%s")
         .withRecordLabelFields("name")
         .withAuditRules(new QAuditRules().withAuditLevel(AuditLevel.FIELD))
         .withSection(new QFieldSection("identity", new QIcon("badge"), Tier.T1, List.of("id", "name", "enabled", "effect")))
         .withSection(new QFieldSection("scope", "What and When", new QIcon("filter_alt"), Tier.T2, List.of("scopeType", "scopeValue", "startsAt", "endsAt", "reason")))
         .withSection(new QFieldSection("system", new QIcon("calendar_month"), Tier.T3, List.of("createdByUserId", "createDate", "modifyDate")));
      table.getField("effect").setDefaultValue(AlertsPossibleValues.MuteEffect.SILENCE_NOTIFICATIONS.name());
      table.getField("enabled").setDefaultValue(true);
      table.getField("scopeType").setDefaultValue(AlertsPossibleValues.ScopeType.ALL.name());
      return (customize(table, AlertsSubscriptionCustomizer.class));
   }



   /*******************************************************************************
    ** alerts_notification
    *******************************************************************************/
   public static QTableMetaData buildNotificationTable(AlertsQBitConfig config) throws QException
   {
      QTableMetaData table = base(config, AlertsNotification.TABLE_NAME, "Notification Log", "send", AlertsNotification.class)
         .withRecordLabelFormat("%s %s")
         .withRecordLabelFields("kind", "recipientLabel")
         .withSection(new QFieldSection("identity", new QIcon("badge"), Tier.T1, List.of("id", "alertId", "kind", "status", "channelId", "recipientLabel")))
         .withSection(new QFieldSection("delivery", "Delivery", new QIcon("send"), Tier.T2, List.of("suppressedReason", "attempts", "nextAttemptAt", "sentAt", "lastError", "providerMessageId")))
         .withSection(new QFieldSection("system", new QIcon("calendar_month"), Tier.T3, List.of("recipientUserId", "subscriptionId", "alertEventId", "claimedUntil", "createDate", "modifyDate")));
      return (readOnly(table));
   }



   /*******************************************************************************
    ** alerts_inbox_item
    *******************************************************************************/
   public static QTableMetaData buildInboxItemTable(AlertsQBitConfig config) throws QException
   {
      QTableMetaData table = base(config, AlertsInboxItem.TABLE_NAME, "My Alerts Inbox", "inbox", AlertsInboxItem.class)
         .withRecordLabelFormat("%s")
         .withRecordLabelFields("alertId")
         .withRecordSecurityLock(AlertsSecurity.ownerLock("userId"))
         .withRecordSecurityLock(AlertsSecurity.visibilityLock(JOIN_ALERT_INBOX_ITEM))
         .withSection(new QFieldSection("identity", new QIcon("badge"), Tier.T1, List.of("id", "alertId", "deliveredAt", "readAt", "archivedAt")))
         .withSection(new QFieldSection("system", new QIcon("calendar_month"), Tier.T3, List.of("userId", "notificationId", "createDate", "modifyDate")));
      table.withoutCapabilities(Capability.TABLE_INSERT);
      return (guard(table));
   }



   /*******************************************************************************
    ** alerts_heartbeat
    *******************************************************************************/
   public static QTableMetaData buildHeartbeatTable(AlertsQBitConfig config) throws QException
   {
      QTableMetaData table = base(config, AlertsHeartbeat.TABLE_NAME, "Heartbeats", "favorite", AlertsHeartbeat.class)
         .withRecordLabelFormat("%s")
         .withRecordLabelFields("heartbeatKey")
         .withUniqueKey(new UniqueKey("heartbeatKey"))
         .withSection(new QFieldSection("identity", new QIcon("badge"), Tier.T1, List.of("id", "heartbeatKey", "lastBeatAt", "beatCount")))
         .withSection(new QFieldSection("system", new QIcon("calendar_month"), Tier.T3, List.of("createDate", "modifyDate")));
      return (readOnly(table));
   }



   /*******************************************************************************
    ** All the tables.
    *******************************************************************************/
   public static List<QTableMetaData> buildAllTables(AlertsQBitConfig config) throws QException
   {
      return (List.of(buildAlertTable(config), buildMonitorTable(config), buildTypeTable(config), buildGroupTable(config), buildSubscriptionTable(config),
         buildUserPreferenceTable(config), buildTeamTable(config), buildTeamMemberTable(config), buildChannelTable(config), buildMuteTable(config),
         buildNotificationTable(config), buildEventTable(config), buildRecordRefTable(config), buildInboxItemTable(config), buildHeartbeatTable(config)));
   }



   /*******************************************************************************
    ** Table-backed possible-value sources (for the id fields), plus the enums.
    *******************************************************************************/
   public static List<QPossibleValueSource> buildPossibleValueSources()
   {
      List<QPossibleValueSource> sources = new ArrayList<>(AlertsPossibleValues.buildEnumSources());
      for(String table : List.of(AlertsAlert.TABLE_NAME, AlertsType.TABLE_NAME, AlertsGroup.TABLE_NAME, AlertsMonitor.TABLE_NAME, AlertsTeam.TABLE_NAME,
         AlertsChannel.TABLE_NAME, AlertsSubscription.TABLE_NAME))
      {
         sources.add(QPossibleValueSource.newForTable(table));
      }
      return (sources);
   }



   /*******************************************************************************
    ** The one-to-many joins from each parent to its children.
    *******************************************************************************/
   public static List<QJoinMetaData> buildJoins()
   {
      List<QJoinMetaData> joins = new ArrayList<>();
      joins.add(oneToMany(JOIN_ALERT_EVENT, AlertsAlert.TABLE_NAME, AlertsEvent.TABLE_NAME, "alertId"));
      joins.add(oneToMany(JOIN_ALERT_RECORD_REF, AlertsAlert.TABLE_NAME, AlertsRecordRef.TABLE_NAME, "alertId"));
      joins.add(oneToMany(JOIN_ALERT_NOTIFICATION, AlertsAlert.TABLE_NAME, AlertsNotification.TABLE_NAME, "alertId"));
      joins.add(oneToMany(JOIN_ALERT_INBOX_ITEM, AlertsAlert.TABLE_NAME, AlertsInboxItem.TABLE_NAME, "alertId"));
      joins.add(oneToMany(JOIN_TYPE_ALERT, AlertsType.TABLE_NAME, AlertsAlert.TABLE_NAME, "alertTypeId"));
      joins.add(oneToMany(JOIN_TYPE_MONITOR, AlertsType.TABLE_NAME, AlertsMonitor.TABLE_NAME, "alertTypeId"));
      joins.add(oneToMany(JOIN_GROUP_TYPE, AlertsGroup.TABLE_NAME, AlertsType.TABLE_NAME, "groupId"));
      joins.add(oneToMany(JOIN_MONITOR_ALERT, AlertsMonitor.TABLE_NAME, AlertsAlert.TABLE_NAME, "monitorId"));
      joins.add(oneToMany(JOIN_TEAM_MEMBER, AlertsTeam.TABLE_NAME, AlertsTeamMember.TABLE_NAME, "teamId"));
      joins.add(oneToMany(JOIN_CHANNEL_NOTIFICATION, AlertsChannel.TABLE_NAME, AlertsNotification.TABLE_NAME, "channelId"));
      return (joins);
   }



   /*******************************************************************************
    ** The record-view child lists (newest first, a few columns each, "View
    ** all" beyond the cap).
    *******************************************************************************/
   public static List<QWidgetMetaDataInterface> buildChildListWidgets()
   {
      Map<String, QJoinMetaData> joins = new LinkedHashMap<>();
      for(QJoinMetaData join : buildJoins())
      {
         joins.put(join.getName(), join);
      }

      List<QWidgetMetaDataInterface> widgets = new ArrayList<>();
      widgets.add(childList(joins.get(JOIN_ALERT_EVENT), "Timeline", 50, new QFilterOrderBy("at", false), List.of("at", "eventType", "userId", "toValue", "note")));
      widgets.add(childList(joins.get(JOIN_ALERT_RECORD_REF), "Records", 25, new QFilterOrderBy("id", true), List.of("tableName", "recordId", "label", "active")));
      widgets.add(childList(joins.get(JOIN_ALERT_NOTIFICATION), "Notifications", 25, new QFilterOrderBy("id", false), List.of("kind", "status", "recipientLabel", "sentAt")));
      widgets.add(childList(joins.get(JOIN_TYPE_ALERT), "Alerts", 10, new QFilterOrderBy("lastRaisedAt", false), List.of("title", "severity", "status", "lastRaisedAt")));
      widgets.add(childList(joins.get(JOIN_TYPE_MONITOR), "Monitors", 10, new QFilterOrderBy("id", true), List.of("name", "kind", "enabled", "lastStatus")));
      widgets.add(childList(joins.get(JOIN_GROUP_TYPE), "Alert Types", 50, new QFilterOrderBy("label", true), List.of("label", "defaultSeverity", "enabled", "openCount")));
      widgets.add(childList(joins.get(JOIN_MONITOR_ALERT), "Alerts", 10, new QFilterOrderBy("lastRaisedAt", false), List.of("title", "severity", "status", "lastRaisedAt")));
      widgets.add(ChildRecordListRenderer.widgetMetaDataBuilder(joins.get(JOIN_TEAM_MEMBER)).withLabel("Members").withCanAddChildRecord(true).withMaxRows(100)
         .withOnlyIncludeFieldNames(List.of("userId", "role")).getWidgetMetaData());
      widgets.add(childList(joins.get(JOIN_CHANNEL_NOTIFICATION), "Notifications", 25, new QFilterOrderBy("id", false), List.of("kind", "status", "recipientLabel", "sentAt")));
      return (widgets);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static QWidgetMetaDataInterface childList(QJoinMetaData join, String label, int maxRows, QFilterOrderBy orderBy, List<String> fields)
   {
      return (ChildRecordListRenderer.widgetMetaDataBuilder(join)
         .withLabel(label)
         .withMaxRows(maxRows)
         .withOrderBys(List.of(orderBy))
         .withOnlyIncludeFieldNames(fields)
         .getWidgetMetaData());
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static QJoinMetaData oneToMany(String name, String left, String right, String rightField)
   {
      return (new QJoinMetaData()
         .withName(name)
         .withLeftTable(left)
         .withRightTable(right)
         .withType(JoinType.ONE_TO_MANY)
         .withJoinOn(new JoinOn("id", rightField)));
   }



   /*******************************************************************************
    ** A table with QQQ's per-table permissions, entity fields, snake_case
    ** backend column names, and createDate / modifyDate managed by QQQ.
    *******************************************************************************/
   private static QTableMetaData base(AlertsQBitConfig config, String name, String label, String icon, Class<? extends QRecordEntity> entity) throws QException
   {
      QTableMetaData table = new QTableMetaData()
         .withName(name)
         .withLabel(label)
         .withIcon(new QIcon(icon))
         .withBackendName(config.getBackendName())
         .withPrimaryKeyField("id")
         .withPermissionRules(new QPermissionRules().withLevel(PermissionLevel.READ_INSERT_EDIT_DELETE_PERMISSIONS))
         .withFieldsFromEntity(entity);
      QInstanceEnricher.setInferredFieldBackendNames(table);
      return (table);
   }



   /*******************************************************************************
    ** Only the qbit writes this table.
    *******************************************************************************/
   private static QTableMetaData readOnly(QTableMetaData table)
   {
      table.withoutCapabilities(Capability.TABLE_INSERT, Capability.TABLE_UPDATE, Capability.TABLE_DELETE);
      return (guard(table));
   }



   /*******************************************************************************
    ** Reject writes the table's capabilities don't allow, in the core actions,
    ** unless the qbit itself (system user) makes them.
    *******************************************************************************/
   private static QTableMetaData guard(QTableMetaData table)
   {
      QCodeReference reference = new QCodeReference(AlertsWriteGuardCustomizer.class);
      for(TableCustomizers role : List.of(TableCustomizers.PRE_INSERT_RECORD, TableCustomizers.PRE_UPDATE_RECORD, TableCustomizers.PRE_DELETE_RECORD))
      {
         table.withCustomizer(role, reference);
      }
      return (table);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static QTableMetaData customize(QTableMetaData table, Class<?> customizer)
   {
      QCodeReference reference = new QCodeReference(customizer);
      for(TableCustomizers role : List.of(TableCustomizers.PRE_INSERT_RECORD, TableCustomizers.PRE_UPDATE_RECORD))
      {
         table.withCustomizer(role, reference);
      }
      return (table);
   }



   /*******************************************************************************
    ** A record-view section showing a child-list widget.
    *******************************************************************************/
   private static QFieldSection widgetSection(String joinName, String label, String icon)
   {
      return (new QFieldSection(joinName, label, new QIcon(icon), Tier.T2, null).withWidgetName(joinName));
   }



   /*******************************************************************************
    ** Make String fields TEXT (unbounded).
    *******************************************************************************/
   private static QTableMetaData text(QTableMetaData table, String... names)
   {
      for(String name : names)
      {
         QFieldMetaData field = table.getField(name);
         field.setType(QFieldType.TEXT);
      }
      return (table);
   }
}
