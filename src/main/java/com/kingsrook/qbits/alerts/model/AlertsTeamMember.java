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
 ** One user in a team.
 **
 ** Generated from the qbit-alerts schema (one definition for the entity,
 ** the Liquibase changelog and the test DDL).
 *******************************************************************************/
public class AlertsTeamMember extends QRecordEntity
{
   public static final String TABLE_NAME = "alerts_team_member";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true, possibleValueSourceName = "alerts_team", label = "Team")
   private Integer teamId;

   @QField(isRequired = true, maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, label = "User")
   private String userId;

   @QField(maxLength = 16, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, possibleValueSourceName = "alerts_team_role")
   private String role;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AlertsTeamMember()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AlertsTeamMember(QRecord record)
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
   public AlertsTeamMember withId(Integer id)
   {
      this.id = id;
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
   public AlertsTeamMember withTeamId(Integer teamId)
   {
      this.teamId = teamId;
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
   public AlertsTeamMember withUserId(String userId)
   {
      this.userId = userId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for role
    *******************************************************************************/
   public String getRole()
   {
      return (this.role);
   }



   /*******************************************************************************
    ** Setter for role
    *******************************************************************************/
   public void setRole(String role)
   {
      this.role = role;
   }



   /*******************************************************************************
    ** Fluent setter for role
    *******************************************************************************/
   public AlertsTeamMember withRole(String role)
   {
      this.role = role;
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
   public AlertsTeamMember withCreateDate(Instant createDate)
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
   public AlertsTeamMember withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }
}
