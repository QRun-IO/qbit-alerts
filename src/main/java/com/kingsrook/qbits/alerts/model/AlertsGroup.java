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
 ** A category of alerts for people ("Backups", "CDN"), optionally nested. The
 ** Alerts home groups by these, subscriptions target them, and every alert
 ** type belongs to one.
 **
 ** Generated from the qbit-alerts schema (one definition for the entity,
 ** the Liquibase changelog and the test DDL).
 *******************************************************************************/
public class AlertsGroup extends QRecordEntity
{
   public static final String TABLE_NAME = "alerts_group";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true, maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String name;

   @QField(isRequired = true, maxLength = 200, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String label;

   @QField(possibleValueSourceName = "alerts_group", label = "Parent Group")
   private Integer parentGroupId;

   @QField(maxLength = 50, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String icon;

   @QField()
   private Integer sortOrder;

   @QField()
   private String description;

   @QField(possibleValueSourceName = "alerts_team", label = "Owner Team")
   private Integer ownerTeamId;

   @QField(possibleValueSourceName = "alerts_escalation_policy")
   private Integer escalationPolicyId;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AlertsGroup()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AlertsGroup(QRecord record)
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
   public AlertsGroup withId(Integer id)
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
   public AlertsGroup withName(String name)
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
   public AlertsGroup withLabel(String label)
   {
      this.label = label;
      return (this);
   }



   /*******************************************************************************
    ** Getter for parentGroupId
    *******************************************************************************/
   public Integer getParentGroupId()
   {
      return (this.parentGroupId);
   }



   /*******************************************************************************
    ** Setter for parentGroupId
    *******************************************************************************/
   public void setParentGroupId(Integer parentGroupId)
   {
      this.parentGroupId = parentGroupId;
   }



   /*******************************************************************************
    ** Fluent setter for parentGroupId
    *******************************************************************************/
   public AlertsGroup withParentGroupId(Integer parentGroupId)
   {
      this.parentGroupId = parentGroupId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for icon
    *******************************************************************************/
   public String getIcon()
   {
      return (this.icon);
   }



   /*******************************************************************************
    ** Setter for icon
    *******************************************************************************/
   public void setIcon(String icon)
   {
      this.icon = icon;
   }



   /*******************************************************************************
    ** Fluent setter for icon
    *******************************************************************************/
   public AlertsGroup withIcon(String icon)
   {
      this.icon = icon;
      return (this);
   }



   /*******************************************************************************
    ** Getter for sortOrder
    *******************************************************************************/
   public Integer getSortOrder()
   {
      return (this.sortOrder);
   }



   /*******************************************************************************
    ** Setter for sortOrder
    *******************************************************************************/
   public void setSortOrder(Integer sortOrder)
   {
      this.sortOrder = sortOrder;
   }



   /*******************************************************************************
    ** Fluent setter for sortOrder
    *******************************************************************************/
   public AlertsGroup withSortOrder(Integer sortOrder)
   {
      this.sortOrder = sortOrder;
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
   public AlertsGroup withDescription(String description)
   {
      this.description = description;
      return (this);
   }



   /*******************************************************************************
    ** Getter for ownerTeamId
    *******************************************************************************/
   public Integer getOwnerTeamId()
   {
      return (this.ownerTeamId);
   }



   /*******************************************************************************
    ** Setter for ownerTeamId
    *******************************************************************************/
   public void setOwnerTeamId(Integer ownerTeamId)
   {
      this.ownerTeamId = ownerTeamId;
   }



   /*******************************************************************************
    ** Fluent setter for ownerTeamId
    *******************************************************************************/
   public AlertsGroup withOwnerTeamId(Integer ownerTeamId)
   {
      this.ownerTeamId = ownerTeamId;
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
   public AlertsGroup withCreateDate(Instant createDate)
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
   public AlertsGroup withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }



   /*******************************************************************************
    ** Getter for escalationPolicyId
    *******************************************************************************/
   public Integer getEscalationPolicyId()
   {
      return (this.escalationPolicyId);
   }



   /*******************************************************************************
    ** Setter for escalationPolicyId
    *******************************************************************************/
   public void setEscalationPolicyId(Integer escalationPolicyId)
   {
      this.escalationPolicyId = escalationPolicyId;
   }



   /*******************************************************************************
    ** Fluent setter for escalationPolicyId
    *******************************************************************************/
   public AlertsGroup withEscalationPolicyId(Integer escalationPolicyId)
   {
      this.escalationPolicyId = escalationPolicyId;
      return (this);
   }
}
