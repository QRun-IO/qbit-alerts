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
 ** One notification for one user: when it reached them, and whether they read it, dismissed it, or were emailed.
 **
 ** Generated from the qbit-alerts schema (one definition for the entity,
 ** the Liquibase changelog and the test DDL).
 *******************************************************************************/
public class AlertsNotificationRecipient extends QRecordEntity
{
   public static final String TABLE_NAME = "alerts_notification_recipient";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true, isEditable = false, possibleValueSourceName = "alerts_notification", label = "Notification")
   private Integer notificationId;

   @QField(isRequired = true, isEditable = false, maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, label = "User")
   private String userId;

   @QField(isEditable = false)
   private Instant deliveredAt;

   @QField()
   private Instant readAt;

   @QField()
   private Instant dismissedAt;

   @QField(isEditable = false)
   private Instant emailedAt;

   @QField(isEditable = false, maxLength = 16, possibleValueSourceName = "alerts_email_status")
   private String emailStatus;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AlertsNotificationRecipient()
   {
   }


   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AlertsNotificationRecipient(QRecord record)
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
   public AlertsNotificationRecipient withId(Integer id)
   {
      this.id = id;
      return (this);
   }


   /*******************************************************************************
    ** Getter for notificationId
    *******************************************************************************/
   public Integer getNotificationId()
   {
      return (this.notificationId);
   }


   /*******************************************************************************
    ** Setter for notificationId
    *******************************************************************************/
   public void setNotificationId(Integer notificationId)
   {
      this.notificationId = notificationId;
   }


   /*******************************************************************************
    ** Fluent setter for notificationId
    *******************************************************************************/
   public AlertsNotificationRecipient withNotificationId(Integer notificationId)
   {
      this.notificationId = notificationId;
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
   public AlertsNotificationRecipient withUserId(String userId)
   {
      this.userId = userId;
      return (this);
   }


   /*******************************************************************************
    ** Getter for deliveredAt
    *******************************************************************************/
   public Instant getDeliveredAt()
   {
      return (this.deliveredAt);
   }


   /*******************************************************************************
    ** Setter for deliveredAt
    *******************************************************************************/
   public void setDeliveredAt(Instant deliveredAt)
   {
      this.deliveredAt = deliveredAt;
   }


   /*******************************************************************************
    ** Fluent setter for deliveredAt
    *******************************************************************************/
   public AlertsNotificationRecipient withDeliveredAt(Instant deliveredAt)
   {
      this.deliveredAt = deliveredAt;
      return (this);
   }


   /*******************************************************************************
    ** Getter for readAt
    *******************************************************************************/
   public Instant getReadAt()
   {
      return (this.readAt);
   }


   /*******************************************************************************
    ** Setter for readAt
    *******************************************************************************/
   public void setReadAt(Instant readAt)
   {
      this.readAt = readAt;
   }


   /*******************************************************************************
    ** Fluent setter for readAt
    *******************************************************************************/
   public AlertsNotificationRecipient withReadAt(Instant readAt)
   {
      this.readAt = readAt;
      return (this);
   }


   /*******************************************************************************
    ** Getter for dismissedAt
    *******************************************************************************/
   public Instant getDismissedAt()
   {
      return (this.dismissedAt);
   }


   /*******************************************************************************
    ** Setter for dismissedAt
    *******************************************************************************/
   public void setDismissedAt(Instant dismissedAt)
   {
      this.dismissedAt = dismissedAt;
   }


   /*******************************************************************************
    ** Fluent setter for dismissedAt
    *******************************************************************************/
   public AlertsNotificationRecipient withDismissedAt(Instant dismissedAt)
   {
      this.dismissedAt = dismissedAt;
      return (this);
   }


   /*******************************************************************************
    ** Getter for emailedAt
    *******************************************************************************/
   public Instant getEmailedAt()
   {
      return (this.emailedAt);
   }


   /*******************************************************************************
    ** Setter for emailedAt
    *******************************************************************************/
   public void setEmailedAt(Instant emailedAt)
   {
      this.emailedAt = emailedAt;
   }


   /*******************************************************************************
    ** Fluent setter for emailedAt
    *******************************************************************************/
   public AlertsNotificationRecipient withEmailedAt(Instant emailedAt)
   {
      this.emailedAt = emailedAt;
      return (this);
   }


   /*******************************************************************************
    ** Getter for emailStatus
    *******************************************************************************/
   public String getEmailStatus()
   {
      return (this.emailStatus);
   }


   /*******************************************************************************
    ** Setter for emailStatus
    *******************************************************************************/
   public void setEmailStatus(String emailStatus)
   {
      this.emailStatus = emailStatus;
   }


   /*******************************************************************************
    ** Fluent setter for emailStatus
    *******************************************************************************/
   public AlertsNotificationRecipient withEmailStatus(String emailStatus)
   {
      this.emailStatus = emailStatus;
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
   public AlertsNotificationRecipient withCreateDate(Instant createDate)
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
   public AlertsNotificationRecipient withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }
}
