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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.kingsrook.qbits.alerts.AlertsRuntime;
import com.kingsrook.qqq.backend.core.actions.customizers.QCodeLoader;
import com.kingsrook.qqq.backend.core.alerts.NotificationAudience;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.modules.authentication.QAuthenticationModuleDispatcher;
import com.kingsrook.qqq.backend.core.modules.authentication.QAuthenticationModuleInterface;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Resolves a notification audience to user ids at send time
 ** (docs/NOTIFICATIONS.md section 4): ALL_USERS and ROLES from the user
 ** directory, PERMISSION by building each directory user's session, USERS as
 ** given. The result is capped at maxAudienceSize. Call it as the system user.
 *******************************************************************************/
public class AlertsAudienceResolver
{
   private static final QLogger LOG = QLogger.getLogger(AlertsAudienceResolver.class);

   private final AlertsRuntime runtime;



   /*******************************************************************************
    ** The users an audience reached: ids in order, labels where known, and why
    ** some could not be resolved.
    *******************************************************************************/
   public record Resolved(List<String> userIds, Map<String, String> labels, List<String> warnings)
   {
   }



   /*******************************************************************************
    ** Constructor
    *******************************************************************************/
   public AlertsAudienceResolver(AlertsRuntime runtime)
   {
      this.runtime = runtime;
   }



   /*******************************************************************************
    ** Resolve an audience to users.
    *******************************************************************************/
   public Resolved resolve(NotificationAudience audience) throws QException
   {
      Map<String, String> users    = new LinkedHashMap<>();
      List<String>        warnings = new ArrayList<>();
      if(audience == null)
      {
         warnings.add("No audience is set.");
         return (new Resolved(List.of(), Map.of(), warnings));
      }

      switch(audience.getType())
      {
         case USERS ->
         {
            Map<String, String> labels = labelsIfKnown();
            for(String userId : audience.getValues())
            {
               users.put(userId, labels.getOrDefault(userId, userId));
            }
            if(audience.getValues().isEmpty())
            {
               warnings.add("Name at least one user id.");
            }
         }
         case ALL_USERS ->
         {
            for(AlertsUserDirectoryInterface.DirectoryUser user : directory().listUsers())
            {
               users.put(user.userId(), label(user));
            }
         }
         case ROLES ->
         {
            Set<String> wanted = audience.getValues().stream().map(role -> role.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
            boolean     anyRoles = false;
            for(AlertsUserDirectoryInterface.DirectoryUser user : directory().listUsers())
            {
               Set<String> roles = user.roles() == null ? Set.of() : user.roles();
               anyRoles |= !roles.isEmpty();
               if(roles.stream().anyMatch(role -> wanted.contains(role.toLowerCase(Locale.ROOT))))
               {
                  users.put(user.userId(), label(user));
               }
            }
            if(!anyRoles)
            {
               warnings.add("The user directory lists no roles, so a roles audience reaches nobody. Configure a user directory (AlertsQBitConfig.withUserDirectory).");
            }
         }
         case PERMISSION ->
         {
            String  permission = audience.getValues().isEmpty() ? null : audience.getValues().get(0);
            boolean anySession = false;
            for(AlertsUserDirectoryInterface.DirectoryUser user : permission == null ? List.<AlertsUserDirectoryInterface.DirectoryUser>of() : directory().listUsers())
            {
               QSession session = automatedSession(user.userId());
               anySession |= session != null;
               if(session != null && session.hasPermission(permission))
               {
                  users.put(user.userId(), label(user));
               }
            }
            if(permission == null)
            {
               warnings.add("Name the permission.");
            }
            else if(!anySession)
            {
               warnings.add("The authentication module could not build sessions for users, so their permissions are unknown and a permission audience reaches nobody.");
            }
         }
         default -> warnings.add("Unknown audience.");
      }

      List<String> ids = new ArrayList<>(users.keySet());
      int          max = runtime.getConfig().getMaxAudienceSize();
      if(ids.size() > max)
      {
         warnings.add("The audience has " + ids.size() + " users; only the first " + max + " (maxAudienceSize) are reached.");
         ids = new ArrayList<>(ids.subList(0, max));
      }
      Map<String, String> labels = new LinkedHashMap<>();
      for(String id : ids)
      {
         labels.put(id, users.get(id));
      }
      return (new Resolved(ids, labels, warnings));
   }



   /*******************************************************************************
    ** The configured directory, or the known-users default.
    *******************************************************************************/
   public AlertsUserDirectoryInterface directory()
   {
      QCodeReference reference = runtime.getConfig().getUserDirectory();
      if(reference != null)
      {
         AlertsUserDirectoryInterface directory = QCodeLoader.getAdHoc(AlertsUserDirectoryInterface.class, reference);
         if(directory != null)
         {
            return (directory);
         }
      }
      return (new KnownUsersDirectory());
   }



   /*******************************************************************************
    ** Labels for the directory's users (empty when it can't be read).
    *******************************************************************************/
   private Map<String, String> labelsIfKnown()
   {
      Map<String, String> labels = new LinkedHashMap<>();
      try
      {
         for(AlertsUserDirectoryInterface.DirectoryUser user : directory().listUsers())
         {
            labels.put(user.userId(), label(user));
         }
      }
      catch(Exception e)
      {
         LOG.debug("Could not read the user directory for labels", e);
      }
      return (labels);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String label(AlertsUserDirectoryInterface.DirectoryUser user)
   {
      return (user.label() == null || user.label().isBlank() ? user.userId() : user.label());
   }



   /*******************************************************************************
    ** A session for a user from the instance's authentication module (null
    ** when it can't make one).
    *******************************************************************************/
   private QSession automatedSession(String userId)
   {
      try
      {
         QInstance qInstance = runtime.getQInstance();
         if(qInstance.getAuthentication() == null)
         {
            return (null);
         }
         QAuthenticationModuleInterface module = new QAuthenticationModuleDispatcher().getQModule(qInstance.getAuthentication());
         return (module.createAutomatedSessionForUser(qInstance, userId));
      }
      catch(Exception e)
      {
         LOG.debug("Could not build a session for a user", e, logPair("userId", userId));
         return (null);
      }
   }
}
