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


import java.util.List;
import java.util.Set;
import com.kingsrook.qqq.backend.core.exceptions.QException;


/*******************************************************************************
 ** The host's list of people, for notification audiences (docs/NOTIFICATIONS.md
 ** section 4). Set it with AlertsQBitConfig.withUserDirectory. Without one,
 ** the directory is everyone who has used Alerts (KnownUsersDirectory).
 *******************************************************************************/
public interface AlertsUserDirectoryInterface
{
   /*******************************************************************************
    ** Every active user, with their roles or groups (for ROLES audiences).
    *******************************************************************************/
   List<DirectoryUser> listUsers() throws QException;



   /*******************************************************************************
    ** One person: their QQQ user id (the session's user id reference), a
    ** display label, an email address (may be null), and their roles or groups.
    *******************************************************************************/
   record DirectoryUser(String userId, String label, String emailAddress, Set<String> roles)
   {
   }
}
