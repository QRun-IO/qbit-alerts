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


package com.kingsrook.qbits.alerts.spi;


import java.io.Serializable;
import java.time.Instant;


/*******************************************************************************
 ** A notification for people (stand-in for core's
 ** com.kingsrook.qqq.backend.core.alerts.NotificationSignal): what to tell
 ** them, how urgent it is, what it is about, and who it is for. Send it with
 ** the alerts sink's notify (core: QAlerts.notify).
 *******************************************************************************/
public class NotificationSignal implements Serializable
{
   private String title;
   private String body;
   private NotificationSeverity severity;
   private NotificationCategory category;
   private String sourceName;
   private String sourceKey;
   private String sourceTableName;
   private String sourceRecordId;
   private AlertLink link;
   private NotificationAudience audience;
   private Instant publishAt;
   private Instant expireAt;
   private Boolean sticky;
   private Instant occurredAt;



   /*******************************************************************************
    ** Default constructor.
    *******************************************************************************/
   public NotificationSignal()
   {
   }



   /*******************************************************************************
    ** Constructor with the title.
    *******************************************************************************/
   public NotificationSignal(String title)
   {
      this.title = title;
   }



   /*******************************************************************************
    ** Getter for title: The headline (plain text, required).
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
   public NotificationSignal withTitle(String title)
   {
      this.title = title;
      return (this);
   }



   /*******************************************************************************
    ** Getter for body: The message (markdown).
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
   public NotificationSignal withBody(String body)
   {
      this.body = body;
      return (this);
   }



   /*******************************************************************************
    ** Getter for severity: Default INFO.
    *******************************************************************************/
   public NotificationSeverity getSeverity()
   {
      return (this.severity);
   }



   /*******************************************************************************
    ** Setter for severity
    *******************************************************************************/
   public void setSeverity(NotificationSeverity severity)
   {
      this.severity = severity;
   }



   /*******************************************************************************
    ** Fluent setter for severity
    *******************************************************************************/
   public NotificationSignal withSeverity(NotificationSeverity severity)
   {
      this.severity = severity;
      return (this);
   }



   /*******************************************************************************
    ** Getter for category: Default SYSTEM.
    *******************************************************************************/
   public NotificationCategory getCategory()
   {
      return (this.category);
   }



   /*******************************************************************************
    ** Setter for category
    *******************************************************************************/
   public void setCategory(NotificationCategory category)
   {
      this.category = category;
   }



   /*******************************************************************************
    ** Fluent setter for category
    *******************************************************************************/
   public NotificationSignal withCategory(NotificationCategory category)
   {
      this.category = category;
      return (this);
   }



   /*******************************************************************************
    ** Getter for sourceName: Who emits it (a qbit or app name).
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
   public NotificationSignal withSourceName(String sourceName)
   {
      this.sourceName = sourceName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for sourceKey: Dedup key: a second notify with the same key and audience updates the first.
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
   public NotificationSignal withSourceKey(String sourceKey)
   {
      this.sourceKey = sourceKey;
      return (this);
   }



   /*******************************************************************************
    ** Getter for sourceTableName: The table of the record it is about.
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
   public NotificationSignal withSourceTableName(String sourceTableName)
   {
      this.sourceTableName = sourceTableName;
      return (this);
   }



   /*******************************************************************************
    ** Getter for sourceRecordId: The record it is about.
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
   public NotificationSignal withSourceRecordId(String sourceRecordId)
   {
      this.sourceRecordId = sourceRecordId;
      return (this);
   }



   /*******************************************************************************
    ** Getter for link: Where it takes the reader.
    *******************************************************************************/
   public AlertLink getLink()
   {
      return (this.link);
   }



   /*******************************************************************************
    ** Setter for link
    *******************************************************************************/
   public void setLink(AlertLink link)
   {
      this.link = link;
   }



   /*******************************************************************************
    ** Fluent setter for link
    *******************************************************************************/
   public NotificationSignal withLink(AlertLink link)
   {
      this.link = link;
      return (this);
   }



   /*******************************************************************************
    ** Getter for audience: Who it is for (required).
    *******************************************************************************/
   public NotificationAudience getAudience()
   {
      return (this.audience);
   }



   /*******************************************************************************
    ** Setter for audience
    *******************************************************************************/
   public void setAudience(NotificationAudience audience)
   {
      this.audience = audience;
   }



   /*******************************************************************************
    ** Fluent setter for audience
    *******************************************************************************/
   public NotificationSignal withAudience(NotificationAudience audience)
   {
      this.audience = audience;
      return (this);
   }



   /*******************************************************************************
    ** Getter for publishAt: Not shown before this (default now).
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
   public NotificationSignal withPublishAt(Instant publishAt)
   {
      this.publishAt = publishAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for expireAt: Hidden after this.
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
   public NotificationSignal withExpireAt(Instant expireAt)
   {
      this.expireAt = expireAt;
      return (this);
   }



   /*******************************************************************************
    ** Getter for sticky: A banner until dismissed (meant for CRITICAL).
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
   public NotificationSignal withSticky(Boolean sticky)
   {
      this.sticky = sticky;
      return (this);
   }



   /*******************************************************************************
    ** Getter for occurredAt: When it happened (default now).
    *******************************************************************************/
   public Instant getOccurredAt()
   {
      return (this.occurredAt);
   }



   /*******************************************************************************
    ** Setter for occurredAt
    *******************************************************************************/
   public void setOccurredAt(Instant occurredAt)
   {
      this.occurredAt = occurredAt;
   }



   /*******************************************************************************
    ** Fluent setter for occurredAt
    *******************************************************************************/
   public NotificationSignal withOccurredAt(Instant occurredAt)
   {
      this.occurredAt = occurredAt;
      return (this);
   }
}
