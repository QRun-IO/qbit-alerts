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
 ** Who hears about what: a user, a team or a channel, scoped to all alerts, a
 ** group, a type, a monitor, a table or one record, with a minimum severity
 ** and a delivery mode.
 **
 ** Generated from the qbit-alerts schema (one definition for the entity,
 ** the Liquibase changelog and the test DDL).
 *******************************************************************************/
public class AlertsSubscription extends QRecordEntity
{
   public static final String TABLE_NAME = "alerts_subscription";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true, maxLength = 16, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, possibleValueSourceName = "alerts_subscriber_type")
   private String subscriberType;

   @QField(maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, label = "User")
   private String userId;

   @QField(possibleValueSourceName = "alerts_team", label = "Team")
   private Integer teamId;

   @QField(possibleValueSourceName = "alerts_channel", label = "Channel")
   private Integer channelId;

   @QField(isRequired = true, maxLength = 20, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, possibleValueSourceName = "alerts_scope_type")
   private String scopeType;

   @QField(maxLength = 300, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String scopeValue;

   @QField(maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, label = "Scope Record")
   private String scopeRecordId;

   @QField(maxLength = 16, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, possibleValueSourceName = "alerts_severity")
   private String minSeverity;

   @QField(maxLength = 200, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String events;

   @QField(maxLength = 20, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, possibleValueSourceName = "alerts_delivery_mode")
   private String deliveryMode;

   @QField(maxLength = 200, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, label = "Channels")
   private String channelOverrideIds;

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
   public AlertsSubscription()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AlertsSubscription(QRecord record)
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
   public AlertsSubscription withId(Integer id)
   {
      this.id = id;
      return (this);
   }



   /*******************************************************************************
    ** Getter for subscriberType
    *******************************************************************************/
   public String getSubscriberType()
   {
      return (this.subscriberType);
   }



   /*******************************************************************************
    ** Setter for subscriberType
    *******************************************************************************/
   public void setSubscriberType(String subscriberType)
   {
      this.subscriberType = subscriberType;
   }



   /*******************************************************************************
    ** Fluent setter for subscriberType
    *******************************************************************************/
   public AlertsSubscription withSubscriberType(String subscriberType)
   {
      this.subscriberType = subscriberType;
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
   public AlertsSubscription withUserId(String userId)
   {
      this.userId = userId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for teamId
    *******************************************************************************/
   public Integer getTeamId()
   {
      return (this.teamId);
   }



   /*******************************************************************************
    ** Setter for teamId
    *******************************************************************************/
   public void setTeamId(Integer teamId)
   {
      this.teamId = teamId;
   }



   /*******************************************************************************
    ** Fluent setter for teamId
    *******************************************************************************/
   public AlertsSubscription withTeamId(Integer teamId)
   {
      this.teamId = teamId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for channelId
    *******************************************************************************/
   public Integer getChannelId()
   {
      return (this.channelId);
   }



   /*******************************************************************************
    ** Setter for channelId
    *******************************************************************************/
   public void setChannelId(Integer channelId)
   {
      this.channelId = channelId;
   }



   /*******************************************************************************
    ** Fluent setter for channelId
    *******************************************************************************/
   public AlertsSubscription withChannelId(Integer channelId)
   {
      this.channelId = channelId;
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
   public AlertsSubscription withScopeType(String scopeType)
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
   public AlertsSubscription withScopeValue(String scopeValue)
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
   public AlertsSubscription withScopeRecordId(String scopeRecordId)
   {
      this.scopeRecordId = scopeRecordId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for minSeverity
    *******************************************************************************/
   public String getMinSeverity()
   {
      return (this.minSeverity);
   }



   /*******************************************************************************
    ** Setter for minSeverity
    *******************************************************************************/
   public void setMinSeverity(String minSeverity)
   {
      this.minSeverity = minSeverity;
   }



   /*******************************************************************************
    ** Fluent setter for minSeverity
    *******************************************************************************/
   public AlertsSubscription withMinSeverity(String minSeverity)
   {
      this.minSeverity = minSeverity;
      return (this);
   }



   /*******************************************************************************
    ** Getter for events
    *******************************************************************************/
   public String getEvents()
   {
      return (this.events);
   }



   /*******************************************************************************
    ** Setter for events
    *******************************************************************************/
   public void setEvents(String events)
   {
      this.events = events;
   }



   /*******************************************************************************
    ** Fluent setter for events
    *******************************************************************************/
   public AlertsSubscription withEvents(String events)
   {
      this.events = events;
      return (this);
   }



   /*******************************************************************************
    ** Getter for deliveryMode
    *******************************************************************************/
   public String getDeliveryMode()
   {
      return (this.deliveryMode);
   }



   /*******************************************************************************
    ** Setter for deliveryMode
    *******************************************************************************/
   public void setDeliveryMode(String deliveryMode)
   {
      this.deliveryMode = deliveryMode;
   }



   /*******************************************************************************
    ** Fluent setter for deliveryMode
    *******************************************************************************/
   public AlertsSubscription withDeliveryMode(String deliveryMode)
   {
      this.deliveryMode = deliveryMode;
      return (this);
   }



   /*******************************************************************************
    ** Getter for channelOverrideIds
    *******************************************************************************/
   public String getChannelOverrideIds()
   {
      return (this.channelOverrideIds);
   }



   /*******************************************************************************
    ** Setter for channelOverrideIds
    *******************************************************************************/
   public void setChannelOverrideIds(String channelOverrideIds)
   {
      this.channelOverrideIds = channelOverrideIds;
   }



   /*******************************************************************************
    ** Fluent setter for channelOverrideIds
    *******************************************************************************/
   public AlertsSubscription withChannelOverrideIds(String channelOverrideIds)
   {
      this.channelOverrideIds = channelOverrideIds;
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
   public AlertsSubscription withEnabled(Boolean enabled)
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
   public AlertsSubscription withCreatedByUserId(String createdByUserId)
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
   public AlertsSubscription withCreateDate(Instant createDate)
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
   public AlertsSubscription withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }
}
