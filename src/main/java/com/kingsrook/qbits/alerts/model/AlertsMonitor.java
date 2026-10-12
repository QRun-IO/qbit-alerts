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
 ** A configured check that raises alerts of a type: table query, aggregate
 ** threshold, heartbeat, process failure or code check. Seeded monitors may be
 ** tuned or disabled, not deleted.
 **
 ** Generated from the qbit-alerts schema (one definition for the entity,
 ** the Liquibase changelog and the test DDL).
 *******************************************************************************/
public class AlertsMonitor extends QRecordEntity
{
   public static final String TABLE_NAME = "alerts_monitor";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true, maxLength = 200, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String name;

   @QField(isRequired = true, possibleValueSourceName = "alerts_type", label = "Alert Type")
   private Integer alertTypeId;

   @QField(isRequired = true, maxLength = 20, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, possibleValueSourceName = "alerts_monitor_kind")
   private String kind;

   @QField()
   private Boolean enabled;

   @QField(isEditable = false)
   private Boolean seeded;

   @QField(isEditable = false, maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, label = "Source")
   private String sourceName;

   @QField()
   private String description;

   @QField()
   private String definition;

   @QField(label = "One Alert per Record")
   private Boolean perRecord;

   @QField(maxLength = 16, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, possibleValueSourceName = "alerts_severity")
   private String severity;

   @QField(label = "Check Every (Seconds)")
   private Integer scheduleSeconds;

   @QField(label = "Must Hold For (Minutes)")
   private Integer forMinutes;

   @QField(isEditable = false)
   private Instant nextRunAt;

   @QField(isEditable = false, maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String claimedBy;

   @QField(isEditable = false)
   private Instant claimedUntil;

   @QField(isEditable = false)
   private Instant lastRunAt;

   @QField(isEditable = false, maxLength = 20, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, possibleValueSourceName = "alerts_monitor_status")
   private String lastStatus;

   @QField(isEditable = false, maxLength = 200, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String lastValue;

   @QField(isEditable = false, maxLength = 2000, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String lastError;

   @QField(isEditable = false, label = "Last Elapsed (ms)")
   private Integer lastElapsedMs;

   @QField(isEditable = false)
   private Integer consecutiveFailures;

   @QField(isEditable = false)
   private Instant conditionSince;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AlertsMonitor()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AlertsMonitor(QRecord record)
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
   public AlertsMonitor withId(Integer id)
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
   public AlertsMonitor withName(String name)
   {
      this.name = name;
      return (this);
   }



   /*******************************************************************************
    ** Getter for alertTypeId
    *******************************************************************************/
   public Integer getAlertTypeId()
   {
      return (this.alertTypeId);
   }



   /*******************************************************************************
    ** Setter for alertTypeId
    *******************************************************************************/
   public void setAlertTypeId(Integer alertTypeId)
   {
      this.alertTypeId = alertTypeId;
   }



   /*******************************************************************************
    ** Fluent setter for alertTypeId
    *******************************************************************************/
   public AlertsMonitor withAlertTypeId(Integer alertTypeId)
   {
      this.alertTypeId = alertTypeId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for kind
    *******************************************************************************/
   public String getKind()
   {
      return (this.kind);
   }



   /*******************************************************************************
    ** Setter for kind
    *******************************************************************************/
   public void setKind(String kind)
   {
      this.kind = kind;
   }



   /*******************************************************************************
    ** Fluent setter for kind
    *******************************************************************************/
   public AlertsMonitor withKind(String kind)
   {
      this.kind = kind;
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
   public AlertsMonitor withEnabled(Boolean enabled)
   {
      this.enabled = enabled;
      return (this);
   }



   /*******************************************************************************
    ** Getter for seeded
    *******************************************************************************/
   public Boolean getSeeded()
   {
      return (this.seeded);
   }



   /*******************************************************************************
    ** Setter for seeded
    *******************************************************************************/
   public void setSeeded(Boolean seeded)
   {
      this.seeded = seeded;
   }



   /*******************************************************************************
    ** Fluent setter for seeded
    *******************************************************************************/
   public AlertsMonitor withSeeded(Boolean seeded)
   {
      this.seeded = seeded;
      return (this);
   }



   /*******************************************************************************
    ** Getter for sourceName
    *******************************************************************************/
   public String getSourceName()
   {
      return (this.sourceName);
   }



   /*******************************************************************************
    ** Setter for sourceName
    *******************************************************************************/
   public void setSourceName(String sourceName)
   {
      this.sourceName = sourceName;
   }



   /*******************************************************************************
    ** Fluent setter for sourceName
    *******************************************************************************/
   public AlertsMonitor withSourceName(String sourceName)
   {
      this.sourceName = sourceName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for description
    *******************************************************************************/
   public String getDescription()
   {
      return (this.description);
   }



   /*******************************************************************************
    ** Setter for description
    *******************************************************************************/
   public void setDescription(String description)
   {
      this.description = description;
   }



   /*******************************************************************************
    ** Fluent setter for description
    *******************************************************************************/
   public AlertsMonitor withDescription(String description)
   {
      this.description = description;
      return (this);
   }



   /*******************************************************************************
    ** Getter for definition
    *******************************************************************************/
   public String getDefinition()
   {
      return (this.definition);
   }



   /*******************************************************************************
    ** Setter for definition
    *******************************************************************************/
   public void setDefinition(String definition)
   {
      this.definition = definition;
   }



   /*******************************************************************************
    ** Fluent setter for definition
    *******************************************************************************/
   public AlertsMonitor withDefinition(String definition)
   {
      this.definition = definition;
      return (this);
   }



   /*******************************************************************************
    ** Getter for perRecord
    *******************************************************************************/
   public Boolean getPerRecord()
   {
      return (this.perRecord);
   }



   /*******************************************************************************
    ** Setter for perRecord
    *******************************************************************************/
   public void setPerRecord(Boolean perRecord)
   {
      this.perRecord = perRecord;
   }



   /*******************************************************************************
    ** Fluent setter for perRecord
    *******************************************************************************/
   public AlertsMonitor withPerRecord(Boolean perRecord)
   {
      this.perRecord = perRecord;
      return (this);
   }



   /*******************************************************************************
    ** Getter for severity
    *******************************************************************************/
   public String getSeverity()
   {
      return (this.severity);
   }



   /*******************************************************************************
    ** Setter for severity
    *******************************************************************************/
   public void setSeverity(String severity)
   {
      this.severity = severity;
   }



   /*******************************************************************************
    ** Fluent setter for severity
    *******************************************************************************/
   public AlertsMonitor withSeverity(String severity)
   {
      this.severity = severity;
      return (this);
   }



   /*******************************************************************************
    ** Getter for scheduleSeconds
    *******************************************************************************/
   public Integer getScheduleSeconds()
   {
      return (this.scheduleSeconds);
   }



   /*******************************************************************************
    ** Setter for scheduleSeconds
    *******************************************************************************/
   public void setScheduleSeconds(Integer scheduleSeconds)
   {
      this.scheduleSeconds = scheduleSeconds;
   }



   /*******************************************************************************
    ** Fluent setter for scheduleSeconds
    *******************************************************************************/
   public AlertsMonitor withScheduleSeconds(Integer scheduleSeconds)
   {
      this.scheduleSeconds = scheduleSeconds;
      return (this);
   }



   /*******************************************************************************
    ** Getter for forMinutes
    *******************************************************************************/
   public Integer getForMinutes()
   {
      return (this.forMinutes);
   }



   /*******************************************************************************
    ** Setter for forMinutes
    *******************************************************************************/
   public void setForMinutes(Integer forMinutes)
   {
      this.forMinutes = forMinutes;
   }



   /*******************************************************************************
    ** Fluent setter for forMinutes
    *******************************************************************************/
   public AlertsMonitor withForMinutes(Integer forMinutes)
   {
      this.forMinutes = forMinutes;
      return (this);
   }



   /*******************************************************************************
    ** Getter for nextRunAt
    *******************************************************************************/
   public Instant getNextRunAt()
   {
      return (this.nextRunAt);
   }



   /*******************************************************************************
    ** Setter for nextRunAt
    *******************************************************************************/
   public void setNextRunAt(Instant nextRunAt)
   {
      this.nextRunAt = nextRunAt;
   }



   /*******************************************************************************
    ** Fluent setter for nextRunAt
    *******************************************************************************/
   public AlertsMonitor withNextRunAt(Instant nextRunAt)
   {
      this.nextRunAt = nextRunAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for claimedBy
    *******************************************************************************/
   public String getClaimedBy()
   {
      return (this.claimedBy);
   }



   /*******************************************************************************
    ** Setter for claimedBy
    *******************************************************************************/
   public void setClaimedBy(String claimedBy)
   {
      this.claimedBy = claimedBy;
   }



   /*******************************************************************************
    ** Fluent setter for claimedBy
    *******************************************************************************/
   public AlertsMonitor withClaimedBy(String claimedBy)
   {
      this.claimedBy = claimedBy;
      return (this);
   }



   /*******************************************************************************
    ** Getter for claimedUntil
    *******************************************************************************/
   public Instant getClaimedUntil()
   {
      return (this.claimedUntil);
   }



   /*******************************************************************************
    ** Setter for claimedUntil
    *******************************************************************************/
   public void setClaimedUntil(Instant claimedUntil)
   {
      this.claimedUntil = claimedUntil;
   }



   /*******************************************************************************
    ** Fluent setter for claimedUntil
    *******************************************************************************/
   public AlertsMonitor withClaimedUntil(Instant claimedUntil)
   {
      this.claimedUntil = claimedUntil;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastRunAt
    *******************************************************************************/
   public Instant getLastRunAt()
   {
      return (this.lastRunAt);
   }



   /*******************************************************************************
    ** Setter for lastRunAt
    *******************************************************************************/
   public void setLastRunAt(Instant lastRunAt)
   {
      this.lastRunAt = lastRunAt;
   }



   /*******************************************************************************
    ** Fluent setter for lastRunAt
    *******************************************************************************/
   public AlertsMonitor withLastRunAt(Instant lastRunAt)
   {
      this.lastRunAt = lastRunAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastStatus
    *******************************************************************************/
   public String getLastStatus()
   {
      return (this.lastStatus);
   }



   /*******************************************************************************
    ** Setter for lastStatus
    *******************************************************************************/
   public void setLastStatus(String lastStatus)
   {
      this.lastStatus = lastStatus;
   }



   /*******************************************************************************
    ** Fluent setter for lastStatus
    *******************************************************************************/
   public AlertsMonitor withLastStatus(String lastStatus)
   {
      this.lastStatus = lastStatus;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastValue
    *******************************************************************************/
   public String getLastValue()
   {
      return (this.lastValue);
   }



   /*******************************************************************************
    ** Setter for lastValue
    *******************************************************************************/
   public void setLastValue(String lastValue)
   {
      this.lastValue = lastValue;
   }



   /*******************************************************************************
    ** Fluent setter for lastValue
    *******************************************************************************/
   public AlertsMonitor withLastValue(String lastValue)
   {
      this.lastValue = lastValue;
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
   public AlertsMonitor withLastError(String lastError)
   {
      this.lastError = lastError;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastElapsedMs
    *******************************************************************************/
   public Integer getLastElapsedMs()
   {
      return (this.lastElapsedMs);
   }



   /*******************************************************************************
    ** Setter for lastElapsedMs
    *******************************************************************************/
   public void setLastElapsedMs(Integer lastElapsedMs)
   {
      this.lastElapsedMs = lastElapsedMs;
   }



   /*******************************************************************************
    ** Fluent setter for lastElapsedMs
    *******************************************************************************/
   public AlertsMonitor withLastElapsedMs(Integer lastElapsedMs)
   {
      this.lastElapsedMs = lastElapsedMs;
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
   public AlertsMonitor withConsecutiveFailures(Integer consecutiveFailures)
   {
      this.consecutiveFailures = consecutiveFailures;
      return (this);
   }



   /*******************************************************************************
    ** Getter for conditionSince
    *******************************************************************************/
   public Instant getConditionSince()
   {
      return (this.conditionSince);
   }



   /*******************************************************************************
    ** Setter for conditionSince
    *******************************************************************************/
   public void setConditionSince(Instant conditionSince)
   {
      this.conditionSince = conditionSince;
   }



   /*******************************************************************************
    ** Fluent setter for conditionSince
    *******************************************************************************/
   public AlertsMonitor withConditionSince(Instant conditionSince)
   {
      this.conditionSince = conditionSince;
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
   public AlertsMonitor withCreateDate(Instant createDate)
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
   public AlertsMonitor withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }
}
