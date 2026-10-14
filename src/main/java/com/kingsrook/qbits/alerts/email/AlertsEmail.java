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

package com.kingsrook.qbits.alerts.email;


import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;


/*******************************************************************************
 ** The guard for sending through qbit-email. qbit-email is an optional
 ** dependency: this class (which names no qbit-email class) answers whether the
 ** email qbit is on the classpath AND produced in the current instance. Only
 ** then does the rest of the qbit call {@link AlertsEmailSender}; otherwise
 ** email goes through core SendMessageAction and messagingProviderName.
 *******************************************************************************/
public final class AlertsEmail
{
   private static final String EMAIL_SERVICE_CLASS = "com.kingsrook.qbits.email.outbox.EmailService";



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private AlertsEmail()
   {
   }



   /*******************************************************************************
    ** Whether email goes through qbit-email in the current instance.
    *******************************************************************************/
   public static boolean isActive()
   {
      return (isActive(QContext.getQInstance()));
   }



   /*******************************************************************************
    ** Whether email goes through qbit-email in an instance.
    *******************************************************************************/
   public static boolean isActive(QInstance qInstance)
   {
      if(qInstance == null)
      {
         return (false);
      }
      try
      {
         Class.forName(EMAIL_SERVICE_CLASS);
      }
      catch(ClassNotFoundException | LinkageError e)
      {
         return (false);
      }
      return (AlertsEmailSender.isInstalled(qInstance));
   }
}
