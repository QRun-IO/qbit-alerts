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
import com.kingsrook.qbits.alerts.AlertsRuntime;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.middleware.javalin.specs.BasicResponse;
import com.kingsrook.qqq.middleware.javalin.specs.extensions.AbstractJsonEndpointSpec;
import com.kingsrook.qqq.middleware.javalin.specs.v1.responses.BasicErrorResponseV1;
import com.kingsrook.qqq.openapi.model.Method;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;


/*******************************************************************************
 ** Shared JSON transport; AlertsApi owns authorization and behavior.
 *******************************************************************************/
abstract class AlertsJsonSpec extends AbstractJsonEndpointSpec
{
   /*******************************************************************************
    ** The stable operation identifier exposed to clients.
    *******************************************************************************/
   protected abstract String operationId();



   /*******************************************************************************
    ** Publish operation identifiers in both the fragment and served document.
    *******************************************************************************/
   @Override
   public Method defineMethod()
   {
      return (super.defineMethod().withOperationId(operationId()));
   }



   /*******************************************************************************
    ** Keep the existing Content-Type requirement, including empty POST bodies.
    *******************************************************************************/
   @Override
   public JsonInput buildInput(Context context) throws Exception
   {
      if(defineRequestBodySchema() != null && (context.contentType() == null || !context.contentType().toLowerCase(java.util.Locale.ROOT).startsWith("application/json")))
      {
         throw (new QBadRequestException("The request body must be JSON (Content-Type: application/json)."));
      }
      try
      {
         return (super.buildInput(context));
      }
      catch(QBadRequestException e)
      {
         throw (new QBadRequestException("The request body is not valid JSON."));
      }
   }



   /*******************************************************************************
    ** Framework error responses retain their existing HTTP status mapping.
    *******************************************************************************/
   @Override
   public List<BasicResponse> defineAdditionalBasicResponses()
   {
      return (List.of(new BasicResponse(HttpStatus.BAD_REQUEST, "Invalid input", BasicErrorResponseV1.class.getSimpleName()),
         new BasicResponse(HttpStatus.UNAUTHORIZED, "Sign in required", BasicErrorResponseV1.class.getSimpleName()),
         new BasicResponse(HttpStatus.FORBIDDEN, "Permission denied", BasicErrorResponseV1.class.getSimpleName()),
         new BasicResponse(HttpStatus.NOT_FOUND, "Not found", BasicErrorResponseV1.class.getSimpleName())));
   }



   /*******************************************************************************
    ** Resolve the runtime in the authenticated caller context.
    *******************************************************************************/
   protected AlertsApi api() throws QException
   {
      return (new AlertsApi(AlertsRuntime.get()));
   }
}
