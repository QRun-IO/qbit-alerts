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

package com.kingsrook.qbits.alerts;


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper;
import com.kingsrook.qqq.backend.core.actions.permissions.TablePermissionSubType;
import com.kingsrook.qqq.backend.core.context.CapturedContext;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.security.MultiRecordSecurityLock;
import com.kingsrook.qqq.backend.core.model.metadata.security.QSecurityKeyType;
import com.kingsrook.qqq.backend.core.model.metadata.security.RecordSecurityLock;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.session.QSystemUserSession;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** The Alerts record-security keys (design section 7).
 **
 ** Three security key types guard the qbit's tables:
 ** - alerts_user: the session's user id. Personal rows (subscriptions,
 **   settings, inbox items) are locked to it. alerts.admin holders get its
 **   all-access key.
 ** - alerts_permission: every permission the session holds. An alert whose
 **   type sets a visibility permission is only readable with it.
 ** - alerts_readable_table: every table the session can read. An alert about
 **   a subject table is only readable by users who can read that table.
 ** The last two follow the logged-in user, so counts, lists, the bell and
 ** the home are all filtered in the query. (The system user, which the
 ** engine runs as, holds every all-access key.)
 **
 ** Hosts call applyToSession from their authentication customizer, after the
 ** session's user and permissions are set. The qbit's own endpoints and
 ** widgets also call ensureSessionKeys, so a host that forgets still gets
 ** correct (never wider) results there. Without the keys, gated alerts (with
 ** a visibility permission or a subject table) are hidden.
 *******************************************************************************/
public final class AlertsSecurity
{
   public static final String KEY_USER                 = "alerts_user";
   public static final String KEY_USER_ALL_ACCESS      = "alerts_user_all_access";
   public static final String KEY_PERMISSION           = "alerts_permission";
   public static final String KEY_PERMISSION_ALL       = "alerts_permission_all_access";
   public static final String KEY_READABLE_TABLE       = "alerts_readable_table";
   public static final String KEY_READABLE_TABLE_ALL   = "alerts_readable_table_all_access";

   private static final String ALERT_TABLE = "alerts_alert";



   /*******************************************************************************
    ** Keys only.
    *******************************************************************************/
   private AlertsSecurity()
   {
   }



   /*******************************************************************************
    ** The three key types, for the producer.
    *******************************************************************************/
   public static List<QSecurityKeyType> buildSecurityKeyTypes()
   {
      return (List.of(
         new QSecurityKeyType().withName(KEY_USER).withAllAccessKeyName(KEY_USER_ALL_ACCESS),
         new QSecurityKeyType().withName(KEY_PERMISSION).withAllAccessKeyName(KEY_PERMISSION_ALL),
         new QSecurityKeyType().withName(KEY_READABLE_TABLE).withAllAccessKeyName(KEY_READABLE_TABLE_ALL)));
   }



   /*******************************************************************************
    ** Owner lock: the session's alerts_user value must equal the field.
    *******************************************************************************/
   public static RecordSecurityLock ownerLock(String fieldName)
   {
      return (new RecordSecurityLock()
         .withSecurityKeyType(KEY_USER)
         .withFieldName(fieldName)
         .withLockScope(RecordSecurityLock.LockScope.READ_AND_WRITE));
   }



   /*******************************************************************************
    ** Visibility lock on an alert (joinFromAlert null) or on a child of an
    ** alert (through that join): the session must hold the alert's
    ** visibilityPermission and be able to read its subjectTableName (each
    ** check passes when the alert has none).
    *******************************************************************************/
   public static MultiRecordSecurityLock visibilityLock(String joinFromAlert)
   {
      String prefix = joinFromAlert == null ? "" : ALERT_TABLE + ".";
      RecordSecurityLock permission = new RecordSecurityLock()
         .withSecurityKeyType(KEY_PERMISSION)
         .withFieldName(prefix + "visibilityPermission")
         .withNullValueBehavior(RecordSecurityLock.NullValueBehavior.ALLOW)
         .withLockScope(RecordSecurityLock.LockScope.READ);
      RecordSecurityLock table = new RecordSecurityLock()
         .withSecurityKeyType(KEY_READABLE_TABLE)
         .withFieldName(prefix + "subjectTableName")
         .withNullValueBehavior(RecordSecurityLock.NullValueBehavior.ALLOW)
         .withLockScope(RecordSecurityLock.LockScope.READ);
      if(joinFromAlert != null)
      {
         permission.setJoinNameChain(List.of(joinFromAlert));
         table.setJoinNameChain(List.of(joinFromAlert));
      }
      return (new MultiRecordSecurityLock()
         .withOperator(MultiRecordSecurityLock.BooleanOperator.AND)
         .withLock(permission)
         .withLock(table));
   }



   /*******************************************************************************
    ** Put the alerts keys into a session: the user id (and, for alerts.admin,
    ** the all-access key), every permission it holds, and every table it can
    ** read. Idempotent: earlier alerts keys are replaced. A session without
    ** alerts.view gets none, so the locks deny.
    *******************************************************************************/
   public static void applyToSession(QInstance qInstance, QSession session)
   {
      if(session == null || session instanceof QSystemUserSession || qInstance == null)
      {
         return;
      }

      Map<String, List<Serializable>> keys = session.getSecurityKeyValues();
      if(keys != null)
      {
         for(String key : List.of(KEY_USER, KEY_USER_ALL_ACCESS, KEY_PERMISSION, KEY_READABLE_TABLE))
         {
            keys.remove(key);
         }
      }

      if(!session.hasPermission(AlertsQBitConfig.PERMISSION_VIEW))
      {
         return;
      }

      String userId = userId(session);
      if(StringUtils.hasContent(userId))
      {
         session.withSecurityKeyValue(KEY_USER, userId);
      }
      if(session.hasPermission(AlertsQBitConfig.PERMISSION_ADMIN))
      {
         session.withSecurityKeyValue(KEY_USER_ALL_ACCESS, true);
      }

      Set<String> permissions = session.getPermissions();
      if(permissions != null)
      {
         for(String permission : permissions)
         {
            session.withSecurityKeyValue(KEY_PERMISSION, permission);
         }
      }

      for(String tableName : readableTables(qInstance, session))
      {
         session.withSecurityKeyValue(KEY_READABLE_TABLE, tableName);
      }
   }



   /*******************************************************************************
    ** Apply the keys only if the session has none yet.
    *******************************************************************************/
   public static void ensureSessionKeys(QInstance qInstance, QSession session)
   {
      if(session == null || session instanceof QSystemUserSession)
      {
         return;
      }
      Map<String, List<Serializable>> keys = session.getSecurityKeyValues();
      if(keys == null || !keys.containsKey(KEY_READABLE_TABLE))
      {
         applyToSession(qInstance, session);
      }
   }



   /*******************************************************************************
    ** The tables the session may read (by its QQQ table permissions).
    *******************************************************************************/
   public static List<String> readableTables(QInstance qInstance, QSession session)
   {
      List<String> names = new ArrayList<>();
      QContext.withTemporaryContext(new CapturedContext(qInstance, session), () ->
      {
         AbstractActionInput input = new AbstractActionInput();
         for(QTableMetaData table : qInstance.getTables().values())
         {
            if(PermissionsHelper.hasTablePermission(input, table.getName(), TablePermissionSubType.READ))
            {
               names.add(table.getName());
            }
         }
      });
      return (names);
   }



   /*******************************************************************************
    ** The session's user id (null when there is none).
    *******************************************************************************/
   public static String userId(QSession session)
   {
      if(session == null || session.getUser() == null)
      {
         return (null);
      }
      return (session.getUser().getIdReference());
   }



   /*******************************************************************************
    ** A system-user session holding the alerts all-access keys (QQQ caches the
    ** system user's keys from whatever instance it saw first, so add ours
    ** explicitly).
    *******************************************************************************/
   public static QSystemUserSession systemSession()
   {
      QSystemUserSession session = new QSystemUserSession();
      session.withSecurityKeyValue(KEY_USER_ALL_ACCESS, true);
      session.withSecurityKeyValue(KEY_PERMISSION_ALL, true);
      session.withSecurityKeyValue(KEY_READABLE_TABLE_ALL, true);
      return (session);
   }



   /*******************************************************************************
    ** True when the current session is the system user.
    *******************************************************************************/
   public static boolean isSystemUser()
   {
      return (QContext.getQSession() instanceof QSystemUserSession);
   }
}
