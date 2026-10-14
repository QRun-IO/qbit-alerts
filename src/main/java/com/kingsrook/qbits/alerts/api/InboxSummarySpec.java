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


import java.util.Map;
import com.kingsrook.qqq.middleware.javalin.specs.BasicOperation;
import com.kingsrook.qqq.openapi.model.HttpMethod;
import com.kingsrook.qqq.openapi.model.Schema;


/*******************************************************************************
 ** Read the bell summary.
 *******************************************************************************/
public class InboxSummarySpec extends AlertsJsonSpec
{
   public static final String OPERATION_ID = "alertsInboxSummary";



   /*******************************************************************************
    ** Describe the established method and path.
    *******************************************************************************/
   @Override
   public BasicOperation defineBasicOperation()
   {
      return (new BasicOperation().withHttpMethod(HttpMethod.GET).withPath("/alerts/inbox/summary")
         .withShortSummary("Read the bell summary").withLongDescription("Uses the signed-in caller's permissions and record visibility."));
   }



   /*******************************************************************************
    ** Stable identifier for generated clients.
    *******************************************************************************/
   @Override
   protected String operationId()
   {
      return (OPERATION_ID);
   }



   /*******************************************************************************
    ** Versioned response schema.
    *******************************************************************************/
   @Override
   protected String getResponseSchemaName()
   {
      return ("AlertsInboxSummaryV1");
   }



   /*******************************************************************************
    ** Publish shared components once; all six extension specs register together.
    *******************************************************************************/
   @Override
   public Map<String, Schema> defineComponentSchemas()
   {
      return (AlertsSchemas.components());
   }



   /*******************************************************************************
    ** Invoke the existing service with the caller session.
    *******************************************************************************/
   @Override
   protected Object handle(JsonInput input) throws Exception
   {
      return (api().inboxSummary());
   }
}
