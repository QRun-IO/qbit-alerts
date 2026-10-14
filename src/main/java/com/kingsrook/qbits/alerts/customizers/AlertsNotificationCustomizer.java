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


package com.kingsrook.qbits.alerts.customizers;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import com.kingsrook.qbits.alerts.AlertsQBitConfig;
import com.kingsrook.qbits.alerts.AlertsRuntime;
import com.kingsrook.qbits.alerts.AlertsSecurity;
import com.kingsrook.qbits.alerts.model.AlertsNotification;
import com.kingsrook.qbits.alerts.model.AlertsNotificationPreference;
import com.kingsrook.qbits.alerts.model.AlertsNotificationRecipient;
import com.kingsrook.qbits.alerts.notifications.NotificationService;
import com.kingsrook.qbits.alerts.spi.NotificationAudience;
import com.kingsrook.qbits.alerts.spi.NotificationCategory;
import com.kingsrook.qqq.backend.core.actions.customizers.AbstractPreInsertCustomizer;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizerInterface;
import com.kingsrook.qqq.backend.core.actions.tables.DeleteAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.tables.Capability;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.statusmessages.BadInputStatusMessage;
import com.kingsrook.qqq.backend.core.model.statusmessages.PermissionDeniedMessage;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Rules for the notification tables, in the core actions (so the admin, the
 ** API and the bell all follow them):
 ** - Notifications: the author is the session user; new ones start as
 **   DRAFT (sending is a record action); status and the send bookkeeping
 **   can't be edited; links must be app paths or https; an audience needs
 **   its values. Deleting one deletes its recipient rows.
 ** - Recipients: people change only read and dismissed on their own rows
 **   (the owner lock); the qbit inserts and deletes them.
 ** - Notification settings: the user id is the session's; SECURITY can't be
 **   muted in-app.
 ** The system user (the qbit itself) is not restricted.
 *******************************************************************************/
public class AlertsNotificationCustomizer implements TableCustomizerInterface
{
   private static final Set<String> NOTIFICATION_SYSTEM_FIELDS = Set.of("status", "authorUserId", "sentAt", "retractedAt", "recipientCount", "readCount");
   private static final Set<String> RECIPIENT_EDITABLE_FIELDS  = Set.of("id", "readAt", "dismissedAt");



   /*******************************************************************************
    ** Stamp the user and author before QQQ's required-field and security-lock
    ** checks, so they check the values this customizer sets.
    *******************************************************************************/
   @Override
   public AbstractPreInsertCustomizer.WhenToRun whenToRunPreInsert(InsertInput insertInput, boolean isPreview)
   {
      return (AbstractPreInsertCustomizer.WhenToRun.BEFORE_ALL_VALIDATIONS);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> preInsert(InsertInput insertInput, List<QRecord> records, boolean isPreview) throws QException
   {
      if(AlertsSecurity.isSystemUser())
      {
         return (records);
      }
      String tableName = insertInput.getTableName();
      if(AlertsNotificationRecipient.TABLE_NAME.equals(tableName))
      {
         return (AlertsWriteGuardCustomizer.deny(tableName, Capability.TABLE_INSERT, records));
      }
      String userId = AlertsSecurity.userId(QContext.getQSession());
      for(QRecord record : records)
      {
         if(AlertsNotification.TABLE_NAME.equals(tableName))
         {
            record.setValue("authorUserId", userId);
            record.setValue("status", NotificationService.STATUS_DRAFT);
            for(String field : List.of("sentAt", "retractedAt", "recipientCount", "readCount"))
            {
               record.removeValue(field);
            }
            if(!StringUtils.hasContent(record.getValueString("sourceName")))
            {
               record.setValue("sourceName", "admin");
            }
            checkNotification(record);
         }
         else if(AlertsNotificationPreference.TABLE_NAME.equals(tableName))
         {
            if(!isAlertsAdmin() || record.getValue("userId") == null)
            {
               record.setValue("userId", userId);
            }
            checkPreference(record);
         }
      }
      return (records);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> preUpdate(UpdateInput updateInput, List<QRecord> records, boolean isPreview, Optional<List<QRecord>> oldRecordList) throws QException
   {
      if(AlertsSecurity.isSystemUser())
      {
         return (records);
      }
      String tableName = updateInput.getTableName();
      for(QRecord record : records)
      {
         if(AlertsNotification.TABLE_NAME.equals(tableName))
         {
            for(String field : NOTIFICATION_SYSTEM_FIELDS)
            {
               record.removeValue(field);
            }
            checkNotification(record);
         }
         else if(AlertsNotificationRecipient.TABLE_NAME.equals(tableName))
         {
            for(String field : new ArrayList<>(record.getValues().keySet()))
            {
               if(!RECIPIENT_EDITABLE_FIELDS.contains(field))
               {
                  record.removeValue(field);
               }
            }
         }
         else if(AlertsNotificationPreference.TABLE_NAME.equals(tableName))
         {
            record.removeValue("userId");
            QRecord merged = new QRecord(record);
            oldRecordList.flatMap(old -> old.stream().filter(o -> o.getValue("id") != null && o.getValue("id").equals(record.getValue("id"))).findFirst())
               .ifPresent(old -> old.getValues().forEach((field, value) -> merged.getValues().putIfAbsent(field, value)));
            checkPreference(merged);
            merged.getErrors().forEach(record::addError);
         }
      }
      return (records);
   }



   /*******************************************************************************
    ** People never delete recipient rows; deleting a notification deletes its
    ** recipients first (as the qbit).
    *******************************************************************************/
   @Override
   public List<QRecord> preDelete(DeleteInput deleteInput, List<QRecord> records, boolean isPreview) throws QException
   {
      String tableName = deleteInput.getTableName();
      if(AlertsNotificationRecipient.TABLE_NAME.equals(tableName))
      {
         return (AlertsSecurity.isSystemUser() ? records : AlertsWriteGuardCustomizer.deny(tableName, Capability.TABLE_DELETE, records));
      }
      if(AlertsNotification.TABLE_NAME.equals(tableName) && !isPreview)
      {
         List<Serializable> ids = records.stream().map(record -> (Serializable) record.getValueInteger("id")).filter(id -> id != null).toList();
         if(!ids.isEmpty())
         {
            AlertsRuntime.get().asSystem(() -> new DeleteAction().execute(new DeleteInput(AlertsNotificationRecipient.TABLE_NAME)
               .withQueryFilter(new QQueryFilter(new QFilterCriteria("notificationId", QCriteriaOperator.IN, ids)))));
         }
      }
      return (records);
   }



   /*******************************************************************************
    ** A notification's link and audience (the fields present on the record).
    *******************************************************************************/
   static void checkNotification(QRecord record)
   {
      if(record.getValues().containsKey("linkUrl") && StringUtils.hasContent(record.getValueString("linkUrl")) && NotificationService.safeLink(record.getValueString("linkUrl")) == null)
      {
         record.addError(new BadInputStatusMessage("The link must be an app path (starting with /) or an https URL."));
      }
      if(record.getValues().containsKey("audienceType"))
      {
         NotificationAudience audience = NotificationAudience.parse(record.getValueString("audienceType"), record.getValueString("audienceValue"));
         if(audience == null)
         {
            record.addError(new BadInputStatusMessage("Choose who the notification is for."));
         }
         else if(audience.getType() != NotificationAudience.Type.ALL_USERS && audience.getValues().isEmpty())
         {
            record.addError(new BadInputStatusMessage("List the " + switch(audience.getType())
               {
                  case ROLES -> "roles or groups";
                  case PERMISSION -> "permission";
                  default -> "user ids";
               } + " in Audience Members."));
         }
      }
   }



   /*******************************************************************************
    ** SECURITY notices can't be muted in-app.
    *******************************************************************************/
   static void checkPreference(QRecord record)
   {
      if(NotificationCategory.SECURITY.name().equals(record.getValueString("category")) && (Boolean.TRUE.equals(record.getValueBoolean("inAppMuted")) || record.getValue("inAppMinSeverity") != null))
      {
         record.addError(new PermissionDeniedMessage("Security notices can't be muted in-app; you can still choose how they are emailed."));
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static boolean isAlertsAdmin()
   {
      QSession session = QContext.getQSession();
      return (session != null && session.hasPermission(AlertsQBitConfig.PERMISSION_ADMIN));
   }
}
