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
 ** Timed notification steps for an unacknowledged alert.
 *******************************************************************************/
public class AlertsEscalationPolicy extends QRecordEntity
{
   public static final String TABLE_NAME = "alerts_escalation_policy";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true, maxLength = 100)
   private String name;

   @QField()
   private String description;

   @QField()
   private Integer repeatAfterMinutes;

   @QField()
   private Integer maxRepeats;

   @QField()
   private Boolean enabled;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AlertsEscalationPolicy()
   {
   }



   /*******************************************************************************
    ** Populate from a QRecord.
    *******************************************************************************/
   public AlertsEscalationPolicy(QRecord record)
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
   public AlertsEscalationPolicy withId(Integer id)
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
   public AlertsEscalationPolicy withName(String name)
   {
      this.name = name;
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
   public AlertsEscalationPolicy withDescription(String description)
   {
      this.description = description;
      return (this);
   }



   /*******************************************************************************
    ** Getter for repeatAfterMinutes
    *******************************************************************************/
   public Integer getRepeatAfterMinutes()
   {
      return (this.repeatAfterMinutes);
   }



   /*******************************************************************************
    ** Setter for repeatAfterMinutes
    *******************************************************************************/
   public void setRepeatAfterMinutes(Integer repeatAfterMinutes)
   {
      this.repeatAfterMinutes = repeatAfterMinutes;
   }



   /*******************************************************************************
    ** Fluent setter for repeatAfterMinutes
    *******************************************************************************/
   public AlertsEscalationPolicy withRepeatAfterMinutes(Integer repeatAfterMinutes)
   {
      this.repeatAfterMinutes = repeatAfterMinutes;
      return (this);
   }



   /*******************************************************************************
    ** Getter for maxRepeats
    *******************************************************************************/
   public Integer getMaxRepeats()
   {
      return (this.maxRepeats);
   }



   /*******************************************************************************
    ** Setter for maxRepeats
    *******************************************************************************/
   public void setMaxRepeats(Integer maxRepeats)
   {
      this.maxRepeats = maxRepeats;
   }



   /*******************************************************************************
    ** Fluent setter for maxRepeats
    *******************************************************************************/
   public AlertsEscalationPolicy withMaxRepeats(Integer maxRepeats)
   {
      this.maxRepeats = maxRepeats;
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
   public AlertsEscalationPolicy withEnabled(Boolean enabled)
   {
      this.enabled = enabled;
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
   public AlertsEscalationPolicy withCreateDate(Instant createDate)
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
   public AlertsEscalationPolicy withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }
}
