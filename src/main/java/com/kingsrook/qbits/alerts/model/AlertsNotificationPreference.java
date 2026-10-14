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
 ** A user's settings for one notification category: muted in-app (never for security), and email off, immediate or in a daily digest, from a minimum severity.
 **
 ** Generated from the qbit-alerts schema (one definition for the entity,
 ** the Liquibase changelog and the test DDL).
 *******************************************************************************/
public class AlertsNotificationPreference extends QRecordEntity
{
   public static final String TABLE_NAME = "alerts_notification_preference";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true, isEditable = false, maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, label = "User")
   private String userId;

   @QField(isRequired = true, maxLength = 16, possibleValueSourceName = "alerts_notification_category")
   private String category;

   @QField(label = "Muted In-App")
   private Boolean inAppMuted;

   @QField(maxLength = 16, possibleValueSourceName = "alerts_notification_severity", label = "In-App From Severity")
   private String inAppMinSeverity;

   @QField(maxLength = 16, possibleValueSourceName = "alerts_email_mode", label = "Email")
   private String emailMode;

   @QField(maxLength = 16, possibleValueSourceName = "alerts_notification_severity", label = "Email From Severity")
   private String emailMinSeverity;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AlertsNotificationPreference()
   {
   }


   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AlertsNotificationPreference(QRecord record)
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
   public AlertsNotificationPreference withId(Integer id)
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
   public AlertsNotificationPreference withUserId(String userId)
   {
      this.userId = userId;
      return (this);
   }


   /*******************************************************************************
    ** Getter for category
    *******************************************************************************/
   public String getCategory()
   {
      return (this.category);
   }


   /*******************************************************************************
    ** Setter for category
    *******************************************************************************/
   public void setCategory(String category)
   {
      this.category = category;
   }


   /*******************************************************************************
    ** Fluent setter for category
    *******************************************************************************/
   public AlertsNotificationPreference withCategory(String category)
   {
      this.category = category;
      return (this);
   }


   /*******************************************************************************
    ** Getter for inAppMuted
    *******************************************************************************/
   public Boolean getInAppMuted()
   {
      return (this.inAppMuted);
   }


   /*******************************************************************************
    ** Setter for inAppMuted
    *******************************************************************************/
   public void setInAppMuted(Boolean inAppMuted)
   {
      this.inAppMuted = inAppMuted;
   }


   /*******************************************************************************
    ** Fluent setter for inAppMuted
    *******************************************************************************/
   public AlertsNotificationPreference withInAppMuted(Boolean inAppMuted)
   {
      this.inAppMuted = inAppMuted;
      return (this);
   }


   /*******************************************************************************
    ** Getter for emailMode
    *******************************************************************************/
   public String getEmailMode()
   {
      return (this.emailMode);
   }


   /*******************************************************************************
    ** Setter for emailMode
    *******************************************************************************/
   public void setEmailMode(String emailMode)
   {
      this.emailMode = emailMode;
   }


   /*******************************************************************************
    ** Fluent setter for emailMode
    *******************************************************************************/
   public AlertsNotificationPreference withEmailMode(String emailMode)
   {
      this.emailMode = emailMode;
      return (this);
   }


   /*******************************************************************************
    ** Getter for emailMinSeverity
    *******************************************************************************/
   public String getEmailMinSeverity()
   {
      return (this.emailMinSeverity);
   }


   /*******************************************************************************
    ** Setter for emailMinSeverity
    *******************************************************************************/
   public void setEmailMinSeverity(String emailMinSeverity)
   {
      this.emailMinSeverity = emailMinSeverity;
   }


   /*******************************************************************************
    ** Fluent setter for emailMinSeverity
    *******************************************************************************/
   public AlertsNotificationPreference withEmailMinSeverity(String emailMinSeverity)
   {
      this.emailMinSeverity = emailMinSeverity;
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
   public AlertsNotificationPreference withCreateDate(Instant createDate)
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
   public AlertsNotificationPreference withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }


   /*******************************************************************************
    ** getInAppMinSeverity
    *******************************************************************************/
   public String getInAppMinSeverity()
   {
      return (this.inAppMinSeverity);
   }


   /*******************************************************************************
    ** setInAppMinSeverity
    *******************************************************************************/
   public void setInAppMinSeverity(String inAppMinSeverity)
   {
      this.inAppMinSeverity = inAppMinSeverity;
   }


   /*******************************************************************************
    ** withInAppMinSeverity
    *******************************************************************************/
   public AlertsNotificationPreference withInAppMinSeverity(String inAppMinSeverity)
   {
      this.inAppMinSeverity = inAppMinSeverity;
      return (this);
   }
}
