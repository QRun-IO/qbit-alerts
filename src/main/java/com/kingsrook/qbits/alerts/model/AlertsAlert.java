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
 ** One open (or closed) problem, identified by (type, dedupKey). Repeats
 ** update it; they never duplicate it. openDedupKey is unique while the alert
 ** is not resolved.
 **
 ** Generated from the qbit-alerts schema (one definition for the entity,
 ** the Liquibase changelog and the test DDL).
 *******************************************************************************/
public class AlertsAlert extends QRecordEntity
{
   public static final String TABLE_NAME = "alerts_alert";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isEditable = false, possibleValueSourceName = "alerts_type", label = "Alert Type")
   private Integer alertTypeId;

   @QField(isEditable = false, maxLength = 200, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, label = "Type Name")
   private String alertTypeName;

   @QField(isEditable = false, possibleValueSourceName = "alerts_monitor", label = "Monitor")
   private Integer monitorId;

   @QField(isEditable = false, possibleValueSourceName = "alerts_group", label = "Group")
   private Integer groupId;

   @QField(isEditable = false, maxLength = 300, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String dedupKey;

   @QField(isEditable = false, maxLength = 520, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String openDedupKey;

   @QField(isEditable = false, maxLength = 20, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, possibleValueSourceName = "alerts_status")
   private String status;

   @QField(isEditable = false, maxLength = 16, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, possibleValueSourceName = "alerts_severity")
   private String severity;

   @QField(isEditable = false, maxLength = 500, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String title;

   @QField(isEditable = false, maxLength = 2000, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String summary;

   @QField(isEditable = false)
   private String details;

   @QField(isEditable = false, maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, label = "Subject Table")
   private String subjectTableName;

   @QField(isEditable = false, maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, label = "Subject Record")
   private String subjectRecordId;

   @QField(isEditable = false, label = "Records")
   private Integer recordRefCount;

   @QField(isEditable = false, label = "Items")
   private Integer itemCount;

   @QField(isEditable = false)
   private String links;

   @QField(isEditable = false)
   private String attributes;

   @QField(isEditable = false, maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, label = "Source")
   private String sourceName;

   @QField(isEditable = false, maxLength = 200, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String visibilityPermission;

   @QField(isEditable = false)
   private Instant firstRaisedAt;

   @QField(isEditable = false)
   private Instant lastRaisedAt;

   @QField(isEditable = false, label = "Occurrences")
   private Integer occurrenceCount;

   @QField(isEditable = false, label = "Reopened")
   private Integer reopenCount;

   @QField(isEditable = false)
   private Boolean flapping;

   @QField(isEditable = false, maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, label = "Acknowledged By")
   private String acknowledgedByUserId;

   @QField(isEditable = false)
   private Instant acknowledgedAt;

   @QField(isEditable = false)
   private Instant snoozedUntil;

   @QField(isEditable = false, maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, label = "Assigned To")
   private String assignedUserId;

   @QField(isEditable = false, possibleValueSourceName = "alerts_team", label = "Assigned Team")
   private Integer assignedTeamId;

   @QField(isEditable = false)
   private Instant resolvedAt;

   @QField(isEditable = false, maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, label = "Resolved By")
   private String resolvedByUserId;

   @QField(isEditable = false, maxLength = 20, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, possibleValueSourceName = "alerts_resolution")
   private String resolution;

   @QField(isEditable = false, maxLength = 2000, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String resolutionNote;

   @QField(isEditable = false)
   private Boolean muted;

   @QField(isEditable = false)
   private Instant lastNotifiedAt;

   @QField(isEditable = false)
   private Integer escalationLevel;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AlertsAlert()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AlertsAlert(QRecord record)
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
   public AlertsAlert withId(Integer id)
   {
      this.id = id;
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
   public AlertsAlert withAlertTypeId(Integer alertTypeId)
   {
      this.alertTypeId = alertTypeId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for alertTypeName
    *******************************************************************************/
   public String getAlertTypeName()
   {
      return (this.alertTypeName);
   }



   /*******************************************************************************
    ** Setter for alertTypeName
    *******************************************************************************/
   public void setAlertTypeName(String alertTypeName)
   {
      this.alertTypeName = alertTypeName;
   }



   /*******************************************************************************
    ** Fluent setter for alertTypeName
    *******************************************************************************/
   public AlertsAlert withAlertTypeName(String alertTypeName)
   {
      this.alertTypeName = alertTypeName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for monitorId
    *******************************************************************************/
   public Integer getMonitorId()
   {
      return (this.monitorId);
   }



   /*******************************************************************************
    ** Setter for monitorId
    *******************************************************************************/
   public void setMonitorId(Integer monitorId)
   {
      this.monitorId = monitorId;
   }



   /*******************************************************************************
    ** Fluent setter for monitorId
    *******************************************************************************/
   public AlertsAlert withMonitorId(Integer monitorId)
   {
      this.monitorId = monitorId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for groupId
    *******************************************************************************/
   public Integer getGroupId()
   {
      return (this.groupId);
   }



   /*******************************************************************************
    ** Setter for groupId
    *******************************************************************************/
   public void setGroupId(Integer groupId)
   {
      this.groupId = groupId;
   }



   /*******************************************************************************
    ** Fluent setter for groupId
    *******************************************************************************/
   public AlertsAlert withGroupId(Integer groupId)
   {
      this.groupId = groupId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for dedupKey
    *******************************************************************************/
   public String getDedupKey()
   {
      return (this.dedupKey);
   }



   /*******************************************************************************
    ** Setter for dedupKey
    *******************************************************************************/
   public void setDedupKey(String dedupKey)
   {
      this.dedupKey = dedupKey;
   }



   /*******************************************************************************
    ** Fluent setter for dedupKey
    *******************************************************************************/
   public AlertsAlert withDedupKey(String dedupKey)
   {
      this.dedupKey = dedupKey;
      return (this);
   }



   /*******************************************************************************
    ** Getter for openDedupKey
    *******************************************************************************/
   public String getOpenDedupKey()
   {
      return (this.openDedupKey);
   }



   /*******************************************************************************
    ** Setter for openDedupKey
    *******************************************************************************/
   public void setOpenDedupKey(String openDedupKey)
   {
      this.openDedupKey = openDedupKey;
   }



   /*******************************************************************************
    ** Fluent setter for openDedupKey
    *******************************************************************************/
   public AlertsAlert withOpenDedupKey(String openDedupKey)
   {
      this.openDedupKey = openDedupKey;
      return (this);
   }



   /*******************************************************************************
    ** Getter for status
    *******************************************************************************/
   public String getStatus()
   {
      return (this.status);
   }



   /*******************************************************************************
    ** Setter for status
    *******************************************************************************/
   public void setStatus(String status)
   {
      this.status = status;
   }



   /*******************************************************************************
    ** Fluent setter for status
    *******************************************************************************/
   public AlertsAlert withStatus(String status)
   {
      this.status = status;
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
   public AlertsAlert withSeverity(String severity)
   {
      this.severity = severity;
      return (this);
   }



   /*******************************************************************************
    ** Getter for title
    *******************************************************************************/
   public String getTitle()
   {
      return (this.title);
   }



   /*******************************************************************************
    ** Setter for title
    *******************************************************************************/
   public void setTitle(String title)
   {
      this.title = title;
   }



   /*******************************************************************************
    ** Fluent setter for title
    *******************************************************************************/
   public AlertsAlert withTitle(String title)
   {
      this.title = title;
      return (this);
   }



   /*******************************************************************************
    ** Getter for summary
    *******************************************************************************/
   public String getSummary()
   {
      return (this.summary);
   }



   /*******************************************************************************
    ** Setter for summary
    *******************************************************************************/
   public void setSummary(String summary)
   {
      this.summary = summary;
   }



   /*******************************************************************************
    ** Fluent setter for summary
    *******************************************************************************/
   public AlertsAlert withSummary(String summary)
   {
      this.summary = summary;
      return (this);
   }



   /*******************************************************************************
    ** Getter for details
    *******************************************************************************/
   public String getDetails()
   {
      return (this.details);
   }



   /*******************************************************************************
    ** Setter for details
    *******************************************************************************/
   public void setDetails(String details)
   {
      this.details = details;
   }



   /*******************************************************************************
    ** Fluent setter for details
    *******************************************************************************/
   public AlertsAlert withDetails(String details)
   {
      this.details = details;
      return (this);
   }



   /*******************************************************************************
    ** Getter for subjectTableName
    *******************************************************************************/
   public String getSubjectTableName()
   {
      return (this.subjectTableName);
   }



   /*******************************************************************************
    ** Setter for subjectTableName
    *******************************************************************************/
   public void setSubjectTableName(String subjectTableName)
   {
      this.subjectTableName = subjectTableName;
   }



   /*******************************************************************************
    ** Fluent setter for subjectTableName
    *******************************************************************************/
   public AlertsAlert withSubjectTableName(String subjectTableName)
   {
      this.subjectTableName = subjectTableName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for subjectRecordId
    *******************************************************************************/
   public String getSubjectRecordId()
   {
      return (this.subjectRecordId);
   }



   /*******************************************************************************
    ** Setter for subjectRecordId
    *******************************************************************************/
   public void setSubjectRecordId(String subjectRecordId)
   {
      this.subjectRecordId = subjectRecordId;
   }



   /*******************************************************************************
    ** Fluent setter for subjectRecordId
    *******************************************************************************/
   public AlertsAlert withSubjectRecordId(String subjectRecordId)
   {
      this.subjectRecordId = subjectRecordId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for recordRefCount
    *******************************************************************************/
   public Integer getRecordRefCount()
   {
      return (this.recordRefCount);
   }



   /*******************************************************************************
    ** Setter for recordRefCount
    *******************************************************************************/
   public void setRecordRefCount(Integer recordRefCount)
   {
      this.recordRefCount = recordRefCount;
   }



   /*******************************************************************************
    ** Fluent setter for recordRefCount
    *******************************************************************************/
   public AlertsAlert withRecordRefCount(Integer recordRefCount)
   {
      this.recordRefCount = recordRefCount;
      return (this);
   }



   /*******************************************************************************
    ** Getter for itemCount
    *******************************************************************************/
   public Integer getItemCount()
   {
      return (this.itemCount);
   }



   /*******************************************************************************
    ** Setter for itemCount
    *******************************************************************************/
   public void setItemCount(Integer itemCount)
   {
      this.itemCount = itemCount;
   }



   /*******************************************************************************
    ** Fluent setter for itemCount
    *******************************************************************************/
   public AlertsAlert withItemCount(Integer itemCount)
   {
      this.itemCount = itemCount;
      return (this);
   }



   /*******************************************************************************
    ** Getter for links
    *******************************************************************************/
   public String getLinks()
   {
      return (this.links);
   }



   /*******************************************************************************
    ** Setter for links
    *******************************************************************************/
   public void setLinks(String links)
   {
      this.links = links;
   }



   /*******************************************************************************
    ** Fluent setter for links
    *******************************************************************************/
   public AlertsAlert withLinks(String links)
   {
      this.links = links;
      return (this);
   }



   /*******************************************************************************
    ** Getter for attributes
    *******************************************************************************/
   public String getAttributes()
   {
      return (this.attributes);
   }



   /*******************************************************************************
    ** Setter for attributes
    *******************************************************************************/
   public void setAttributes(String attributes)
   {
      this.attributes = attributes;
   }



   /*******************************************************************************
    ** Fluent setter for attributes
    *******************************************************************************/
   public AlertsAlert withAttributes(String attributes)
   {
      this.attributes = attributes;
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
   public AlertsAlert withSourceName(String sourceName)
   {
      this.sourceName = sourceName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for visibilityPermission
    *******************************************************************************/
   public String getVisibilityPermission()
   {
      return (this.visibilityPermission);
   }



   /*******************************************************************************
    ** Setter for visibilityPermission
    *******************************************************************************/
   public void setVisibilityPermission(String visibilityPermission)
   {
      this.visibilityPermission = visibilityPermission;
   }



   /*******************************************************************************
    ** Fluent setter for visibilityPermission
    *******************************************************************************/
   public AlertsAlert withVisibilityPermission(String visibilityPermission)
   {
      this.visibilityPermission = visibilityPermission;
      return (this);
   }



   /*******************************************************************************
    ** Getter for firstRaisedAt
    *******************************************************************************/
   public Instant getFirstRaisedAt()
   {
      return (this.firstRaisedAt);
   }



   /*******************************************************************************
    ** Setter for firstRaisedAt
    *******************************************************************************/
   public void setFirstRaisedAt(Instant firstRaisedAt)
   {
      this.firstRaisedAt = firstRaisedAt;
   }



   /*******************************************************************************
    ** Fluent setter for firstRaisedAt
    *******************************************************************************/
   public AlertsAlert withFirstRaisedAt(Instant firstRaisedAt)
   {
      this.firstRaisedAt = firstRaisedAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastRaisedAt
    *******************************************************************************/
   public Instant getLastRaisedAt()
   {
      return (this.lastRaisedAt);
   }



   /*******************************************************************************
    ** Setter for lastRaisedAt
    *******************************************************************************/
   public void setLastRaisedAt(Instant lastRaisedAt)
   {
      this.lastRaisedAt = lastRaisedAt;
   }



   /*******************************************************************************
    ** Fluent setter for lastRaisedAt
    *******************************************************************************/
   public AlertsAlert withLastRaisedAt(Instant lastRaisedAt)
   {
      this.lastRaisedAt = lastRaisedAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for occurrenceCount
    *******************************************************************************/
   public Integer getOccurrenceCount()
   {
      return (this.occurrenceCount);
   }



   /*******************************************************************************
    ** Setter for occurrenceCount
    *******************************************************************************/
   public void setOccurrenceCount(Integer occurrenceCount)
   {
      this.occurrenceCount = occurrenceCount;
   }



   /*******************************************************************************
    ** Fluent setter for occurrenceCount
    *******************************************************************************/
   public AlertsAlert withOccurrenceCount(Integer occurrenceCount)
   {
      this.occurrenceCount = occurrenceCount;
      return (this);
   }



   /*******************************************************************************
    ** Getter for reopenCount
    *******************************************************************************/
   public Integer getReopenCount()
   {
      return (this.reopenCount);
   }



   /*******************************************************************************
    ** Setter for reopenCount
    *******************************************************************************/
   public void setReopenCount(Integer reopenCount)
   {
      this.reopenCount = reopenCount;
   }



   /*******************************************************************************
    ** Fluent setter for reopenCount
    *******************************************************************************/
   public AlertsAlert withReopenCount(Integer reopenCount)
   {
      this.reopenCount = reopenCount;
      return (this);
   }



   /*******************************************************************************
    ** Getter for flapping
    *******************************************************************************/
   public Boolean getFlapping()
   {
      return (this.flapping);
   }



   /*******************************************************************************
    ** Setter for flapping
    *******************************************************************************/
   public void setFlapping(Boolean flapping)
   {
      this.flapping = flapping;
   }



   /*******************************************************************************
    ** Fluent setter for flapping
    *******************************************************************************/
   public AlertsAlert withFlapping(Boolean flapping)
   {
      this.flapping = flapping;
      return (this);
   }



   /*******************************************************************************
    ** Getter for acknowledgedByUserId
    *******************************************************************************/
   public String getAcknowledgedByUserId()
   {
      return (this.acknowledgedByUserId);
   }



   /*******************************************************************************
    ** Setter for acknowledgedByUserId
    *******************************************************************************/
   public void setAcknowledgedByUserId(String acknowledgedByUserId)
   {
      this.acknowledgedByUserId = acknowledgedByUserId;
   }



   /*******************************************************************************
    ** Fluent setter for acknowledgedByUserId
    *******************************************************************************/
   public AlertsAlert withAcknowledgedByUserId(String acknowledgedByUserId)
   {
      this.acknowledgedByUserId = acknowledgedByUserId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for acknowledgedAt
    *******************************************************************************/
   public Instant getAcknowledgedAt()
   {
      return (this.acknowledgedAt);
   }



   /*******************************************************************************
    ** Setter for acknowledgedAt
    *******************************************************************************/
   public void setAcknowledgedAt(Instant acknowledgedAt)
   {
      this.acknowledgedAt = acknowledgedAt;
   }



   /*******************************************************************************
    ** Fluent setter for acknowledgedAt
    *******************************************************************************/
   public AlertsAlert withAcknowledgedAt(Instant acknowledgedAt)
   {
      this.acknowledgedAt = acknowledgedAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for snoozedUntil
    *******************************************************************************/
   public Instant getSnoozedUntil()
   {
      return (this.snoozedUntil);
   }



   /*******************************************************************************
    ** Setter for snoozedUntil
    *******************************************************************************/
   public void setSnoozedUntil(Instant snoozedUntil)
   {
      this.snoozedUntil = snoozedUntil;
   }



   /*******************************************************************************
    ** Fluent setter for snoozedUntil
    *******************************************************************************/
   public AlertsAlert withSnoozedUntil(Instant snoozedUntil)
   {
      this.snoozedUntil = snoozedUntil;
      return (this);
   }



   /*******************************************************************************
    ** Getter for assignedUserId
    *******************************************************************************/
   public String getAssignedUserId()
   {
      return (this.assignedUserId);
   }



   /*******************************************************************************
    ** Setter for assignedUserId
    *******************************************************************************/
   public void setAssignedUserId(String assignedUserId)
   {
      this.assignedUserId = assignedUserId;
   }



   /*******************************************************************************
    ** Fluent setter for assignedUserId
    *******************************************************************************/
   public AlertsAlert withAssignedUserId(String assignedUserId)
   {
      this.assignedUserId = assignedUserId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for assignedTeamId
    *******************************************************************************/
   public Integer getAssignedTeamId()
   {
      return (this.assignedTeamId);
   }



   /*******************************************************************************
    ** Setter for assignedTeamId
    *******************************************************************************/
   public void setAssignedTeamId(Integer assignedTeamId)
   {
      this.assignedTeamId = assignedTeamId;
   }



   /*******************************************************************************
    ** Fluent setter for assignedTeamId
    *******************************************************************************/
   public AlertsAlert withAssignedTeamId(Integer assignedTeamId)
   {
      this.assignedTeamId = assignedTeamId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for resolvedAt
    *******************************************************************************/
   public Instant getResolvedAt()
   {
      return (this.resolvedAt);
   }



   /*******************************************************************************
    ** Setter for resolvedAt
    *******************************************************************************/
   public void setResolvedAt(Instant resolvedAt)
   {
      this.resolvedAt = resolvedAt;
   }



   /*******************************************************************************
    ** Fluent setter for resolvedAt
    *******************************************************************************/
   public AlertsAlert withResolvedAt(Instant resolvedAt)
   {
      this.resolvedAt = resolvedAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for resolvedByUserId
    *******************************************************************************/
   public String getResolvedByUserId()
   {
      return (this.resolvedByUserId);
   }



   /*******************************************************************************
    ** Setter for resolvedByUserId
    *******************************************************************************/
   public void setResolvedByUserId(String resolvedByUserId)
   {
      this.resolvedByUserId = resolvedByUserId;
   }



   /*******************************************************************************
    ** Fluent setter for resolvedByUserId
    *******************************************************************************/
   public AlertsAlert withResolvedByUserId(String resolvedByUserId)
   {
      this.resolvedByUserId = resolvedByUserId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for resolution
    *******************************************************************************/
   public String getResolution()
   {
      return (this.resolution);
   }



   /*******************************************************************************
    ** Setter for resolution
    *******************************************************************************/
   public void setResolution(String resolution)
   {
      this.resolution = resolution;
   }



   /*******************************************************************************
    ** Fluent setter for resolution
    *******************************************************************************/
   public AlertsAlert withResolution(String resolution)
   {
      this.resolution = resolution;
      return (this);
   }



   /*******************************************************************************
    ** Getter for resolutionNote
    *******************************************************************************/
   public String getResolutionNote()
   {
      return (this.resolutionNote);
   }



   /*******************************************************************************
    ** Setter for resolutionNote
    *******************************************************************************/
   public void setResolutionNote(String resolutionNote)
   {
      this.resolutionNote = resolutionNote;
   }



   /*******************************************************************************
    ** Fluent setter for resolutionNote
    *******************************************************************************/
   public AlertsAlert withResolutionNote(String resolutionNote)
   {
      this.resolutionNote = resolutionNote;
      return (this);
   }



   /*******************************************************************************
    ** Getter for muted
    *******************************************************************************/
   public Boolean getMuted()
   {
      return (this.muted);
   }



   /*******************************************************************************
    ** Setter for muted
    *******************************************************************************/
   public void setMuted(Boolean muted)
   {
      this.muted = muted;
   }



   /*******************************************************************************
    ** Fluent setter for muted
    *******************************************************************************/
   public AlertsAlert withMuted(Boolean muted)
   {
      this.muted = muted;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastNotifiedAt
    *******************************************************************************/
   public Instant getLastNotifiedAt()
   {
      return (this.lastNotifiedAt);
   }



   /*******************************************************************************
    ** Setter for lastNotifiedAt
    *******************************************************************************/
   public void setLastNotifiedAt(Instant lastNotifiedAt)
   {
      this.lastNotifiedAt = lastNotifiedAt;
   }



   /*******************************************************************************
    ** Fluent setter for lastNotifiedAt
    *******************************************************************************/
   public AlertsAlert withLastNotifiedAt(Instant lastNotifiedAt)
   {
      this.lastNotifiedAt = lastNotifiedAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for escalationLevel
    *******************************************************************************/
   public Integer getEscalationLevel()
   {
      return (this.escalationLevel);
   }



   /*******************************************************************************
    ** Setter for escalationLevel
    *******************************************************************************/
   public void setEscalationLevel(Integer escalationLevel)
   {
      this.escalationLevel = escalationLevel;
   }



   /*******************************************************************************
    ** Fluent setter for escalationLevel
    *******************************************************************************/
   public AlertsAlert withEscalationLevel(Integer escalationLevel)
   {
      this.escalationLevel = escalationLevel;
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
   public AlertsAlert withCreateDate(Instant createDate)
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
   public AlertsAlert withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }
}
