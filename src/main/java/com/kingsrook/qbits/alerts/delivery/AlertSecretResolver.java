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

package com.kingsrook.qbits.alerts.delivery;


import com.kingsrook.qqq.backend.core.instances.QMetaDataVariableInterpreter;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Resolves a channel's secretRef at send time (design section 7.3). The
 ** reference is a name, never a value: an environment variable name
 ** (SLACK_ALERTS_WEBHOOK) or a QQQ variable expression (${env.NAME},
 ** ${prop.name}), which also covers Kubernetes secrets injected as
 ** environment variables and .env files. Values are never stored, logged or
 ** shown.
 *******************************************************************************/
public class AlertSecretResolver
{
   private final QMetaDataVariableInterpreter interpreter;



   /*******************************************************************************
    ** Constructor
    *******************************************************************************/
   public AlertSecretResolver()
   {
      this(new QMetaDataVariableInterpreter());
   }



   /*******************************************************************************
    ** Constructor (tests can pass an interpreter with value maps)
    *******************************************************************************/
   public AlertSecretResolver(QMetaDataVariableInterpreter interpreter)
   {
      this.interpreter = interpreter;
   }



   /*******************************************************************************
    ** The secret's value, or null when the reference is blank or unresolved.
    *******************************************************************************/
   public String resolve(String secretRef)
   {
      if(!StringUtils.hasContent(secretRef))
      {
         return (null);
      }
      String expression = secretRef.trim().startsWith("${") ? secretRef.trim() : "${env." + secretRef.trim() + "}";
      String value      = interpreter.interpret(expression);
      if(value == null || value.equals(expression) || value.isBlank())
      {
         return (null);
      }
      return (value);
   }



   /*******************************************************************************
    ** "present" or "missing", for display.
    *******************************************************************************/
   public String describe(String secretRef)
   {
      if(!StringUtils.hasContent(secretRef))
      {
         return ("none");
      }
      return (resolve(secretRef) == null ? "missing" : "present");
   }
}
