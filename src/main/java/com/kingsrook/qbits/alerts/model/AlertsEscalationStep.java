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


/*******************************************************************************
 ** One ordered destination in an escalation policy.
 *******************************************************************************/
public class AlertsEscalationStep extends QRecordEntity
{
   public static final String TABLE_NAME = "alerts_escalation_step";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true, possibleValueSourceName = "alerts_escalation_policy")
   private Integer policyId;

   @QField(isRequired = true)
   private Integer stepOrder;

   @QField(isRequired = true)
   private Integer afterMinutes;

   @QField(isRequired = true, maxLength = 16, possibleValueSourceName = "alerts_subscriber_type")
   private String targetType;

   @QField(maxLength = 100)
   private String userId;

   @QField(possibleValueSourceName = "alerts_team")
   private Integer teamId;

   @QField(possibleValueSourceName = "alerts_channel")
   private Integer channelId;

   @QField(isRequired = true, maxLength = 16, possibleValueSourceName = "alerts_status")
   private String onlyIfStatus;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AlertsEscalationStep()
   {
   }



   /*******************************************************************************
    ** Populate from a QRecord.
    *******************************************************************************/
   public AlertsEscalationStep(QRecord record)
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
   public AlertsEscalationStep withId(Integer id)
   {
      this.id = id;
      return (this);
   }



   /*******************************************************************************
    ** Getter for policyId
    *******************************************************************************/
   public Integer getPolicyId()
   {
      return (this.policyId);
   }



   /*******************************************************************************
    ** Setter for policyId
    *******************************************************************************/
   public void setPolicyId(Integer policyId)
   {
      this.policyId = policyId;
   }



   /*******************************************************************************
    ** Fluent setter for policyId
    *******************************************************************************/
   public AlertsEscalationStep withPolicyId(Integer policyId)
   {
      this.policyId = policyId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for stepOrder
    *******************************************************************************/
   public Integer getStepOrder()
   {
      return (this.stepOrder);
   }



   /*******************************************************************************
    ** Setter for stepOrder
    *******************************************************************************/
   public void setStepOrder(Integer stepOrder)
   {
      this.stepOrder = stepOrder;
   }



   /*******************************************************************************
    ** Fluent setter for stepOrder
    *******************************************************************************/
   public AlertsEscalationStep withStepOrder(Integer stepOrder)
   {
      this.stepOrder = stepOrder;
      return (this);
   }



   /*******************************************************************************
    ** Getter for afterMinutes
    *******************************************************************************/
   public Integer getAfterMinutes()
   {
      return (this.afterMinutes);
   }



   /*******************************************************************************
    ** Setter for afterMinutes
    *******************************************************************************/
   public void setAfterMinutes(Integer afterMinutes)
   {
      this.afterMinutes = afterMinutes;
   }



   /*******************************************************************************
    ** Fluent setter for afterMinutes
    *******************************************************************************/
   public AlertsEscalationStep withAfterMinutes(Integer afterMinutes)
   {
      this.afterMinutes = afterMinutes;
      return (this);
   }



   /*******************************************************************************
    ** Getter for targetType
    *******************************************************************************/
   public String getTargetType()
   {
      return (this.targetType);
   }



   /*******************************************************************************
    ** Setter for targetType
    *******************************************************************************/
   public void setTargetType(String targetType)
   {
      this.targetType = targetType;
   }



   /*******************************************************************************
    ** Fluent setter for targetType
    *******************************************************************************/
   public AlertsEscalationStep withTargetType(String targetType)
   {
      this.targetType = targetType;
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
   public AlertsEscalationStep withUserId(String userId)
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
   public AlertsEscalationStep withTeamId(Integer teamId)
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
   public AlertsEscalationStep withChannelId(Integer channelId)
   {
      this.channelId = channelId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for onlyIfStatus
    *******************************************************************************/
   public String getOnlyIfStatus()
   {
      return (this.onlyIfStatus);
   }



   /*******************************************************************************
    ** Setter for onlyIfStatus
    *******************************************************************************/
   public void setOnlyIfStatus(String onlyIfStatus)
   {
      this.onlyIfStatus = onlyIfStatus;
   }



   /*******************************************************************************
    ** Fluent setter for onlyIfStatus
    *******************************************************************************/
   public AlertsEscalationStep withOnlyIfStatus(String onlyIfStatus)
   {
      this.onlyIfStatus = onlyIfStatus;
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
   public AlertsEscalationStep withCreateDate(Instant createDate)
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
   public AlertsEscalationStep withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }
}
