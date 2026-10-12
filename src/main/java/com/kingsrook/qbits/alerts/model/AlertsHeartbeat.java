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
 ** The last time each dead-man's switch beat (QAlerts.heartbeat, or a process
 ** that completed). Heartbeat monitors watch these.
 **
 ** Generated from the qbit-alerts schema (one definition for the entity,
 ** the Liquibase changelog and the test DDL).
 *******************************************************************************/
public class AlertsHeartbeat extends QRecordEntity
{
   public static final String TABLE_NAME = "alerts_heartbeat";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true, isEditable = false, maxLength = 300, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String heartbeatKey;

   @QField(isEditable = false)
   private Instant lastBeatAt;

   @QField(isEditable = false)
   private Integer beatCount;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AlertsHeartbeat()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AlertsHeartbeat(QRecord record)
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
   public AlertsHeartbeat withId(Integer id)
   {
      this.id = id;
      return (this);
   }



   /*******************************************************************************
    ** Getter for heartbeatKey
    *******************************************************************************/
   public String getHeartbeatKey()
   {
      return (this.heartbeatKey);
   }



   /*******************************************************************************
    ** Setter for heartbeatKey
    *******************************************************************************/
   public void setHeartbeatKey(String heartbeatKey)
   {
      this.heartbeatKey = heartbeatKey;
   }



   /*******************************************************************************
    ** Fluent setter for heartbeatKey
    *******************************************************************************/
   public AlertsHeartbeat withHeartbeatKey(String heartbeatKey)
   {
      this.heartbeatKey = heartbeatKey;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastBeatAt
    *******************************************************************************/
   public Instant getLastBeatAt()
   {
      return (this.lastBeatAt);
   }



   /*******************************************************************************
    ** Setter for lastBeatAt
    *******************************************************************************/
   public void setLastBeatAt(Instant lastBeatAt)
   {
      this.lastBeatAt = lastBeatAt;
   }



   /*******************************************************************************
    ** Fluent setter for lastBeatAt
    *******************************************************************************/
   public AlertsHeartbeat withLastBeatAt(Instant lastBeatAt)
   {
      this.lastBeatAt = lastBeatAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for beatCount
    *******************************************************************************/
   public Integer getBeatCount()
   {
      return (this.beatCount);
   }



   /*******************************************************************************
    ** Setter for beatCount
    *******************************************************************************/
   public void setBeatCount(Integer beatCount)
   {
      this.beatCount = beatCount;
   }



   /*******************************************************************************
    ** Fluent setter for beatCount
    *******************************************************************************/
   public AlertsHeartbeat withBeatCount(Integer beatCount)
   {
      this.beatCount = beatCount;
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
   public AlertsHeartbeat withCreateDate(Instant createDate)
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
   public AlertsHeartbeat withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }
}
