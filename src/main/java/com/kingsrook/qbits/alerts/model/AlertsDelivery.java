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
 ** One delivery attempt: who, which channel, status, retries and error.
 ** Suppressed deliveries are logged too, with their reason. Written by the
 ** qbit; read-only.
 **
 ** Generated from the qbit-alerts schema (one definition for the entity,
 ** the Liquibase changelog and the test DDL).
 *******************************************************************************/
public class AlertsDelivery extends QRecordEntity
{
   public static final String TABLE_NAME = "alerts_delivery";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isEditable = false, possibleValueSourceName = "alerts_alert", label = "Alert")
   private Integer alertId;

   @QField(isEditable = false, label = "Alert Event")
   private Integer alertEventId;

   @QField(isEditable = false, possibleValueSourceName = "alerts_subscription", label = "Subscription")
   private Integer subscriptionId;

   @QField(isEditable = false, possibleValueSourceName = "alerts_channel", label = "Channel")
   private Integer channelId;

   @QField(isEditable = false, maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, label = "Recipient")
   private String recipientUserId;

   @QField(isEditable = false, maxLength = 300, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, label = "Sent To")
   private String recipientLabel;

   @QField(isEditable = false, maxLength = 16, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, possibleValueSourceName = "alerts_delivery_kind")
   private String kind;

   @QField(isEditable = false, maxLength = 16, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, possibleValueSourceName = "alerts_delivery_status")
   private String status;

   @QField(isEditable = false, maxLength = 20, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, possibleValueSourceName = "alerts_suppressed_reason")
   private String suppressedReason;

   @QField(isEditable = false)
   private Integer attempts;

   @QField(isEditable = false)
   private Instant nextAttemptAt;

   @QField(isEditable = false)
   private Instant claimedUntil;

   @QField(isEditable = false)
   private Instant sentAt;

   @QField(isEditable = false, maxLength = 300, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String providerMessageId;

   @QField(isEditable = false, maxLength = 2000, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String lastError;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AlertsDelivery()
   {
   }



   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AlertsDelivery(QRecord record)
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
   public AlertsDelivery withId(Integer id)
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
   public AlertsDelivery withAlertId(Integer alertId)
   {
      this.alertId = alertId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for alertEventId
    *******************************************************************************/
   public Integer getAlertEventId()
   {
      return (this.alertEventId);
   }



   /*******************************************************************************
    ** Setter for alertEventId
    *******************************************************************************/
   public void setAlertEventId(Integer alertEventId)
   {
      this.alertEventId = alertEventId;
   }



   /*******************************************************************************
    ** Fluent setter for alertEventId
    *******************************************************************************/
   public AlertsDelivery withAlertEventId(Integer alertEventId)
   {
      this.alertEventId = alertEventId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for subscriptionId
    *******************************************************************************/
   public Integer getSubscriptionId()
   {
      return (this.subscriptionId);
   }



   /*******************************************************************************
    ** Setter for subscriptionId
    *******************************************************************************/
   public void setSubscriptionId(Integer subscriptionId)
   {
      this.subscriptionId = subscriptionId;
   }



   /*******************************************************************************
    ** Fluent setter for subscriptionId
    *******************************************************************************/
   public AlertsDelivery withSubscriptionId(Integer subscriptionId)
   {
      this.subscriptionId = subscriptionId;
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
   public AlertsDelivery withChannelId(Integer channelId)
   {
      this.channelId = channelId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for recipientUserId
    *******************************************************************************/
   public String getRecipientUserId()
   {
      return (this.recipientUserId);
   }



   /*******************************************************************************
    ** Setter for recipientUserId
    *******************************************************************************/
   public void setRecipientUserId(String recipientUserId)
   {
      this.recipientUserId = recipientUserId;
   }



   /*******************************************************************************
    ** Fluent setter for recipientUserId
    *******************************************************************************/
   public AlertsDelivery withRecipientUserId(String recipientUserId)
   {
      this.recipientUserId = recipientUserId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for recipientLabel
    *******************************************************************************/
   public String getRecipientLabel()
   {
      return (this.recipientLabel);
   }



   /*******************************************************************************
    ** Setter for recipientLabel
    *******************************************************************************/
   public void setRecipientLabel(String recipientLabel)
   {
      this.recipientLabel = recipientLabel;
   }



   /*******************************************************************************
    ** Fluent setter for recipientLabel
    *******************************************************************************/
   public AlertsDelivery withRecipientLabel(String recipientLabel)
   {
      this.recipientLabel = recipientLabel;
      return (this);
   }



   /*******************************************************************************
    ** Getter for kind
    *******************************************************************************/
   public String getKind()
   {
      return (this.kind);
   }



   /*******************************************************************************
    ** Setter for kind
    *******************************************************************************/
   public void setKind(String kind)
   {
      this.kind = kind;
   }



   /*******************************************************************************
    ** Fluent setter for kind
    *******************************************************************************/
   public AlertsDelivery withKind(String kind)
   {
      this.kind = kind;
      return (this);
   }



   /*******************************************************************************
    ** Getter for status
    *******************************************************************************/
   public String getStatus()
   {
      return (this.status);
   }



   /*******************************************************************************
    ** Setter for status
    *******************************************************************************/
   public void setStatus(String status)
   {
      this.status = status;
   }



   /*******************************************************************************
    ** Fluent setter for status
    *******************************************************************************/
   public AlertsDelivery withStatus(String status)
   {
      this.status = status;
      return (this);
   }



   /*******************************************************************************
    ** Getter for suppressedReason
    *******************************************************************************/
   public String getSuppressedReason()
   {
      return (this.suppressedReason);
   }



   /*******************************************************************************
    ** Setter for suppressedReason
    *******************************************************************************/
   public void setSuppressedReason(String suppressedReason)
   {
      this.suppressedReason = suppressedReason;
   }



   /*******************************************************************************
    ** Fluent setter for suppressedReason
    *******************************************************************************/
   public AlertsDelivery withSuppressedReason(String suppressedReason)
   {
      this.suppressedReason = suppressedReason;
      return (this);
   }



   /*******************************************************************************
    ** Getter for attempts
    *******************************************************************************/
   public Integer getAttempts()
   {
      return (this.attempts);
   }



   /*******************************************************************************
    ** Setter for attempts
    *******************************************************************************/
   public void setAttempts(Integer attempts)
   {
      this.attempts = attempts;
   }



   /*******************************************************************************
    ** Fluent setter for attempts
    *******************************************************************************/
   public AlertsDelivery withAttempts(Integer attempts)
   {
      this.attempts = attempts;
      return (this);
   }



   /*******************************************************************************
    ** Getter for nextAttemptAt
    *******************************************************************************/
   public Instant getNextAttemptAt()
   {
      return (this.nextAttemptAt);
   }



   /*******************************************************************************
    ** Setter for nextAttemptAt
    *******************************************************************************/
   public void setNextAttemptAt(Instant nextAttemptAt)
   {
      this.nextAttemptAt = nextAttemptAt;
   }



   /*******************************************************************************
    ** Fluent setter for nextAttemptAt
    *******************************************************************************/
   public AlertsDelivery withNextAttemptAt(Instant nextAttemptAt)
   {
      this.nextAttemptAt = nextAttemptAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for claimedUntil
    *******************************************************************************/
   public Instant getClaimedUntil()
   {
      return (this.claimedUntil);
   }



   /*******************************************************************************
    ** Setter for claimedUntil
    *******************************************************************************/
   public void setClaimedUntil(Instant claimedUntil)
   {
      this.claimedUntil = claimedUntil;
   }



   /*******************************************************************************
    ** Fluent setter for claimedUntil
    *******************************************************************************/
   public AlertsDelivery withClaimedUntil(Instant claimedUntil)
   {
      this.claimedUntil = claimedUntil;
      return (this);
   }



   /*******************************************************************************
    ** Getter for sentAt
    *******************************************************************************/
   public Instant getSentAt()
   {
      return (this.sentAt);
   }



   /*******************************************************************************
    ** Setter for sentAt
    *******************************************************************************/
   public void setSentAt(Instant sentAt)
   {
      this.sentAt = sentAt;
   }



   /*******************************************************************************
    ** Fluent setter for sentAt
    *******************************************************************************/
   public AlertsDelivery withSentAt(Instant sentAt)
   {
      this.sentAt = sentAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for providerMessageId
    *******************************************************************************/
   public String getProviderMessageId()
   {
      return (this.providerMessageId);
   }



   /*******************************************************************************
    ** Setter for providerMessageId
    *******************************************************************************/
   public void setProviderMessageId(String providerMessageId)
   {
      this.providerMessageId = providerMessageId;
   }



   /*******************************************************************************
    ** Fluent setter for providerMessageId
    *******************************************************************************/
   public AlertsDelivery withProviderMessageId(String providerMessageId)
   {
      this.providerMessageId = providerMessageId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for lastError
    *******************************************************************************/
   public String getLastError()
   {
      return (this.lastError);
   }



   /*******************************************************************************
    ** Setter for lastError
    *******************************************************************************/
   public void setLastError(String lastError)
   {
      this.lastError = lastError;
   }



   /*******************************************************************************
    ** Fluent setter for lastError
    *******************************************************************************/
   public AlertsDelivery withLastError(String lastError)
   {
      this.lastError = lastError;
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
   public AlertsDelivery withCreateDate(Instant createDate)
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
   public AlertsDelivery withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }
}
