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
 ** A record an alert is about. The label is internal: shown in-app only, to
 ** users who can see the record.
 **
 ** Generated from the qbit-alerts schema (one definition for the entity,
 ** the Liquibase changelog and the test DDL).
 *******************************************************************************/
public class AlertsRecordRef extends QRecordEntity
{
   public static final String TABLE_NAME = "alerts_record_ref";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true, isEditable = false, possibleValueSourceName = "alerts_alert", label = "Alert")
   private Integer alertId;

   @QField(isEditable = false, maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String tableName;

   @QField(isEditable = false, maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String recordId;

   @QField(isEditable = false, maxLength = 300, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String label;

   @QField(isEditable = false)
   private Boolean active;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AlertsRecordRef()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AlertsRecordRef(QRecord record)
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
   public AlertsRecordRef withId(Integer id)
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
   public AlertsRecordRef withAlertId(Integer alertId)
   {
      this.alertId = alertId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for tableName
    *******************************************************************************/
   public String getTableName()
   {
      return (this.tableName);
   }



   /*******************************************************************************
    ** Setter for tableName
    *******************************************************************************/
   public void setTableName(String tableName)
   {
      this.tableName = tableName;
   }



   /*******************************************************************************
    ** Fluent setter for tableName
    *******************************************************************************/
   public AlertsRecordRef withTableName(String tableName)
   {
      this.tableName = tableName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for recordId
    *******************************************************************************/
   public String getRecordId()
   {
      return (this.recordId);
   }



   /*******************************************************************************
    ** Setter for recordId
    *******************************************************************************/
   public void setRecordId(String recordId)
   {
      this.recordId = recordId;
   }



   /*******************************************************************************
    ** Fluent setter for recordId
    *******************************************************************************/
   public AlertsRecordRef withRecordId(String recordId)
   {
      this.recordId = recordId;
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
   public AlertsRecordRef withLabel(String label)
   {
      this.label = label;
      return (this);
   }



   /*******************************************************************************
    ** Getter for active
    *******************************************************************************/
   public Boolean getActive()
   {
      return (this.active);
   }



   /*******************************************************************************
    ** Setter for active
    *******************************************************************************/
   public void setActive(Boolean active)
   {
      this.active = active;
   }



   /*******************************************************************************
    ** Fluent setter for active
    *******************************************************************************/
   public AlertsRecordRef withActive(Boolean active)
   {
      this.active = active;
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
   public AlertsRecordRef withCreateDate(Instant createDate)
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
   public AlertsRecordRef withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }
}
