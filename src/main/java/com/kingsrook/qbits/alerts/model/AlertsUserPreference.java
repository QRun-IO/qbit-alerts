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
 ** Per-user alert settings: email address, quiet hours, digest hour, time zone
 ** and a vacation pause. Created with defaults the first time a user opens
 ** Alerts.
 **
 ** Generated from the qbit-alerts schema (one definition for the entity,
 ** the Liquibase changelog and the test DDL).
 *******************************************************************************/
public class AlertsUserPreference extends QRecordEntity
{
   public static final String TABLE_NAME = "alerts_user_preference";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true, isEditable = false, maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, label = "User")
   private String userId;

   @QField(maxLength = 200, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, label = "Name")
   private String userLabel;

   @QField(maxLength = 300, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String emailAddress;

   @QField(maxLength = 64, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String timeZone;

   @QField(maxLength = 5, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String quietHoursStart;

   @QField(maxLength = 5, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String quietHoursEnd;

   @QField(maxLength = 50, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String quietDays;

   @QField(maxLength = 16, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, possibleValueSourceName = "alerts_severity")
   private String quietBypassSeverity;

   @QField()
   private Integer digestHour;

   @QField(maxLength = 200, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, label = "Default Channels")
   private String defaultChannelIds;

   @QField()
   private Boolean emailEnabled;

   @QField(label = "In-App Enabled")
   private Boolean inAppEnabled;

   @QField()
   private Instant pausedUntil;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AlertsUserPreference()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AlertsUserPreference(QRecord record)
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
   public AlertsUserPreference withId(Integer id)
   {
      this.id = id;
      return (this);
   }



   /*******************************************************************************
    ** Getter for userId
    *******************************************************************************/
   public String getUserId()
   {
      return (this.userId);
   }



   /*******************************************************************************
    ** Setter for userId
    *******************************************************************************/
   public void setUserId(String userId)
   {
      this.userId = userId;
   }



   /*******************************************************************************
    ** Fluent setter for userId
    *******************************************************************************/
   public AlertsUserPreference withUserId(String userId)
   {
      this.userId = userId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for userLabel
    *******************************************************************************/
   public String getUserLabel()
   {
      return (this.userLabel);
   }



   /*******************************************************************************
    ** Setter for userLabel
    *******************************************************************************/
   public void setUserLabel(String userLabel)
   {
      this.userLabel = userLabel;
   }



   /*******************************************************************************
    ** Fluent setter for userLabel
    *******************************************************************************/
   public AlertsUserPreference withUserLabel(String userLabel)
   {
      this.userLabel = userLabel;
      return (this);
   }



   /*******************************************************************************
    ** Getter for emailAddress
    *******************************************************************************/
   public String getEmailAddress()
   {
      return (this.emailAddress);
   }



   /*******************************************************************************
    ** Setter for emailAddress
    *******************************************************************************/
   public void setEmailAddress(String emailAddress)
   {
      this.emailAddress = emailAddress;
   }



   /*******************************************************************************
    ** Fluent setter for emailAddress
    *******************************************************************************/
   public AlertsUserPreference withEmailAddress(String emailAddress)
   {
      this.emailAddress = emailAddress;
      return (this);
   }



   /*******************************************************************************
    ** Getter for timeZone
    *******************************************************************************/
   public String getTimeZone()
   {
      return (this.timeZone);
   }



   /*******************************************************************************
    ** Setter for timeZone
    *******************************************************************************/
   public void setTimeZone(String timeZone)
   {
      this.timeZone = timeZone;
   }



   /*******************************************************************************
    ** Fluent setter for timeZone
    *******************************************************************************/
   public AlertsUserPreference withTimeZone(String timeZone)
   {
      this.timeZone = timeZone;
      return (this);
   }



   /*******************************************************************************
    ** Getter for quietHoursStart
    *******************************************************************************/
   public String getQuietHoursStart()
   {
      return (this.quietHoursStart);
   }



   /*******************************************************************************
    ** Setter for quietHoursStart
    *******************************************************************************/
   public void setQuietHoursStart(String quietHoursStart)
   {
      this.quietHoursStart = quietHoursStart;
   }



   /*******************************************************************************
    ** Fluent setter for quietHoursStart
    *******************************************************************************/
   public AlertsUserPreference withQuietHoursStart(String quietHoursStart)
   {
      this.quietHoursStart = quietHoursStart;
      return (this);
   }



   /*******************************************************************************
    ** Getter for quietHoursEnd
    *******************************************************************************/
   public String getQuietHoursEnd()
   {
      return (this.quietHoursEnd);
   }



   /*******************************************************************************
    ** Setter for quietHoursEnd
    *******************************************************************************/
   public void setQuietHoursEnd(String quietHoursEnd)
   {
      this.quietHoursEnd = quietHoursEnd;
   }



   /*******************************************************************************
    ** Fluent setter for quietHoursEnd
    *******************************************************************************/
   public AlertsUserPreference withQuietHoursEnd(String quietHoursEnd)
   {
      this.quietHoursEnd = quietHoursEnd;
      return (this);
   }



   /*******************************************************************************
    ** Getter for quietDays
    *******************************************************************************/
   public String getQuietDays()
   {
      return (this.quietDays);
   }



   /*******************************************************************************
    ** Setter for quietDays
    *******************************************************************************/
   public void setQuietDays(String quietDays)
   {
      this.quietDays = quietDays;
   }



   /*******************************************************************************
    ** Fluent setter for quietDays
    *******************************************************************************/
   public AlertsUserPreference withQuietDays(String quietDays)
   {
      this.quietDays = quietDays;
      return (this);
   }



   /*******************************************************************************
    ** Getter for quietBypassSeverity
    *******************************************************************************/
   public String getQuietBypassSeverity()
   {
      return (this.quietBypassSeverity);
   }



   /*******************************************************************************
    ** Setter for quietBypassSeverity
    *******************************************************************************/
   public void setQuietBypassSeverity(String quietBypassSeverity)
   {
      this.quietBypassSeverity = quietBypassSeverity;
   }



   /*******************************************************************************
    ** Fluent setter for quietBypassSeverity
    *******************************************************************************/
   public AlertsUserPreference withQuietBypassSeverity(String quietBypassSeverity)
   {
      this.quietBypassSeverity = quietBypassSeverity;
      return (this);
   }



   /*******************************************************************************
    ** Getter for digestHour
    *******************************************************************************/
   public Integer getDigestHour()
   {
      return (this.digestHour);
   }



   /*******************************************************************************
    ** Setter for digestHour
    *******************************************************************************/
   public void setDigestHour(Integer digestHour)
   {
      this.digestHour = digestHour;
   }



   /*******************************************************************************
    ** Fluent setter for digestHour
    *******************************************************************************/
   public AlertsUserPreference withDigestHour(Integer digestHour)
   {
      this.digestHour = digestHour;
      return (this);
   }



   /*******************************************************************************
    ** Getter for defaultChannelIds
    *******************************************************************************/
   public String getDefaultChannelIds()
   {
      return (this.defaultChannelIds);
   }



   /*******************************************************************************
    ** Setter for defaultChannelIds
    *******************************************************************************/
   public void setDefaultChannelIds(String defaultChannelIds)
   {
      this.defaultChannelIds = defaultChannelIds;
   }



   /*******************************************************************************
    ** Fluent setter for defaultChannelIds
    *******************************************************************************/
   public AlertsUserPreference withDefaultChannelIds(String defaultChannelIds)
   {
      this.defaultChannelIds = defaultChannelIds;
      return (this);
   }



   /*******************************************************************************
    ** Getter for emailEnabled
    *******************************************************************************/
   public Boolean getEmailEnabled()
   {
      return (this.emailEnabled);
   }



   /*******************************************************************************
    ** Setter for emailEnabled
    *******************************************************************************/
   public void setEmailEnabled(Boolean emailEnabled)
   {
      this.emailEnabled = emailEnabled;
   }



   /*******************************************************************************
    ** Fluent setter for emailEnabled
    *******************************************************************************/
   public AlertsUserPreference withEmailEnabled(Boolean emailEnabled)
   {
      this.emailEnabled = emailEnabled;
      return (this);
   }



   /*******************************************************************************
    ** Getter for inAppEnabled
    *******************************************************************************/
   public Boolean getInAppEnabled()
   {
      return (this.inAppEnabled);
   }



   /*******************************************************************************
    ** Setter for inAppEnabled
    *******************************************************************************/
   public void setInAppEnabled(Boolean inAppEnabled)
   {
      this.inAppEnabled = inAppEnabled;
   }



   /*******************************************************************************
    ** Fluent setter for inAppEnabled
    *******************************************************************************/
   public AlertsUserPreference withInAppEnabled(Boolean inAppEnabled)
   {
      this.inAppEnabled = inAppEnabled;
      return (this);
   }



   /*******************************************************************************
    ** Getter for pausedUntil
    *******************************************************************************/
   public Instant getPausedUntil()
   {
      return (this.pausedUntil);
   }



   /*******************************************************************************
    ** Setter for pausedUntil
    *******************************************************************************/
   public void setPausedUntil(Instant pausedUntil)
   {
      this.pausedUntil = pausedUntil;
   }



   /*******************************************************************************
    ** Fluent setter for pausedUntil
    *******************************************************************************/
   public AlertsUserPreference withPausedUntil(Instant pausedUntil)
   {
      this.pausedUntil = pausedUntil;
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
   public AlertsUserPreference withCreateDate(Instant createDate)
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
   public AlertsUserPreference withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }
}
