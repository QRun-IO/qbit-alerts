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
 ** A maintenance window or mute rule that silences notifications (or, for
 ** admins, suppresses alerts) for a scope and a time.
 **
 ** Generated from the qbit-alerts schema (one definition for the entity,
 ** the Liquibase changelog and the test DDL).
 *******************************************************************************/
public class AlertsMute extends QRecordEntity
{
   public static final String TABLE_NAME = "alerts_mute";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true, maxLength = 200, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String name;

   @QField(maxLength = 2000, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String reason;

   @QField(isRequired = true, maxLength = 20, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, possibleValueSourceName = "alerts_scope_type")
   private String scopeType;

   @QField(maxLength = 520, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String scopeValue;

   @QField(maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, label = "Scope Record")
   private String scopeRecordId;

   @QField(isRequired = true)
   private Instant startsAt;

   @QField(isRequired = true)
   private Instant endsAt;

   @QField(maxLength = 30, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, possibleValueSourceName = "alerts_mute_effect")
   private String effect;

   @QField()
   private Boolean enabled;

   @QField(isEditable = false, maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, label = "Created By")
   private String createdByUserId;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AlertsMute()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AlertsMute(QRecord record)
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
   public AlertsMute withId(Integer id)
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
   public AlertsMute withName(String name)
   {
      this.name = name;
      return (this);
   }



   /*******************************************************************************
    ** Getter for reason
    *******************************************************************************/
   public String getReason()
   {
      return (this.reason);
   }



   /*******************************************************************************
    ** Setter for reason
    *******************************************************************************/
   public void setReason(String reason)
   {
      this.reason = reason;
   }



   /*******************************************************************************
    ** Fluent setter for reason
    *******************************************************************************/
   public AlertsMute withReason(String reason)
   {
      this.reason = reason;
      return (this);
   }



   /*******************************************************************************
    ** Getter for scopeType
    *******************************************************************************/
   public String getScopeType()
   {
      return (this.scopeType);
   }



   /*******************************************************************************
    ** Setter for scopeType
    *******************************************************************************/
   public void setScopeType(String scopeType)
   {
      this.scopeType = scopeType;
   }



   /*******************************************************************************
    ** Fluent setter for scopeType
    *******************************************************************************/
   public AlertsMute withScopeType(String scopeType)
   {
      this.scopeType = scopeType;
      return (this);
   }



   /*******************************************************************************
    ** Getter for scopeValue
    *******************************************************************************/
   public String getScopeValue()
   {
      return (this.scopeValue);
   }



   /*******************************************************************************
    ** Setter for scopeValue
    *******************************************************************************/
   public void setScopeValue(String scopeValue)
   {
      this.scopeValue = scopeValue;
   }



   /*******************************************************************************
    ** Fluent setter for scopeValue
    *******************************************************************************/
   public AlertsMute withScopeValue(String scopeValue)
   {
      this.scopeValue = scopeValue;
      return (this);
   }



   /*******************************************************************************
    ** Getter for scopeRecordId
    *******************************************************************************/
   public String getScopeRecordId()
   {
      return (this.scopeRecordId);
   }



   /*******************************************************************************
    ** Setter for scopeRecordId
    *******************************************************************************/
   public void setScopeRecordId(String scopeRecordId)
   {
      this.scopeRecordId = scopeRecordId;
   }



   /*******************************************************************************
    ** Fluent setter for scopeRecordId
    *******************************************************************************/
   public AlertsMute withScopeRecordId(String scopeRecordId)
   {
      this.scopeRecordId = scopeRecordId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for startsAt
    *******************************************************************************/
   public Instant getStartsAt()
   {
      return (this.startsAt);
   }



   /*******************************************************************************
    ** Setter for startsAt
    *******************************************************************************/
   public void setStartsAt(Instant startsAt)
   {
      this.startsAt = startsAt;
   }



   /*******************************************************************************
    ** Fluent setter for startsAt
    *******************************************************************************/
   public AlertsMute withStartsAt(Instant startsAt)
   {
      this.startsAt = startsAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for endsAt
    *******************************************************************************/
   public Instant getEndsAt()
   {
      return (this.endsAt);
   }



   /*******************************************************************************
    ** Setter for endsAt
    *******************************************************************************/
   public void setEndsAt(Instant endsAt)
   {
      this.endsAt = endsAt;
   }



   /*******************************************************************************
    ** Fluent setter for endsAt
    *******************************************************************************/
   public AlertsMute withEndsAt(Instant endsAt)
   {
      this.endsAt = endsAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for effect
    *******************************************************************************/
   public String getEffect()
   {
      return (this.effect);
   }



   /*******************************************************************************
    ** Setter for effect
    *******************************************************************************/
   public void setEffect(String effect)
   {
      this.effect = effect;
   }



   /*******************************************************************************
    ** Fluent setter for effect
    *******************************************************************************/
   public AlertsMute withEffect(String effect)
   {
      this.effect = effect;
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
   public AlertsMute withEnabled(Boolean enabled)
   {
      this.enabled = enabled;
      return (this);
   }



   /*******************************************************************************
    ** Getter for createdByUserId
    *******************************************************************************/
   public String getCreatedByUserId()
   {
      return (this.createdByUserId);
   }



   /*******************************************************************************
    ** Setter for createdByUserId
    *******************************************************************************/
   public void setCreatedByUserId(String createdByUserId)
   {
      this.createdByUserId = createdByUserId;
   }



   /*******************************************************************************
    ** Fluent setter for createdByUserId
    *******************************************************************************/
   public AlertsMute withCreatedByUserId(String createdByUserId)
   {
      this.createdByUserId = createdByUserId;
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
   public AlertsMute withCreateDate(Instant createDate)
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
   public AlertsMute withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }
}
