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

package com.kingsrook.qbits.alerts.api;


import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.middleware.javalin.specs.extensions.MiddlewareSpecExtension;
import com.kingsrook.qqq.middleware.javalin.specs.extensions.MiddlewareSpecExtensionsMetaData;


/*******************************************************************************
 ** The alerts UI API, retaining the established /alerts namespace.
 *******************************************************************************/
public final class AlertsUiApi
{
   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private AlertsUiApi()
   {
   }



   /*******************************************************************************
    ** Register once, alongside the optional Javalin webhook provider.
    *******************************************************************************/
   public static void register(QInstance instance)
   {
      MiddlewareSpecExtensionsMetaData.ofOrWithNew(instance).withExtension(extension());
   }



   /*******************************************************************************
    ** The independently versioned, discoverable UI contract.
    *******************************************************************************/
   public static MiddlewareSpecExtension extension()
   {
      return (new MiddlewareSpecExtension().withName("alerts").withPathPrefix("/alerts")
         .withSemanticVersion("1.2.0").withDescription("Alerts inbox and record panel")
         .withSpec(InboxSummarySpec.class).withSpec(InboxSpec.class)
         .withSpec(InboxReadSpec.class).withSpec(InboxUnreadSpec.class)
         .withSpec(InboxDismissSpec.class).withSpec(RecordPanelSpec.class));
   }
}
