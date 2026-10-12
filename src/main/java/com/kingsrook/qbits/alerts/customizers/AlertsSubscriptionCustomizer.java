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


import java.util.List;
import java.util.Optional;
import com.kingsrook.qbits.alerts.AlertsQBitConfig;
import com.kingsrook.qbits.alerts.AlertsSecurity;
import com.kingsrook.qbits.alerts.model.AlertsMute;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues;
import com.kingsrook.qbits.alerts.model.AlertsSubscription;
import com.kingsrook.qbits.alerts.model.AlertsUserPreference;
import com.kingsrook.qqq.backend.core.actions.customizers.TableCustomizerInterface;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.statusmessages.BadInputStatusMessage;
import com.kingsrook.qqq.backend.core.model.statusmessages.PermissionDeniedMessage;


/*******************************************************************************
 ** Keeps personal rows personal (design section 7.1), in the core actions:
 ** - Subscriptions: anyone with alerts.view subscribes themselves (the user
 **   id is set from the session); only alerts.admin may create team or
 **   channel subscriptions or subscribe someone else.
 ** - My Alert Settings: the user id is the session's.
 ** - Mutes: the creator is recorded; only alerts.admin may suppress alerts
 **   (SUPPRESS_ALERTS, a maintenance window), others only silence.
 *******************************************************************************/
public class AlertsSubscriptionCustomizer implements TableCustomizerInterface
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> preInsert(InsertInput insertInput, List<QRecord> records, boolean isPreview) throws QException
   {
      for(QRecord record : records)
      {
         apply(insertInput.getTableName(), record, true);
      }
      return (records);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<QRecord> preUpdate(UpdateInput updateInput, List<QRecord> records, boolean isPreview, Optional<List<QRecord>> oldRecordList) throws QException
   {
      for(QRecord record : records)
      {
         apply(updateInput.getTableName(), record, false);
      }
      return (records);
   }



   /*******************************************************************************
    ** Stamp or check one record.
    *******************************************************************************/
   static void apply(String tableName, QRecord record, boolean isInsert)
   {
      if(AlertsSecurity.isSystemUser())
      {
         return;
      }

      QSession session = QContext.getQSession();
      String   userId  = AlertsSecurity.userId(session);
      boolean  admin   = session != null && session.hasPermission(AlertsQBitConfig.PERMISSION_ADMIN);

      switch(tableName)
      {
         case AlertsSubscription.TABLE_NAME ->
         {
            String subscriberType = record.getValueString("subscriberType");
            if(isInsert)
            {
               record.setValue("createdByUserId", userId);
               if(subscriberType == null)
               {
                  subscriberType = AlertsPossibleValues.SubscriberType.USER.name();
                  record.setValue("subscriberType", subscriberType);
               }
               if(AlertsPossibleValues.SubscriberType.USER.name().equals(subscriberType) && (!admin || record.getValue("userId") == null))
               {
                  record.setValue("userId", userId);
               }
            }
            if(!admin && subscriberType != null && !AlertsPossibleValues.SubscriberType.USER.name().equals(subscriberType))
            {
               record.addError(new PermissionDeniedMessage("Only alerts admins can subscribe a team or a channel."));
            }
            if(AlertsPossibleValues.SubscriberType.TEAM.name().equals(subscriberType) && record.getValue("teamId") == null)
            {
               record.addError(new BadInputStatusMessage("Choose the team to subscribe."));
            }
            if(AlertsPossibleValues.SubscriberType.CHANNEL.name().equals(subscriberType) && record.getValue("channelId") == null)
            {
               record.addError(new BadInputStatusMessage("Choose the channel to subscribe."));
            }
         }
         case AlertsUserPreference.TABLE_NAME ->
         {
            if(isInsert && (!admin || record.getValue("userId") == null))
            {
               record.setValue("userId", userId);
            }
         }
         case AlertsMute.TABLE_NAME ->
         {
            if(isInsert)
            {
               record.setValue("createdByUserId", userId);
            }
            if(!admin && AlertsPossibleValues.MuteEffect.SUPPRESS_ALERTS.name().equals(record.getValueString("effect")))
            {
               record.addError(new PermissionDeniedMessage("Only alerts admins can create maintenance windows that suppress alerts."));
            }
         }
         default ->
         {
            //////////////////////////////
            // nothing for other tables //
            //////////////////////////////
         }
      }
   }
}
