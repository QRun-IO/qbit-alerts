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
 ** The alert timeline: raised, repeated, acknowledged, snoozed, resolved,
 ** reopened, notified, commented and so on. Written by the qbit; read-only.
 **
 ** Generated from the qbit-alerts schema (one definition for the entity,
 ** the Liquibase changelog and the test DDL).
 *******************************************************************************/
public class AlertsEvent extends QRecordEntity
{
   public static final String TABLE_NAME = "alerts_event";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true, isEditable = false, possibleValueSourceName = "alerts_alert", label = "Alert")
   private Integer alertId;

   @QField(isEditable = false, maxLength = 30, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, possibleValueSourceName = "alerts_event_type")
   private String eventType;

   @QField(isEditable = false)
   private Instant at;

   @QField(isEditable = false, maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, label = "User")
   private String userId;

   @QField(isEditable = false, maxLength = 200, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String fromValue;

   @QField(isEditable = false, maxLength = 200, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String toValue;

   @QField(isEditable = false, maxLength = 2000, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String note;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AlertsEvent()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AlertsEvent(QRecord record)
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
   public AlertsEvent withId(Integer id)
   {
      this.id = id;
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
   public AlertsEvent withAlertId(Integer alertId)
   {
      this.alertId = alertId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for eventType
    *******************************************************************************/
   public String getEventType()
   {
      return (this.eventType);
   }



   /*******************************************************************************
    ** Setter for eventType
    *******************************************************************************/
   public void setEventType(String eventType)
   {
      this.eventType = eventType;
   }



   /*******************************************************************************
    ** Fluent setter for eventType
    *******************************************************************************/
   public AlertsEvent withEventType(String eventType)
   {
      this.eventType = eventType;
      return (this);
   }



   /*******************************************************************************
    ** Getter for at
    *******************************************************************************/
   public Instant getAt()
   {
      return (this.at);
   }



   /*******************************************************************************
    ** Setter for at
    *******************************************************************************/
   public void setAt(Instant at)
   {
      this.at = at;
   }



   /*******************************************************************************
    ** Fluent setter for at
    *******************************************************************************/
   public AlertsEvent withAt(Instant at)
   {
      this.at = at;
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
   public AlertsEvent withUserId(String userId)
   {
      this.userId = userId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for fromValue
    *******************************************************************************/
   public String getFromValue()
   {
      return (this.fromValue);
   }



   /*******************************************************************************
    ** Setter for fromValue
    *******************************************************************************/
   public void setFromValue(String fromValue)
   {
      this.fromValue = fromValue;
   }



   /*******************************************************************************
    ** Fluent setter for fromValue
    *******************************************************************************/
   public AlertsEvent withFromValue(String fromValue)
   {
      this.fromValue = fromValue;
      return (this);
   }



   /*******************************************************************************
    ** Getter for toValue
    *******************************************************************************/
   public String getToValue()
   {
      return (this.toValue);
   }



   /*******************************************************************************
    ** Setter for toValue
    *******************************************************************************/
   public void setToValue(String toValue)
   {
      this.toValue = toValue;
   }



   /*******************************************************************************
    ** Fluent setter for toValue
    *******************************************************************************/
   public AlertsEvent withToValue(String toValue)
   {
      this.toValue = toValue;
      return (this);
   }



   /*******************************************************************************
    ** Getter for note
    *******************************************************************************/
   public String getNote()
   {
      return (this.note);
   }



   /*******************************************************************************
    ** Setter for note
    *******************************************************************************/
   public void setNote(String note)
   {
      this.note = note;
   }



   /*******************************************************************************
    ** Fluent setter for note
    *******************************************************************************/
   public AlertsEvent withNote(String note)
   {
      this.note = note;
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
   public AlertsEvent withCreateDate(Instant createDate)
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
   public AlertsEvent withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }
}
