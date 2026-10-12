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


import java.time.Instant;
import com.kingsrook.qqq.backend.core.model.data.QField;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.data.QRecordEntity;
import com.kingsrook.qqq.backend.core.model.metadata.fields.ValueTooLongBehavior;


/*******************************************************************************
 ** A configured destination (in-app, email, an email list). It holds settings
 ** and a reference to its secret, never the secret.
 **
 ** Generated from the qbit-alerts schema (one definition for the entity,
 ** the Liquibase changelog and the test DDL).
 *******************************************************************************/
public class AlertsChannel extends QRecordEntity
{
   public static final String TABLE_NAME = "alerts_channel";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true, maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String name;

   @QField(isRequired = true, maxLength = 200, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String label;

   @QField(isRequired = true, maxLength = 20, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, possibleValueSourceName = "alerts_channel_type")
   private String channelType;

   @QField()
   private Boolean enabled;

   @QField()
   private String settings;

   @QField(maxLength = 200, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, label = "Secret Reference")
   private String secretRef;

   @QField(maxLength = 16, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, possibleValueSourceName = "alerts_detail_level")
   private String detailLevel;

   @QField(maxLength = 16, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, possibleValueSourceName = "alerts_severity")
   private String minSeverity;

   @QField()
   private Integer rateLimitPerHour;

   @QField(isEditable = false, maxLength = 16, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, possibleValueSourceName = "alerts_health_status")
   private String healthStatus;

   @QField(isEditable = false)
   private Instant lastSuccessAt;

   @QField(isEditable = false)
   private Instant lastFailureAt;

   @QField(isEditable = false)
   private Integer consecutiveFailures;

   @QField(isEditable = false, maxLength = 2000, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String lastError;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AlertsChannel()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AlertsChannel(QRecord record)
   {
      populateFromQRecord(record);
   }



   /*******************************************************************************
    ** Getter for id
    *******************************************************************************/
   public Integer getId()
   {
      return (this.id);
   }



   /*******************************************************************************
    ** Setter for id
    *******************************************************************************/
   public void setId(Integer id)
   {
      this.id = id;
   }



   /*******************************************************************************
    ** Fluent setter for id
    *******************************************************************************/
   public AlertsChannel withId(Integer id)
   {
      this.id = id;
      return (this);
   }



   /*******************************************************************************
    ** Getter for name
    *******************************************************************************/
   public String getName()
   {
      return (this.name);
   }



   /*******************************************************************************
    ** Setter for name
    *******************************************************************************/
   public void setName(String name)
   {
      this.name = name;
   }



   /*******************************************************************************
    ** Fluent setter for name
    *******************************************************************************/
   public AlertsChannel withName(String name)
   {
      this.name = name;
      return (this);
   }



   /*******************************************************************************
    ** Getter for label
    *******************************************************************************/
   public String getLabel()
   {
      return (this.label);
   }



   /*******************************************************************************
    ** Setter for label
    *******************************************************************************/
   public void setLabel(String label)
   {
      this.label = label;
   }



   /*******************************************************************************
    ** Fluent setter for label
    *******************************************************************************/
   public AlertsChannel withLabel(String label)
   {
      this.label = label;
      return (this);
   }



   /*******************************************************************************
    ** Getter for channelType
    *******************************************************************************/
   public String getChannelType()
   {
      return (this.channelType);
   }



   /*******************************************************************************
    ** Setter for channelType
    *******************************************************************************/
   public void setChannelType(String channelType)
   {
      this.channelType = channelType;
   }



   /*******************************************************************************
    ** Fluent setter for channelType
    *******************************************************************************/
   public AlertsChannel withChannelType(String channelType)
   {
      this.channelType = channelType;
      return (this);
   }



   /*******************************************************************************
    ** Getter for enabled
    *******************************************************************************/
   public Boolean getEnabled()
   {
      return (this.enabled);
   }



   /*******************************************************************************
    ** Setter for enabled
    *******************************************************************************/
   public void setEnabled(Boolean enabled)
   {
      this.enabled = enabled;
   }



   /*******************************************************************************
    ** Fluent setter for enabled
    *******************************************************************************/
   public AlertsChannel withEnabled(Boolean enabled)
   {
      this.enabled = enabled;
      return (this);
   }



   /*******************************************************************************
    ** Getter for settings
    *******************************************************************************/
   public String getSettings()
   {
      return (this.settings);
   }



   /*******************************************************************************
    ** Setter for settings
    *******************************************************************************/
   public void setSettings(String settings)
   {
      this.settings = settings;
   }



   /*******************************************************************************
    ** Fluent setter for settings
    *******************************************************************************/
   public AlertsChannel withSettings(String settings)
   {
      this.settings = settings;
      return (this);
   }



   /*******************************************************************************
    ** Getter for secretRef
    *******************************************************************************/
   public String getSecretRef()
   {
      return (this.secretRef);
   }



   /*******************************************************************************
    ** Setter for secretRef
    *******************************************************************************/
   public void setSecretRef(String secretRef)
   {
      this.secretRef = secretRef;
   }



   /*******************************************************************************
    ** Fluent setter for secretRef
    *******************************************************************************/
   public AlertsChannel withSecretRef(String secretRef)
   {
      this.secretRef = secretRef;
      return (this);
   }



   /*******************************************************************************
    ** Getter for detailLevel
    *******************************************************************************/
   public String getDetailLevel()
   {
      return (this.detailLevel);
   }



   /*******************************************************************************
    ** Setter for detailLevel
    *******************************************************************************/
   public void setDetailLevel(String detailLevel)
   {
      this.detailLevel = detailLevel;
   }



   /*******************************************************************************
    ** Fluent setter for detailLevel
    *******************************************************************************/
   public AlertsChannel withDetailLevel(String detailLevel)
   {
      this.detailLevel = detailLevel;
      return (this);
   }



   /*******************************************************************************
    ** Getter for minSeverity
    *******************************************************************************/
   public String getMinSeverity()
   {
      return (this.minSeverity);
   }



   /*******************************************************************************
    ** Setter for minSeverity
    *******************************************************************************/
   public void setMinSeverity(String minSeverity)
   {
      this.minSeverity = minSeverity;
   }



   /*******************************************************************************
    ** Fluent setter for minSeverity
    *******************************************************************************/
   public AlertsChannel withMinSeverity(String minSeverity)
   {
      this.minSeverity = minSeverity;
      return (this);
   }



   /*******************************************************************************
    ** Getter for rateLimitPerHour
    *******************************************************************************/
   public Integer getRateLimitPerHour()
   {
      return (this.rateLimitPerHour);
   }



   /*******************************************************************************
    ** Setter for rateLimitPerHour
    *******************************************************************************/
   public void setRateLimitPerHour(Integer rateLimitPerHour)
   {
      this.rateLimitPerHour = rateLimitPerHour;
   }



   /*******************************************************************************
    ** Fluent setter for rateLimitPerHour
    *******************************************************************************/
   public AlertsChannel withRateLimitPerHour(Integer rateLimitPerHour)
   {
      this.rateLimitPerHour = rateLimitPerHour;
      return (this);
   }



   /*******************************************************************************
    ** Getter for healthStatus
    *******************************************************************************/
   public String getHealthStatus()
   {
      return (this.healthStatus);
   }



   /*******************************************************************************
    ** Setter for healthStatus
    *******************************************************************************/
   public void setHealthStatus(String healthStatus)
   {
      this.healthStatus = healthStatus;
   }



   /*******************************************************************************
    ** Fluent setter for healthStatus
    *******************************************************************************/
   public AlertsChannel withHealthStatus(String healthStatus)
   {
      this.healthStatus = healthStatus;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastSuccessAt
    *******************************************************************************/
   public Instant getLastSuccessAt()
   {
      return (this.lastSuccessAt);
   }



   /*******************************************************************************
    ** Setter for lastSuccessAt
    *******************************************************************************/
   public void setLastSuccessAt(Instant lastSuccessAt)
   {
      this.lastSuccessAt = lastSuccessAt;
   }



   /*******************************************************************************
    ** Fluent setter for lastSuccessAt
    *******************************************************************************/
   public AlertsChannel withLastSuccessAt(Instant lastSuccessAt)
   {
      this.lastSuccessAt = lastSuccessAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastFailureAt
    *******************************************************************************/
   public Instant getLastFailureAt()
   {
      return (this.lastFailureAt);
   }



   /*******************************************************************************
    ** Setter for lastFailureAt
    *******************************************************************************/
   public void setLastFailureAt(Instant lastFailureAt)
   {
      this.lastFailureAt = lastFailureAt;
   }



   /*******************************************************************************
    ** Fluent setter for lastFailureAt
    *******************************************************************************/
   public AlertsChannel withLastFailureAt(Instant lastFailureAt)
   {
      this.lastFailureAt = lastFailureAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for consecutiveFailures
    *******************************************************************************/
   public Integer getConsecutiveFailures()
   {
      return (this.consecutiveFailures);
   }



   /*******************************************************************************
    ** Setter for consecutiveFailures
    *******************************************************************************/
   public void setConsecutiveFailures(Integer consecutiveFailures)
   {
      this.consecutiveFailures = consecutiveFailures;
   }



   /*******************************************************************************
    ** Fluent setter for consecutiveFailures
    *******************************************************************************/
   public AlertsChannel withConsecutiveFailures(Integer consecutiveFailures)
   {
      this.consecutiveFailures = consecutiveFailures;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastError
    *******************************************************************************/
   public String getLastError()
   {
      return (this.lastError);
   }



   /*******************************************************************************
    ** Setter for lastError
    *******************************************************************************/
   public void setLastError(String lastError)
   {
      this.lastError = lastError;
   }



   /*******************************************************************************
    ** Fluent setter for lastError
    *******************************************************************************/
   public AlertsChannel withLastError(String lastError)
   {
      this.lastError = lastError;
      return (this);
   }



   /*******************************************************************************
    ** Getter for createDate
    *******************************************************************************/
   public Instant getCreateDate()
   {
      return (this.createDate);
   }



   /*******************************************************************************
    ** Setter for createDate
    *******************************************************************************/
   public void setCreateDate(Instant createDate)
   {
      this.createDate = createDate;
   }



   /*******************************************************************************
    ** Fluent setter for createDate
    *******************************************************************************/
   public AlertsChannel withCreateDate(Instant createDate)
   {
      this.createDate = createDate;
      return (this);
   }



   /*******************************************************************************
    ** Getter for modifyDate
    *******************************************************************************/
   public Instant getModifyDate()
   {
      return (this.modifyDate);
   }



   /*******************************************************************************
    ** Setter for modifyDate
    *******************************************************************************/
   public void setModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
   }



   /*******************************************************************************
    ** Fluent setter for modifyDate
    *******************************************************************************/
   public AlertsChannel withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }
}
