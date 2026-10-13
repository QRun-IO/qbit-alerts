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


import java.util.List;
import com.kingsrook.qbits.alerts.AlertsQBitConfig;
import com.kingsrook.qbits.alerts.model.AlertsAlert;
import com.kingsrook.qbits.alerts.model.AlertsChannel;
import com.kingsrook.qbits.alerts.model.AlertsDelivery;
import com.kingsrook.qbits.alerts.model.AlertsGroup;
import com.kingsrook.qbits.alerts.model.AlertsMonitor;
import com.kingsrook.qbits.alerts.model.AlertsNotification;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues;
import com.kingsrook.qbits.alerts.model.AlertsTeam;
import com.kingsrook.qbits.alerts.model.AlertsType;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;


/*******************************************************************************
 ** The Alerts record and table actions (design section 6.6). Each is a
 ** standard streamed ETL process on its table (optional input, Review summary
 ** lines, Result summary lines), never a nav entry.
 *******************************************************************************/
public enum AlertsAction
{
   ACKNOWLEDGE("alertsAcknowledge", "Acknowledge", "done", AlertsAlert.TABLE_NAME, AlertsQBitConfig.PERMISSION_MANAGE, null),
   SNOOZE("alertsSnooze", "Snooze", "snooze", AlertsAlert.TABLE_NAME, AlertsQBitConfig.PERMISSION_MANAGE, null),
   RESOLVE("alertsResolve", "Resolve", "check_circle", AlertsAlert.TABLE_NAME, AlertsQBitConfig.PERMISSION_MANAGE, null),
   REOPEN("alertsReopen", "Reopen", "restart_alt", AlertsAlert.TABLE_NAME, AlertsQBitConfig.PERMISSION_MANAGE, 1),
   ASSIGN("alertsAssign", "Assign", "assignment_ind", AlertsAlert.TABLE_NAME, AlertsQBitConfig.PERMISSION_MANAGE, null),
   COMMENT("alertsAddComment", "Add comment", "comment", AlertsAlert.TABLE_NAME, AlertsQBitConfig.PERMISSION_VIEW, 1),
   MUTE_ALERT("alertsMuteAlert", "Mute", "notifications_off", AlertsAlert.TABLE_NAME, AlertsQBitConfig.PERMISSION_MANAGE, 1),
   SUBSCRIBE_TYPE("alertsSubscribeToType", "Subscribe me", "notifications", AlertsType.TABLE_NAME, AlertsQBitConfig.PERMISSION_VIEW, 1),
   MUTE_TYPE("alertsMuteType", "Mute", "notifications_off", AlertsType.TABLE_NAME, AlertsQBitConfig.PERMISSION_MANAGE, 1),
   CREATE_MONITOR("alertsCreateMonitorFromType", "Create monitor", "add_alert", AlertsType.TABLE_NAME, AlertsQBitConfig.PERMISSION_ADMIN, 1),
   SUBSCRIBE_GROUP("alertsSubscribeToGroup", "Subscribe me", "notifications", AlertsGroup.TABLE_NAME, AlertsQBitConfig.PERMISSION_VIEW, 1),
   MUTE_GROUP("alertsMuteGroup", "Mute", "notifications_off", AlertsGroup.TABLE_NAME, AlertsQBitConfig.PERMISSION_MANAGE, 1),
   SUBSCRIBE_MONITOR("alertsSubscribeToMonitor", "Subscribe me", "notifications", AlertsMonitor.TABLE_NAME, AlertsQBitConfig.PERMISSION_VIEW, 1),
   MUTE_MONITOR("alertsMuteMonitor", "Mute", "notifications_off", AlertsMonitor.TABLE_NAME, AlertsQBitConfig.PERMISSION_MANAGE, 1),
   RUN_CHECK("alertsRunCheckNow", "Run check now", "play_circle", AlertsMonitor.TABLE_NAME, AlertsQBitConfig.PERMISSION_ADMIN, 1),
   ENABLE_MONITOR("alertsEnableMonitor", "Enable monitor", "toggle_on", AlertsMonitor.TABLE_NAME, AlertsQBitConfig.PERMISSION_ADMIN, null),
   DISABLE_MONITOR("alertsDisableMonitor", "Disable monitor", "toggle_off", AlertsMonitor.TABLE_NAME, AlertsQBitConfig.PERMISSION_ADMIN, null),
   SEND_TEST("alertsSendTestNotification", "Send test notification", "send", AlertsChannel.TABLE_NAME, AlertsQBitConfig.PERMISSION_ADMIN, 1),
   RETRY_DELIVERY("alertsRetryDelivery", "Retry delivery", "replay", AlertsDelivery.TABLE_NAME, AlertsQBitConfig.PERMISSION_ADMIN, null),
   PREVIEW_AUDIENCE("alertsPreviewAudience", "Preview audience", "groups", AlertsNotification.TABLE_NAME, AlertsQBitConfig.PERMISSION_NOTIFICATION_ADMIN, 1),
   SEND_NOTIFICATION("alertsSendNotification", "Send now", "send", AlertsNotification.TABLE_NAME, AlertsQBitConfig.PERMISSION_NOTIFICATION_ADMIN, null),
   SCHEDULE_NOTIFICATION("alertsScheduleNotification", "Schedule", "schedule_send", AlertsNotification.TABLE_NAME, AlertsQBitConfig.PERMISSION_NOTIFICATION_ADMIN, null),
   RETRACT_NOTIFICATION("alertsRetractNotification", "Retract", "unpublished", AlertsNotification.TABLE_NAME, AlertsQBitConfig.PERMISSION_NOTIFICATION_ADMIN, null);

   public static final String FIELD_NOTE          = "alertsNote";
   public static final String FIELD_SNOOZE_HOURS  = "alertsSnoozeHours";
   public static final String FIELD_ASSIGN_USER   = "alertsAssignUserId";
   public static final String FIELD_ASSIGN_TEAM   = "alertsAssignTeamId";
   public static final String FIELD_MIN_SEVERITY  = "alertsMinSeverity";
   public static final String FIELD_DELIVERY_MODE = "alertsDeliveryMode";
   public static final String FIELD_MUTE_MINUTES  = "alertsMuteMinutes";

   private final String  processName;
   private final String  label;
   private final String  icon;
   private final String  tableName;
   private final String  permissionName;
   private final Integer maxRecords;



   /*******************************************************************************
    **
    *******************************************************************************/
   AlertsAction(String processName, String label, String icon, String tableName, String permissionName, Integer maxRecords)
   {
      this.processName = processName;
      this.label = label;
      this.icon = icon;
      this.tableName = tableName;
      this.permissionName = permissionName;
      this.maxRecords = maxRecords;
   }



   /*******************************************************************************
    ** The action for a process name, or null.
    *******************************************************************************/
   public static AlertsAction forProcess(String processName)
   {
      for(AlertsAction action : values())
      {
         if(action.processName.equals(processName))
         {
            return (action);
         }
      }
      return (null);
   }



   /*******************************************************************************
    ** The input form fields (empty: straight to Review).
    *******************************************************************************/
   public List<QFieldMetaData> inputFields()
   {
      return switch(this)
      {
         case SNOOZE -> List.of(new QFieldMetaData(FIELD_SNOOZE_HOURS, QFieldType.INTEGER).withLabel("Snooze for (hours)").withIsRequired(true).withDefaultValue(4));
         case RESOLVE -> List.of(new QFieldMetaData(FIELD_NOTE, QFieldType.TEXT).withLabel("Resolution note").withIsRequired(true));
         case REOPEN -> List.of(new QFieldMetaData(FIELD_NOTE, QFieldType.TEXT).withLabel("Why reopen"));
         case COMMENT -> List.of(new QFieldMetaData(FIELD_NOTE, QFieldType.TEXT).withLabel("Comment").withIsRequired(true));
         case ASSIGN -> List.of(
            new QFieldMetaData(FIELD_ASSIGN_USER, QFieldType.STRING).withLabel("Assign to user (user id)"),
            new QFieldMetaData(FIELD_ASSIGN_TEAM, QFieldType.INTEGER).withLabel("Or a team").withPossibleValueSourceName(AlertsTeam.TABLE_NAME));
         case SUBSCRIBE_TYPE, SUBSCRIBE_GROUP, SUBSCRIBE_MONITOR -> List.of(
            new QFieldMetaData(FIELD_MIN_SEVERITY, QFieldType.STRING).withLabel("Minimum severity").withIsRequired(true).withDefaultValue("WARNING")
               .withPossibleValueSourceName(AlertsPossibleValues.SEVERITY),
            new QFieldMetaData(FIELD_DELIVERY_MODE, QFieldType.STRING).withLabel("Delivery").withIsRequired(true).withDefaultValue("IMMEDIATE")
               .withPossibleValueSourceName(AlertsPossibleValues.DELIVERY_MODE));
         case MUTE_ALERT, MUTE_TYPE, MUTE_GROUP, MUTE_MONITOR -> List.of(
            new QFieldMetaData(FIELD_MUTE_MINUTES, QFieldType.INTEGER).withLabel("Mute for (minutes)").withIsRequired(true).withDefaultValue(60),
            new QFieldMetaData(FIELD_NOTE, QFieldType.TEXT).withLabel("Reason").withIsRequired(true));
         default -> List.of();
      };
   }



   /*******************************************************************************
    ** Getter for processName
    *******************************************************************************/
   public String getProcessName()
   {
      return (this.processName);
   }



   /*******************************************************************************
    ** Getter for label
    *******************************************************************************/
   public String getLabel()
   {
      return (this.label);
   }



   /*******************************************************************************
    ** Getter for icon
    *******************************************************************************/
   public String getIcon()
   {
      return (this.icon);
   }



   /*******************************************************************************
    ** Getter for tableName
    *******************************************************************************/
   public String getTableName()
   {
      return (this.tableName);
   }



   /*******************************************************************************
    ** Getter for permissionName
    *******************************************************************************/
   public String getPermissionName()
   {
      return (this.permissionName);
   }



   /*******************************************************************************
    ** Getter for maxRecords (null: bulk)
    *******************************************************************************/
   public Integer getMaxRecords()
   {
      return (this.maxRecords);
   }
}
