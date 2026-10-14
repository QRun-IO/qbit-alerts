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


import java.util.List;
import com.kingsrook.qqq.middleware.javalin.specs.BasicOperation;
import com.kingsrook.qqq.openapi.model.HttpMethod;
import com.kingsrook.qqq.openapi.model.In;
import com.kingsrook.qqq.openapi.model.Parameter;
import com.kingsrook.qqq.openapi.model.Schema;
import com.kingsrook.qqq.openapi.model.Type;


/*******************************************************************************
 ** Read visible alerts for a record.
 *******************************************************************************/
public class RecordPanelSpec extends AlertsJsonSpec
{
   public static final String OPERATION_ID = "alertsRecordPanel";



   /*******************************************************************************
    ** Describe the established method and path.
    *******************************************************************************/
   @Override
   public BasicOperation defineBasicOperation()
   {
      return (new BasicOperation().withHttpMethod(HttpMethod.GET).withPath("/alerts/record-panel")
         .withShortSummary("Read visible alerts for a record").withLongDescription("Uses the signed-in caller's permissions and record visibility."));
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
      return ("AlertsRecordPanelV1");
   }



   /*******************************************************************************
    ** Existing query parameters and paging semantics.
    *******************************************************************************/
   @Override
   public List<Parameter> defineRequestParameters()
   {
      return (List.of(
         new Parameter().withName("tableName").withIn(In.QUERY).withRequired(true).withSchema(new Schema().withType(Type.STRING)).withDescription("Parent table name."),
         new Parameter().withName("recordId").withIn(In.QUERY).withRequired(true).withSchema(new Schema().withType(Type.STRING)).withDescription("Parent record key.")));
   }



   /*******************************************************************************
    ** Invoke the existing service with the caller session.
    *******************************************************************************/
   @Override
   protected Object handle(JsonInput input) throws Exception
   {
      return (api().recordPanel(input.getQueryParam("tableName"), input.getQueryParam("recordId")));
   }
}
