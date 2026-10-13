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


package com.kingsrook.qbits.alerts.notifications;


import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.kingsrook.qbits.alerts.engine.AlertsDb;
import com.kingsrook.qbits.alerts.model.AlertsUserPreference;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** The default user directory: everyone who has used Alerts, that is, who has
 ** a row in My Alert Settings (created the first time a user opens the bell,
 ** the Alerts home or a record panel). It knows no roles. Called as the
 ** system user.
 *******************************************************************************/
public class KnownUsersDirectory implements AlertsUserDirectoryInterface
{
   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<DirectoryUser> listUsers() throws QException
   {
      List<DirectoryUser> users = new ArrayList<>();
      for(QRecord preference : AlertsDb.query(AlertsUserPreference.TABLE_NAME, new QQueryFilter()))
      {
         String userId = preference.getValueString("userId");
         if(userId != null)
         {
            users.add(new DirectoryUser(userId, preference.getValueString("userLabel"), preference.getValueString("emailAddress"), Set.of()));
         }
      }
      return (users);
   }
}
