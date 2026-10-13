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
 ** A message for people: an announcement an admin writes, or one a producer emits (a failed job, a share, a security notice, a release). One recipient row per user it reaches.
 **
 ** Generated from the qbit-alerts schema (one definition for the entity,
 ** the Liquibase changelog and the test DDL).
 *******************************************************************************/
public class AlertsNotification extends QRecordEntity
{
   public static final String TABLE_NAME = "alerts_notification";

   @QField(isEditable = false, isPrimaryKey = true)
   private Integer id;

   @QField(isRequired = true, maxLength = 200, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String title;

   @QField(label = "Message")
   private String body;

   @QField(isRequired = true, maxLength = 16, possibleValueSourceName = "alerts_notification_severity")
   private String severity;

   @QField(isRequired = true, maxLength = 16, possibleValueSourceName = "alerts_notification_category")
   private String category;

   @QField(isEditable = false, maxLength = 16, possibleValueSourceName = "alerts_notification_status")
   private String status;

   @QField(maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, label = "Source")
   private String sourceName;

   @QField(maxLength = 200, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String sourceKey;

   @QField(maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, label = "Source Table")
   private String sourceTableName;

   @QField(maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, label = "Source Record")
   private String sourceRecordId;

   @QField(maxLength = 1000, valueTooLongBehavior = ValueTooLongBehavior.ERROR, label = "Link")
   private String linkUrl;

   @QField(maxLength = 200, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS)
   private String linkLabel;

   @QField(isRequired = true, maxLength = 16, possibleValueSourceName = "alerts_audience_type", label = "Audience")
   private String audienceType;

   @QField(label = "Audience Members")
   private String audienceValue;

   @QField()
   private Instant publishAt;

   @QField()
   private Instant expireAt;

   @QField(label = "Sticky Banner")
   private Boolean sticky;

   @QField(isEditable = false, maxLength = 100, valueTooLongBehavior = ValueTooLongBehavior.TRUNCATE_ELLIPSIS, label = "Author")
   private String authorUserId;

   @QField(isEditable = false)
   private Instant sentAt;

   @QField(isEditable = false)
   private Instant retractedAt;

   @QField(isEditable = false, label = "Recipients")
   private Integer recipientCount;

   @QField(isEditable = false, label = "Read")
   private Integer readCount;

   @QField(isEditable = false)
   private Instant createDate;

   @QField(isEditable = false)
   private Instant modifyDate;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public AlertsNotification()
   {
   }


   /*******************************************************************************
    ** Constructor that populates from a QRecord.
    *******************************************************************************/
   public AlertsNotification(QRecord record)
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
   public AlertsNotification withId(Integer id)
   {
      this.id = id;
      return (this);
   }


   /*******************************************************************************
    ** Getter for title
    *******************************************************************************/
   public String getTitle()
   {
      return (this.title);
   }


   /*******************************************************************************
    ** Setter for title
    *******************************************************************************/
   public void setTitle(String title)
   {
      this.title = title;
   }


   /*******************************************************************************
    ** Fluent setter for title
    *******************************************************************************/
   public AlertsNotification withTitle(String title)
   {
      this.title = title;
      return (this);
   }


   /*******************************************************************************
    ** Getter for body
    *******************************************************************************/
   public String getBody()
   {
      return (this.body);
   }


   /*******************************************************************************
    ** Setter for body
    *******************************************************************************/
   public void setBody(String body)
   {
      this.body = body;
   }


   /*******************************************************************************
    ** Fluent setter for body
    *******************************************************************************/
   public AlertsNotification withBody(String body)
   {
      this.body = body;
      return (this);
   }


   /*******************************************************************************
    ** Getter for severity
    *******************************************************************************/
   public String getSeverity()
   {
      return (this.severity);
   }


   /*******************************************************************************
    ** Setter for severity
    *******************************************************************************/
   public void setSeverity(String severity)
   {
      this.severity = severity;
   }


   /*******************************************************************************
    ** Fluent setter for severity
    *******************************************************************************/
   public AlertsNotification withSeverity(String severity)
   {
      this.severity = severity;
      return (this);
   }


   /*******************************************************************************
    ** Getter for category
    *******************************************************************************/
   public String getCategory()
   {
      return (this.category);
   }


   /*******************************************************************************
    ** Setter for category
    *******************************************************************************/
   public void setCategory(String category)
   {
      this.category = category;
   }


   /*******************************************************************************
    ** Fluent setter for category
    *******************************************************************************/
   public AlertsNotification withCategory(String category)
   {
      this.category = category;
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
   public AlertsNotification withStatus(String status)
   {
      this.status = status;
      return (this);
   }


   /*******************************************************************************
    ** Getter for sourceName
    *******************************************************************************/
   public String getSourceName()
   {
      return (this.sourceName);
   }


   /*******************************************************************************
    ** Setter for sourceName
    *******************************************************************************/
   public void setSourceName(String sourceName)
   {
      this.sourceName = sourceName;
   }


   /*******************************************************************************
    ** Fluent setter for sourceName
    *******************************************************************************/
   public AlertsNotification withSourceName(String sourceName)
   {
      this.sourceName = sourceName;
      return (this);
   }


   /*******************************************************************************
    ** Getter for sourceKey
    *******************************************************************************/
   public String getSourceKey()
   {
      return (this.sourceKey);
   }


   /*******************************************************************************
    ** Setter for sourceKey
    *******************************************************************************/
   public void setSourceKey(String sourceKey)
   {
      this.sourceKey = sourceKey;
   }


   /*******************************************************************************
    ** Fluent setter for sourceKey
    *******************************************************************************/
   public AlertsNotification withSourceKey(String sourceKey)
   {
      this.sourceKey = sourceKey;
      return (this);
   }


   /*******************************************************************************
    ** Getter for sourceTableName
    *******************************************************************************/
   public String getSourceTableName()
   {
      return (this.sourceTableName);
   }


   /*******************************************************************************
    ** Setter for sourceTableName
    *******************************************************************************/
   public void setSourceTableName(String sourceTableName)
   {
      this.sourceTableName = sourceTableName;
   }


   /*******************************************************************************
    ** Fluent setter for sourceTableName
    *******************************************************************************/
   public AlertsNotification withSourceTableName(String sourceTableName)
   {
      this.sourceTableName = sourceTableName;
      return (this);
   }


   /*******************************************************************************
    ** Getter for sourceRecordId
    *******************************************************************************/
   public String getSourceRecordId()
   {
      return (this.sourceRecordId);
   }


   /*******************************************************************************
    ** Setter for sourceRecordId
    *******************************************************************************/
   public void setSourceRecordId(String sourceRecordId)
   {
      this.sourceRecordId = sourceRecordId;
   }


   /*******************************************************************************
    ** Fluent setter for sourceRecordId
    *******************************************************************************/
   public AlertsNotification withSourceRecordId(String sourceRecordId)
   {
      this.sourceRecordId = sourceRecordId;
      return (this);
   }


   /*******************************************************************************
    ** Getter for linkUrl
    *******************************************************************************/
   public String getLinkUrl()
   {
      return (this.linkUrl);
   }


   /*******************************************************************************
    ** Setter for linkUrl
    *******************************************************************************/
   public void setLinkUrl(String linkUrl)
   {
      this.linkUrl = linkUrl;
   }


   /*******************************************************************************
    ** Fluent setter for linkUrl
    *******************************************************************************/
   public AlertsNotification withLinkUrl(String linkUrl)
   {
      this.linkUrl = linkUrl;
      return (this);
   }


   /*******************************************************************************
    ** Getter for linkLabel
    *******************************************************************************/
   public String getLinkLabel()
   {
      return (this.linkLabel);
   }


   /*******************************************************************************
    ** Setter for linkLabel
    *******************************************************************************/
   public void setLinkLabel(String linkLabel)
   {
      this.linkLabel = linkLabel;
   }


   /*******************************************************************************
    ** Fluent setter for linkLabel
    *******************************************************************************/
   public AlertsNotification withLinkLabel(String linkLabel)
   {
      this.linkLabel = linkLabel;
      return (this);
   }


   /*******************************************************************************
    ** Getter for audienceType
    *******************************************************************************/
   public String getAudienceType()
   {
      return (this.audienceType);
   }


   /*******************************************************************************
    ** Setter for audienceType
    *******************************************************************************/
   public void setAudienceType(String audienceType)
   {
      this.audienceType = audienceType;
   }


   /*******************************************************************************
    ** Fluent setter for audienceType
    *******************************************************************************/
   public AlertsNotification withAudienceType(String audienceType)
   {
      this.audienceType = audienceType;
      return (this);
   }


   /*******************************************************************************
    ** Getter for audienceValue
    *******************************************************************************/
   public String getAudienceValue()
   {
      return (this.audienceValue);
   }


   /*******************************************************************************
    ** Setter for audienceValue
    *******************************************************************************/
   public void setAudienceValue(String audienceValue)
   {
      this.audienceValue = audienceValue;
   }


   /*******************************************************************************
    ** Fluent setter for audienceValue
    *******************************************************************************/
   public AlertsNotification withAudienceValue(String audienceValue)
   {
      this.audienceValue = audienceValue;
      return (this);
   }


   /*******************************************************************************
    ** Getter for publishAt
    *******************************************************************************/
   public Instant getPublishAt()
   {
      return (this.publishAt);
   }


   /*******************************************************************************
    ** Setter for publishAt
    *******************************************************************************/
   public void setPublishAt(Instant publishAt)
   {
      this.publishAt = publishAt;
   }


   /*******************************************************************************
    ** Fluent setter for publishAt
    *******************************************************************************/
   public AlertsNotification withPublishAt(Instant publishAt)
   {
      this.publishAt = publishAt;
      return (this);
   }


   /*******************************************************************************
    ** Getter for expireAt
    *******************************************************************************/
   public Instant getExpireAt()
   {
      return (this.expireAt);
   }


   /*******************************************************************************
    ** Setter for expireAt
    *******************************************************************************/
   public void setExpireAt(Instant expireAt)
   {
      this.expireAt = expireAt;
   }


   /*******************************************************************************
    ** Fluent setter for expireAt
    *******************************************************************************/
   public AlertsNotification withExpireAt(Instant expireAt)
   {
      this.expireAt = expireAt;
      return (this);
   }


   /*******************************************************************************
    ** Getter for sticky
    *******************************************************************************/
   public Boolean getSticky()
   {
      return (this.sticky);
   }


   /*******************************************************************************
    ** Setter for sticky
    *******************************************************************************/
   public void setSticky(Boolean sticky)
   {
      this.sticky = sticky;
   }


   /*******************************************************************************
    ** Fluent setter for sticky
    *******************************************************************************/
   public AlertsNotification withSticky(Boolean sticky)
   {
      this.sticky = sticky;
      return (this);
   }


   /*******************************************************************************
    ** Getter for authorUserId
    *******************************************************************************/
   public String getAuthorUserId()
   {
      return (this.authorUserId);
   }


   /*******************************************************************************
    ** Setter for authorUserId
    *******************************************************************************/
   public void setAuthorUserId(String authorUserId)
   {
      this.authorUserId = authorUserId;
   }


   /*******************************************************************************
    ** Fluent setter for authorUserId
    *******************************************************************************/
   public AlertsNotification withAuthorUserId(String authorUserId)
   {
      this.authorUserId = authorUserId;
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
   public AlertsNotification withSentAt(Instant sentAt)
   {
      this.sentAt = sentAt;
      return (this);
   }


   /*******************************************************************************
    ** Getter for retractedAt
    *******************************************************************************/
   public Instant getRetractedAt()
   {
      return (this.retractedAt);
   }


   /*******************************************************************************
    ** Setter for retractedAt
    *******************************************************************************/
   public void setRetractedAt(Instant retractedAt)
   {
      this.retractedAt = retractedAt;
   }


   /*******************************************************************************
    ** Fluent setter for retractedAt
    *******************************************************************************/
   public AlertsNotification withRetractedAt(Instant retractedAt)
   {
      this.retractedAt = retractedAt;
      return (this);
   }


   /*******************************************************************************
    ** Getter for recipientCount
    *******************************************************************************/
   public Integer getRecipientCount()
   {
      return (this.recipientCount);
   }


   /*******************************************************************************
    ** Setter for recipientCount
    *******************************************************************************/
   public void setRecipientCount(Integer recipientCount)
   {
      this.recipientCount = recipientCount;
   }


   /*******************************************************************************
    ** Fluent setter for recipientCount
    *******************************************************************************/
   public AlertsNotification withRecipientCount(Integer recipientCount)
   {
      this.recipientCount = recipientCount;
      return (this);
   }


   /*******************************************************************************
    ** Getter for readCount
    *******************************************************************************/
   public Integer getReadCount()
   {
      return (this.readCount);
   }


   /*******************************************************************************
    ** Setter for readCount
    *******************************************************************************/
   public void setReadCount(Integer readCount)
   {
      this.readCount = readCount;
   }


   /*******************************************************************************
    ** Fluent setter for readCount
    *******************************************************************************/
   public AlertsNotification withReadCount(Integer readCount)
   {
      this.readCount = readCount;
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
   public AlertsNotification withCreateDate(Instant createDate)
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
   public AlertsNotification withModifyDate(Instant modifyDate)
   {
      this.modifyDate = modifyDate;
      return (this);
   }
}
