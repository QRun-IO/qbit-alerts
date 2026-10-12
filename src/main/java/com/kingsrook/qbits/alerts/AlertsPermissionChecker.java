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


import java.util.Map;
import com.kingsrook.qqq.backend.core.actions.permissions.AvailablePermission;
import com.kingsrook.qqq.backend.core.actions.permissions.CustomPermissionChecker;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionSubType;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput;
import com.kingsrook.qqq.backend.core.model.metadata.code.InitializableViaCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReferenceWithProperties;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.MetaDataWithName;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.MetaDataWithPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.utils.ValueUtils;


/*******************************************************************************
 ** Gates the Alerts app, widgets and processes on one exact permission name
 ** (alerts.view, alerts.manage or alerts.admin), and lists that name among
 ** the instance's available permissions so hosts can grant it.
 *******************************************************************************/
public class AlertsPermissionChecker implements CustomPermissionChecker, InitializableViaCodeReference
{
   public static final String PERMISSION_PROPERTY = "permissionName";

   private String permissionName;



   /*******************************************************************************
    ** Permission rules requiring the named permission.
    *******************************************************************************/
   public static QPermissionRules rules(String permissionName)
   {
      return (new QPermissionRules()
         .withLevel(PermissionLevel.HAS_ACCESS_PERMISSION)
         .withCustomPermissionChecker(new QCodeReferenceWithProperties(AlertsPermissionChecker.class, Map.of(PERMISSION_PROPERTY, permissionName))));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public void initialize(QCodeReference codeReference)
   {
      if(codeReference instanceof QCodeReferenceWithProperties withProperties)
      {
         this.permissionName = ValueUtils.getValueAsString(withProperties.getProperties().get(PERMISSION_PROPERTY));
      }
   }



   /*******************************************************************************
    ** Deny unless the session holds the permission (fail closed).
    *******************************************************************************/
   @Override
   public void checkPermissionsThrowing(AbstractActionInput actionInput, MetaDataWithPermissionRules metaDataWithPermissionRules) throws QPermissionDeniedException
   {
      QSession session = QContext.getQSession();
      if(permissionName == null || session == null || !session.hasPermission(permissionName))
      {
         throw (new QPermissionDeniedException("Permission denied."));
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public boolean handlesBuildAvailablePermission()
   {
      return (true);
   }



   /*******************************************************************************
    ** The exact permission name.
    *******************************************************************************/
   @Override
   public AvailablePermission buildAvailablePermission(QPermissionRules rules, PermissionSubType permissionSubType, String baseName, MetaDataWithName metaDataWithName, String objectType)
   {
      AvailablePermission permission = buildBaseAvailablePermission(rules, permissionSubType, metaDataWithName, objectType);
      return (permission == null || permissionName == null ? null : permission.withName(permissionName));
   }
}
