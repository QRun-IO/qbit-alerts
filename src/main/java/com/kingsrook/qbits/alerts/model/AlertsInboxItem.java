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
 ** One alert in one user's bell.
 **
 ** Generated from the qbit-alerts schema (one definition for the entity,
 ** the Liquibase changelog and the test DDL).
 *******************************************************************************/
public class AlertsInboxItem extends QRecordEntity
{
   public static final String TABLE_NAME = "alerts_inbox_item";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true, isEditable = false, maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, label = "User")
   private String userId;

   @QField(isRequired = true, isEditable = false, possibleValueSourceName = "alerts_alert", label = "Alert")
   private Integer alertId;

   @QField(isEditable = false, label = "Notification")
   private Integer notificationId;

   @QField(isEditable = false)
   private Instant deliveredAt;

   @QField()
   private Instant readAt;

   @QField()
   private Instant archivedAt;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AlertsInboxItem()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AlertsInboxItem(QRecord record)
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
   public AlertsInboxItem withId(Integer id)
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
   public AlertsInboxItem withUserId(String userId)
   {
      this.userId = userId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for alertId
    *******************************************************************************/
   public Integer getAlertId()
   {
      return (this.alertId);
   }



   /*******************************************************************************
    ** Setter for alertId
    *******************************************************************************/
   public void setAlertId(Integer alertId)
   {
      this.alertId = alertId;
   }



   /*******************************************************************************
    ** Fluent setter for alertId
    *******************************************************************************/
   public AlertsInboxItem withAlertId(Integer alertId)
   {
      this.alertId = alertId;
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
   public AlertsInboxItem withNotificationId(Integer notificationId)
   {
      this.notificationId = notificationId;
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
   public AlertsInboxItem withDeliveredAt(Instant deliveredAt)
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
   public AlertsInboxItem withReadAt(Instant readAt)
   {
      this.readAt = readAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for archivedAt
    *******************************************************************************/
   public Instant getArchivedAt()
   {
      return (this.archivedAt);
   }



   /*******************************************************************************
    ** Setter for archivedAt
    *******************************************************************************/
   public void setArchivedAt(Instant archivedAt)
   {
      this.archivedAt = archivedAt;
   }



   /*******************************************************************************
    ** Fluent setter for archivedAt
    *******************************************************************************/
   public AlertsInboxItem withArchivedAt(Instant archivedAt)
   {
      this.archivedAt = archivedAt;
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
   public AlertsInboxItem withCreateDate(Instant createDate)
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
   public AlertsInboxItem withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }
}
