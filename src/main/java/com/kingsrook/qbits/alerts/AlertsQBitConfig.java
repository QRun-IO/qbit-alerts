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


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.alerts.model.AlertsAlert;
import com.kingsrook.qbits.alerts.model.AlertsChannel;
import com.kingsrook.qbits.alerts.model.AlertsEvent;
import com.kingsrook.qbits.alerts.model.AlertsGroup;
import com.kingsrook.qbits.alerts.model.AlertsHeartbeat;
import com.kingsrook.qbits.alerts.model.AlertsInboxItem;
import com.kingsrook.qbits.alerts.model.AlertsMonitor;
import com.kingsrook.qbits.alerts.model.AlertsMute;
import com.kingsrook.qbits.alerts.model.AlertsNotification;
import com.kingsrook.qbits.alerts.model.AlertsRecordRef;
import com.kingsrook.qbits.alerts.model.AlertsSubscription;
import com.kingsrook.qbits.alerts.model.AlertsTeam;
import com.kingsrook.qbits.alerts.model.AlertsTeamMember;
import com.kingsrook.qbits.alerts.model.AlertsType;
import com.kingsrook.qbits.alerts.model.AlertsUserPreference;
import com.kingsrook.qbits.alerts.spi.AlertSeverity;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitConfig;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Host-facing configuration for the Alerts QBit (design section 8).
 **
 ** Tables are named alerts_(name) (lowercase snake_case; the backend table and
 ** column names are the snake_case forms, matching the bundled Liquibase
 ** changelog). Permissions:
 ** - alerts.view: the Alerts app, home and bell; read the alerts the user may
 **   see; comment; subscribe themselves; their own settings and inbox.
 ** - alerts.manage: acknowledge, snooze, resolve, reopen, assign, mute.
 ** - alerts.admin: monitors, types, groups, teams, channels, every
 **   subscription, the notification log, retries.
 ** Each table also has QQQ's standard per-table permissions
 ** (alerts_alert.read, ...); getViewerPermissionNames,
 ** getManagerPermissionNames and getAdminPermissionNames list what to grant
 ** per role. Nothing is granted by default.
 *******************************************************************************/
public class AlertsQBitConfig implements QBitConfig
{
   public static final String TABLE_NAME_PREFIX    = "alerts_";
   public static final String PERMISSION_BASE_NAME = "alerts";

   public static final String PERMISSION_VIEW   = "alerts.view";
   public static final String PERMISSION_MANAGE = "alerts.manage";
   public static final String PERMISSION_ADMIN  = "alerts.admin";

   public static final String APP_NAME = "alerts";

   private String backendName;
   private String schedulerName;
   private String messagingProviderName;
   private String fromAddress;
   private String publicBaseUrl;

   private Integer reopenWindowMinutes   = 30;
   private Integer flapThreshold         = 5;
   private Integer flapWindowMinutes     = 60;
   private Integer maxOpenPerMonitor     = 50;
   private Integer monitorTimeoutSeconds = 15;

   private AlertSeverity bootstrapMinSeverity = AlertSeverity.ERROR;

   private Integer evaluateEverySeconds = 30;
   private Integer deliverEverySeconds  = 15;

   private List<QCodeReference> channelTypes = new ArrayList<>();

   private Boolean registerRoutes  = true;
   private Boolean synchronousSink = false;

   private transient AlertsRuntime runtime;



   /*******************************************************************************
    ** The backend must be named and exist; numbers must be positive.
    *******************************************************************************/
   @Override
   public void validate(QInstance qInstance, List<String> errors)
   {
      if(assertCondition(StringUtils.hasContent(backendName), "backendName is required for the Alerts QBit", errors))
      {
         assertCondition(qInstance.getBackend(backendName) != null, "Alerts QBit backend not found: " + backendName, errors);
      }
      if(StringUtils.hasContent(schedulerName))
      {
         assertCondition(qInstance.getScheduler(schedulerName) != null, "Alerts QBit scheduler not found: " + schedulerName, errors);
      }
      if(StringUtils.hasContent(messagingProviderName))
      {
         assertCondition(qInstance.getMessagingProvider(messagingProviderName) != null, "Alerts QBit messaging provider not found: " + messagingProviderName, errors);
      }
      for(Integer value : new Integer[] {reopenWindowMinutes, flapThreshold, flapWindowMinutes, maxOpenPerMonitor, monitorTimeoutSeconds, evaluateEverySeconds, deliverEverySeconds})
      {
         assertCondition(value != null && value > 0, "Alerts QBit timing and limit settings must be positive", errors);
      }
   }



   /*******************************************************************************
    ** Tables without their own backend use the configured backend.
    *******************************************************************************/
   @Override
   public String getDefaultBackendNameForTables()
   {
      return (backendName);
   }



   /*******************************************************************************
    ** Every table the qbit defines.
    *******************************************************************************/
   public static List<String> allTableNames()
   {
      return (List.of(AlertsAlert.TABLE_NAME, AlertsMonitor.TABLE_NAME, AlertsType.TABLE_NAME, AlertsGroup.TABLE_NAME, AlertsSubscription.TABLE_NAME,
         AlertsUserPreference.TABLE_NAME, AlertsTeam.TABLE_NAME, AlertsTeamMember.TABLE_NAME, AlertsChannel.TABLE_NAME, AlertsMute.TABLE_NAME,
         AlertsNotification.TABLE_NAME, AlertsEvent.TABLE_NAME, AlertsRecordRef.TABLE_NAME, AlertsInboxItem.TABLE_NAME, AlertsHeartbeat.TABLE_NAME));
   }



   /*******************************************************************************
    ** What an ordinary alerts user needs: alerts.view, read on the alert,
    ** timeline, record, type, group, monitor, team and mute tables, and full
    ** access to their own subscriptions, settings and inbox (record locks keep
    ** those to their own rows).
    *******************************************************************************/
   public List<String> getViewerPermissionNames()
   {
      List<String> names = new ArrayList<>();
      names.add(PERMISSION_VIEW);
      for(String table : List.of(AlertsAlert.TABLE_NAME, AlertsEvent.TABLE_NAME, AlertsRecordRef.TABLE_NAME, AlertsType.TABLE_NAME, AlertsGroup.TABLE_NAME,
         AlertsMonitor.TABLE_NAME, AlertsTeam.TABLE_NAME, AlertsTeamMember.TABLE_NAME, AlertsMute.TABLE_NAME))
      {
         names.add(table + ".read");
      }
      for(String table : List.of(AlertsSubscription.TABLE_NAME, AlertsUserPreference.TABLE_NAME, AlertsInboxItem.TABLE_NAME))
      {
         names.add(table + ".read");
         names.add(table + ".insert");
         names.add(table + ".edit");
         names.add(table + ".delete");
      }
      return (names);
   }



   /*******************************************************************************
    ** A viewer's permissions plus alerts.manage and mute editing.
    *******************************************************************************/
   public List<String> getManagerPermissionNames()
   {
      List<String> names = new ArrayList<>(getViewerPermissionNames());
      names.add(PERMISSION_MANAGE);
      names.add(AlertsMute.TABLE_NAME + ".insert");
      names.add(AlertsMute.TABLE_NAME + ".edit");
      names.add(AlertsMute.TABLE_NAME + ".delete");
      return (names);
   }



   /*******************************************************************************
    ** Everything: all three alerts permissions and every table permission.
    *******************************************************************************/
   public List<String> getAdminPermissionNames()
   {
      List<String> names = new ArrayList<>(List.of(PERMISSION_VIEW, PERMISSION_MANAGE, PERMISSION_ADMIN));
      for(String table : allTableNames())
      {
         for(String suffix : List.of("read", "insert", "edit", "delete"))
         {
            names.add(table + "." + suffix);
         }
      }
      return (names);
   }



   /*******************************************************************************
    ** Getter for backendName
    *******************************************************************************/
   public String getBackendName()
   {
      return (this.backendName);
   }



   /*******************************************************************************
    ** Setter for backendName
    *******************************************************************************/
   public void setBackendName(String backendName)
   {
      this.backendName = backendName;
   }



   /*******************************************************************************
    ** Fluent setter for backendName (required: the RDBMS backend for all tables)
    *******************************************************************************/
   public AlertsQBitConfig withBackendName(String backendName)
   {
      this.backendName = backendName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for schedulerName
    *******************************************************************************/
   public String getSchedulerName()
   {
      return (this.schedulerName);
   }



   /*******************************************************************************
    ** Fluent setter for schedulerName. Without it, producer signals still work,
    ** but monitors and delivery do not run on their own (the home says so).
    *******************************************************************************/
   public AlertsQBitConfig withSchedulerName(String schedulerName)
   {
      this.schedulerName = schedulerName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for messagingProviderName
    *******************************************************************************/
   public String getMessagingProviderName()
   {
      return (this.messagingProviderName);
   }



   /*******************************************************************************
    ** Fluent setter for messagingProviderName (a QMessagingProviderMetaData for
    ** email; without it, email channels are disabled with an explanation).
    *******************************************************************************/
   public AlertsQBitConfig withMessagingProviderName(String messagingProviderName)
   {
      this.messagingProviderName = messagingProviderName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for fromAddress
    *******************************************************************************/
   public String getFromAddress()
   {
      return (this.fromAddress);
   }



   /*******************************************************************************
    ** Fluent setter for fromAddress (the From of alert emails)
    *******************************************************************************/
   public AlertsQBitConfig withFromAddress(String fromAddress)
   {
      this.fromAddress = fromAddress;
      return (this);
   }



   /*******************************************************************************
    ** Getter for publicBaseUrl
    *******************************************************************************/
   public String getPublicBaseUrl()
   {
      return (this.publicBaseUrl);
   }



   /*******************************************************************************
    ** Fluent setter for publicBaseUrl (the base for links in messages, e.g.
    ** https://admin.example.com)
    *******************************************************************************/
   public AlertsQBitConfig withPublicBaseUrl(String publicBaseUrl)
   {
      this.publicBaseUrl = publicBaseUrl;
      return (this);
   }



   /*******************************************************************************
    ** Getter for reopenWindowMinutes
    *******************************************************************************/
   public Integer getReopenWindowMinutes()
   {
      return (this.reopenWindowMinutes);
   }



   /*******************************************************************************
    ** Fluent setter for reopenWindowMinutes (a raise this soon after a resolve
    ** reopens the same alert; default 30)
    *******************************************************************************/
   public AlertsQBitConfig withReopenWindowMinutes(Integer reopenWindowMinutes)
   {
      this.reopenWindowMinutes = reopenWindowMinutes;
      return (this);
   }



   /*******************************************************************************
    ** Getter for flapThreshold
    *******************************************************************************/
   public Integer getFlapThreshold()
   {
      return (this.flapThreshold);
   }



   /*******************************************************************************
    ** Fluent setter for flapThreshold (open/resolve changes within the flap
    ** window that mark an alert flapping; default 5)
    *******************************************************************************/
   public AlertsQBitConfig withFlapThreshold(Integer flapThreshold)
   {
      this.flapThreshold = flapThreshold;
      return (this);
   }



   /*******************************************************************************
    ** Getter for flapWindowMinutes
    *******************************************************************************/
   public Integer getFlapWindowMinutes()
   {
      return (this.flapWindowMinutes);
   }



   /*******************************************************************************
    ** Fluent setter for flapWindowMinutes (default 60)
    *******************************************************************************/
   public AlertsQBitConfig withFlapWindowMinutes(Integer flapWindowMinutes)
   {
      this.flapWindowMinutes = flapWindowMinutes;
      return (this);
   }



   /*******************************************************************************
    ** Getter for maxOpenPerMonitor
    *******************************************************************************/
   public Integer getMaxOpenPerMonitor()
   {
      return (this.maxOpenPerMonitor);
   }



   /*******************************************************************************
    ** Fluent setter for maxOpenPerMonitor (the per-record cap; beyond it a
    ** monitor raises one aggregate alert instead; default 50)
    *******************************************************************************/
   public AlertsQBitConfig withMaxOpenPerMonitor(Integer maxOpenPerMonitor)
   {
      this.maxOpenPerMonitor = maxOpenPerMonitor;
      return (this);
   }



   /*******************************************************************************
    ** Getter for monitorTimeoutSeconds
    *******************************************************************************/
   public Integer getMonitorTimeoutSeconds()
   {
      return (this.monitorTimeoutSeconds);
   }



   /*******************************************************************************
    ** Fluent setter for monitorTimeoutSeconds (default 15)
    *******************************************************************************/
   public AlertsQBitConfig withMonitorTimeoutSeconds(Integer monitorTimeoutSeconds)
   {
      this.monitorTimeoutSeconds = monitorTimeoutSeconds;
      return (this);
   }



   /*******************************************************************************
    ** Getter for bootstrapMinSeverity
    *******************************************************************************/
   public AlertSeverity getBootstrapMinSeverity()
   {
      return (this.bootstrapMinSeverity);
   }



   /*******************************************************************************
    ** Fluent setter for bootstrapMinSeverity: the minimum severity of the
    ** in-app subscription given to alerts.admin holders who have none (default
    ** ERROR; null turns the bootstrap rule off).
    *******************************************************************************/
   public AlertsQBitConfig withBootstrapMinSeverity(AlertSeverity bootstrapMinSeverity)
   {
      this.bootstrapMinSeverity = bootstrapMinSeverity;
      return (this);
   }



   /*******************************************************************************
    ** Getter for evaluateEverySeconds
    *******************************************************************************/
   public Integer getEvaluateEverySeconds()
   {
      return (this.evaluateEverySeconds);
   }



   /*******************************************************************************
    ** Fluent setter for evaluateEverySeconds (the monitor evaluator's tick;
    ** default 30)
    *******************************************************************************/
   public AlertsQBitConfig withEvaluateEverySeconds(Integer evaluateEverySeconds)
   {
      this.evaluateEverySeconds = evaluateEverySeconds;
      return (this);
   }



   /*******************************************************************************
    ** Getter for deliverEverySeconds
    *******************************************************************************/
   public Integer getDeliverEverySeconds()
   {
      return (this.deliverEverySeconds);
   }



   /*******************************************************************************
    ** Fluent setter for deliverEverySeconds (the delivery worker's tick;
    ** default 15)
    *******************************************************************************/
   public AlertsQBitConfig withDeliverEverySeconds(Integer deliverEverySeconds)
   {
      this.deliverEverySeconds = deliverEverySeconds;
      return (this);
   }



   /*******************************************************************************
    ** Getter for channelTypes
    *******************************************************************************/
   public List<QCodeReference> getChannelTypes()
   {
      return (this.channelTypes);
   }



   /*******************************************************************************
    ** Add a channel type (an AlertChannelInterface), beyond the built-ins.
    *******************************************************************************/
   public AlertsQBitConfig withChannelType(QCodeReference channelType)
   {
      this.channelTypes.add(channelType);
      return (this);
   }



   /*******************************************************************************
    ** Getter for registerRoutes
    *******************************************************************************/
   public Boolean getRegisterRoutes()
   {
      return (this.registerRoutes);
   }



   /*******************************************************************************
    ** Fluent setter for registerRoutes (the /qqq/v1/alerts routes for the bell
    ** and record panel, when qqq-middleware-javalin is present; default true)
    *******************************************************************************/
   public AlertsQBitConfig withRegisterRoutes(Boolean registerRoutes)
   {
      this.registerRoutes = registerRoutes;
      return (this);
   }



   /*******************************************************************************
    ** Getter for synchronousSink
    *******************************************************************************/
   public Boolean getSynchronousSink()
   {
      return (this.synchronousSink);
   }



   /*******************************************************************************
    ** Fluent setter for synchronousSink: process signals on the caller's
    ** thread instead of the background queue (tests; default false).
    *******************************************************************************/
   public AlertsQBitConfig withSynchronousSink(Boolean synchronousSink)
   {
      this.synchronousSink = synchronousSink;
      return (this);
   }



   /*******************************************************************************
    ** Getter for runtime (built by the producer)
    *******************************************************************************/
   public AlertsRuntime getRuntime()
   {
      return (this.runtime);
   }



   /*******************************************************************************
    ** Setter for runtime
    *******************************************************************************/
   public void setRuntime(AlertsRuntime runtime)
   {
      this.runtime = runtime;
   }

}
