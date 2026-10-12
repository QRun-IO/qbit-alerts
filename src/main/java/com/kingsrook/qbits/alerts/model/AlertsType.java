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
 ** A kind of problem a source can report (e.g. cdn.purge.dead), synced from
 ** QAlertTypeMetaData at startup. Metadata fields are refreshed at boot; the
 ** admin fields (enabled, severityOverride, groupId, notify) are kept.
 **
 ** Generated from the qbit-alerts schema (one definition for the entity,
 ** the Liquibase changelog and the test DDL).
 *******************************************************************************/
public class AlertsType extends QRecordEntity
{
   public static final String TABLE_NAME = "alerts_type";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true, isEditable = false, maxLength = 200, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String name;

   @QField(maxLength = 200, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String label;

   @QField(isEditable = false, maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, label = "Source")
   private String sourceName;

   @QField(possibleValueSourceName = "alerts_group", label = "Group")
   private Integer groupId;

   @QField(isEditable = false, maxLength = 16, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, possibleValueSourceName = "alerts_severity")
   private String defaultSeverity;

   @QField(maxLength = 16, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, possibleValueSourceName = "alerts_severity")
   private String severityOverride;

   @QField()
   private Boolean enabled;

   @QField(isEditable = false)
   private Boolean installed;

   @QField()
   private Boolean notify;

   @QField()
   private String description;

   @QField(label = "How to Fix")
   private String howToFix;

   @QField(isEditable = false, maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, label = "Subject Table")
   private String subjectTableName;

   @QField(isEditable = false, maxLength = 200, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String visibilityPermission;

   @QField(maxLength = 20, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, possibleValueSourceName = "alerts_resolve_mode")
   private String resolveMode;

   @QField()
   private Integer quietPeriodMinutes;

   @QField(maxLength = 16, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, possibleValueSourceName = "alerts_detail_level")
   private String externalDetailCeiling;

   @QField()
   private Integer reminderIntervalMinutes;

   @QField(isEditable = false)
   private Instant lastRaisedAt;

   @QField(isEditable = false)
   private Integer openCount;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AlertsType()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AlertsType(QRecord record)
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
   public AlertsType withId(Integer id)
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
   public AlertsType withName(String name)
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
   public AlertsType withLabel(String label)
   {
      this.label = label;
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
   public AlertsType withSourceName(String sourceName)
   {
      this.sourceName = sourceName;
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
   public AlertsType withGroupId(Integer groupId)
   {
      this.groupId = groupId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for defaultSeverity
    *******************************************************************************/
   public String getDefaultSeverity()
   {
      return (this.defaultSeverity);
   }



   /*******************************************************************************
    ** Setter for defaultSeverity
    *******************************************************************************/
   public void setDefaultSeverity(String defaultSeverity)
   {
      this.defaultSeverity = defaultSeverity;
   }



   /*******************************************************************************
    ** Fluent setter for defaultSeverity
    *******************************************************************************/
   public AlertsType withDefaultSeverity(String defaultSeverity)
   {
      this.defaultSeverity = defaultSeverity;
      return (this);
   }



   /*******************************************************************************
    ** Getter for severityOverride
    *******************************************************************************/
   public String getSeverityOverride()
   {
      return (this.severityOverride);
   }



   /*******************************************************************************
    ** Setter for severityOverride
    *******************************************************************************/
   public void setSeverityOverride(String severityOverride)
   {
      this.severityOverride = severityOverride;
   }



   /*******************************************************************************
    ** Fluent setter for severityOverride
    *******************************************************************************/
   public AlertsType withSeverityOverride(String severityOverride)
   {
      this.severityOverride = severityOverride;
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
   public AlertsType withEnabled(Boolean enabled)
   {
      this.enabled = enabled;
      return (this);
   }



   /*******************************************************************************
    ** Getter for installed
    *******************************************************************************/
   public Boolean getInstalled()
   {
      return (this.installed);
   }



   /*******************************************************************************
    ** Setter for installed
    *******************************************************************************/
   public void setInstalled(Boolean installed)
   {
      this.installed = installed;
   }



   /*******************************************************************************
    ** Fluent setter for installed
    *******************************************************************************/
   public AlertsType withInstalled(Boolean installed)
   {
      this.installed = installed;
      return (this);
   }



   /*******************************************************************************
    ** Getter for notify
    *******************************************************************************/
   public Boolean getNotify()
   {
      return (this.notify);
   }



   /*******************************************************************************
    ** Setter for notify
    *******************************************************************************/
   public void setNotify(Boolean notify)
   {
      this.notify = notify;
   }



   /*******************************************************************************
    ** Fluent setter for notify
    *******************************************************************************/
   public AlertsType withNotify(Boolean notify)
   {
      this.notify = notify;
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
   public AlertsType withDescription(String description)
   {
      this.description = description;
      return (this);
   }



   /*******************************************************************************
    ** Getter for howToFix
    *******************************************************************************/
   public String getHowToFix()
   {
      return (this.howToFix);
   }



   /*******************************************************************************
    ** Setter for howToFix
    *******************************************************************************/
   public void setHowToFix(String howToFix)
   {
      this.howToFix = howToFix;
   }



   /*******************************************************************************
    ** Fluent setter for howToFix
    *******************************************************************************/
   public AlertsType withHowToFix(String howToFix)
   {
      this.howToFix = howToFix;
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
   public AlertsType withSubjectTableName(String subjectTableName)
   {
      this.subjectTableName = subjectTableName;
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
   public AlertsType withVisibilityPermission(String visibilityPermission)
   {
      this.visibilityPermission = visibilityPermission;
      return (this);
   }



   /*******************************************************************************
    ** Getter for resolveMode
    *******************************************************************************/
   public String getResolveMode()
   {
      return (this.resolveMode);
   }



   /*******************************************************************************
    ** Setter for resolveMode
    *******************************************************************************/
   public void setResolveMode(String resolveMode)
   {
      this.resolveMode = resolveMode;
   }



   /*******************************************************************************
    ** Fluent setter for resolveMode
    *******************************************************************************/
   public AlertsType withResolveMode(String resolveMode)
   {
      this.resolveMode = resolveMode;
      return (this);
   }



   /*******************************************************************************
    ** Getter for quietPeriodMinutes
    *******************************************************************************/
   public Integer getQuietPeriodMinutes()
   {
      return (this.quietPeriodMinutes);
   }



   /*******************************************************************************
    ** Setter for quietPeriodMinutes
    *******************************************************************************/
   public void setQuietPeriodMinutes(Integer quietPeriodMinutes)
   {
      this.quietPeriodMinutes = quietPeriodMinutes;
   }



   /*******************************************************************************
    ** Fluent setter for quietPeriodMinutes
    *******************************************************************************/
   public AlertsType withQuietPeriodMinutes(Integer quietPeriodMinutes)
   {
      this.quietPeriodMinutes = quietPeriodMinutes;
      return (this);
   }



   /*******************************************************************************
    ** Getter for externalDetailCeiling
    *******************************************************************************/
   public String getExternalDetailCeiling()
   {
      return (this.externalDetailCeiling);
   }



   /*******************************************************************************
    ** Setter for externalDetailCeiling
    *******************************************************************************/
   public void setExternalDetailCeiling(String externalDetailCeiling)
   {
      this.externalDetailCeiling = externalDetailCeiling;
   }



   /*******************************************************************************
    ** Fluent setter for externalDetailCeiling
    *******************************************************************************/
   public AlertsType withExternalDetailCeiling(String externalDetailCeiling)
   {
      this.externalDetailCeiling = externalDetailCeiling;
      return (this);
   }



   /*******************************************************************************
    ** Getter for reminderIntervalMinutes
    *******************************************************************************/
   public Integer getReminderIntervalMinutes()
   {
      return (this.reminderIntervalMinutes);
   }



   /*******************************************************************************
    ** Setter for reminderIntervalMinutes
    *******************************************************************************/
   public void setReminderIntervalMinutes(Integer reminderIntervalMinutes)
   {
      this.reminderIntervalMinutes = reminderIntervalMinutes;
   }



   /*******************************************************************************
    ** Fluent setter for reminderIntervalMinutes
    *******************************************************************************/
   public AlertsType withReminderIntervalMinutes(Integer reminderIntervalMinutes)
   {
      this.reminderIntervalMinutes = reminderIntervalMinutes;
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
   public AlertsType withLastRaisedAt(Instant lastRaisedAt)
   {
      this.lastRaisedAt = lastRaisedAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for openCount
    *******************************************************************************/
   public Integer getOpenCount()
   {
      return (this.openCount);
   }



   /*******************************************************************************
    ** Setter for openCount
    *******************************************************************************/
   public void setOpenCount(Integer openCount)
   {
      this.openCount = openCount;
   }



   /*******************************************************************************
    ** Fluent setter for openCount
    *******************************************************************************/
   public AlertsType withOpenCount(Integer openCount)
   {
      this.openCount = openCount;
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
   public AlertsType withCreateDate(Instant createDate)
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
   public AlertsType withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }
}
