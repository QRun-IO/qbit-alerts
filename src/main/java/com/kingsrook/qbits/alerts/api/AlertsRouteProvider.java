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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.kingsrook.qbits.alerts.AlertsRuntime;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qqq.middleware.javalin.QJavalinMetaData;
import com.kingsrook.qqq.middleware.javalin.QJavalinRouteProviderInterface;
import com.kingsrook.qqq.middleware.javalin.QJavalinUtils;
import com.kingsrook.qqq.middleware.javalin.executors.ExecutorSessionUtils;
import io.javalin.config.JavalinConfig;
import io.javalin.http.ContentType;
import io.javalin.http.Context;


/*******************************************************************************
 ** Javalin routes under /qqq/v1/alerts for the Next UI bell and record panel:
 ** - GET  /inbox/summary   unread count, highest unread severity, latest 10
 ** - POST /inbox/read      {"alertId": n} or {"all": true}
 ** - GET  /record-panel?tableName=t&amp;recordId=id   the "Open alerts" panel
 ** - POST /record-panel/watch   {"tableName": t, "recordId": id}
 ** - POST /ingest/alertmanager  Alertmanager's webhook (bearer token; off
 **   unless alertmanagerIngestTokenSecretRef is configured)
 ** Each request gets a session from the instance's authentication module;
 ** AlertsApi checks alerts.view and works in that session. Errors map
 ** through QJavalinUtils.handleException (400, 401, 403, 404). POST bodies
 ** must be JSON.
 *******************************************************************************/
public class AlertsRouteProvider implements QJavalinRouteProviderInterface
{
   public static final String BASE_PATH = "/qqq/v1/alerts";

   static final int MAX_INGEST_BYTES = 1024 * 1024;

   private QInstance qInstance;



   /*******************************************************************************
    ** Register this provider once with the instance's Javalin meta-data.
    *******************************************************************************/
   public static void register(QInstance qInstance)
   {
      QJavalinMetaData javalinMetaData = QJavalinMetaData.ofOrWithNew(qInstance);
      boolean registered = CollectionUtils.nonNullList(javalinMetaData.getAdditionalRouteProviderReferences()).stream()
         .anyMatch(reference -> reference != null && AlertsRouteProvider.class.getName().equals(reference.getName()));
      if(!registered)
      {
         javalinMetaData.withAdditionalRouteProviderReference(new QCodeReference(AlertsRouteProvider.class));
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public void setQInstance(QInstance qInstance)
   {
      this.qInstance = qInstance;
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public void acceptJavalinConfig(JavalinConfig config)
   {
      config.routes.get(BASE_PATH + "/inbox/summary", context -> respond(context, false, (api, body) -> api.inboxSummary()));
      config.routes.post(BASE_PATH + "/inbox/read", context -> respond(context, true, AlertsApi::markRead));
      config.routes.get(BASE_PATH + "/record-panel", context -> respond(context, false, (api, body) -> api.recordPanel(context.queryParam("tableName"), context.queryParam("recordId"))));
      config.routes.post(BASE_PATH + "/record-panel/watch", context -> respond(context, true, AlertsApi::watchRecord));
      config.routes.post(BASE_PATH + "/ingest/alertmanager", this::ingestAlertmanager);
   }



   /*******************************************************************************
    ** Alertmanager's webhook (no QQQ session: a bearer token instead).
    *******************************************************************************/
   private void ingestAlertmanager(Context context)
   {
      try
      {
         if(context.body().length() > MAX_INGEST_BYTES)
         {
            throw (new QBadRequestException("The payload is larger than 1 MB."));
         }
         com.kingsrook.qbits.alerts.engine.AlertmanagerIngest ingest = new com.kingsrook.qbits.alerts.engine.AlertmanagerIngest(AlertsRuntime.get(qInstance));
         ingest.authenticate(context.header("Authorization"));
         com.kingsrook.qbits.alerts.engine.AlertmanagerIngest.Result result = ingest.ingest(readJsonBody(context));
         context.contentType(ContentType.APPLICATION_JSON);
         context.result(JsonUtils.toJson(Map.of("raised", result.raised(), "resolved", result.resolved(), "ignored", result.ignored())));
      }
      catch(Exception e)
      {
         QJavalinUtils.handleException(null, context, e);
      }
      finally
      {
         QContext.clear();
      }
   }



   /*******************************************************************************
    ** Session, body, handler, JSON - or the error's status.
    *******************************************************************************/
   private void respond(Context context, boolean readsBody, Handler handler)
   {
      try
      {
         ExecutorSessionUtils.setupSession(context, qInstance);
         Map<String, Object> body = readsBody ? readJsonBody(context) : Map.of();
         Object              result = handler.handle(new AlertsApi(AlertsRuntime.get(qInstance)), body);
         context.contentType(ContentType.APPLICATION_JSON);
         context.result(JsonUtils.toJsonCustomized(result, builder -> builder.serializationInclusion(JsonInclude.Include.ALWAYS)));
      }
      catch(Exception e)
      {
         QJavalinUtils.handleException(null, context, e);
      }
      finally
      {
         QContext.clear();
      }
   }



   /*******************************************************************************
    ** The JSON body (an object), or 400.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   private static Map<String, Object> readJsonBody(Context context) throws QBadRequestException
   {
      String contentType = context.contentType();
      if(contentType == null || !contentType.toLowerCase().startsWith("application/json"))
      {
         throw (new QBadRequestException("The request body must be JSON (Content-Type: application/json)."));
      }
      String body = context.body();
      if(!StringUtils.hasContent(body))
      {
         return (Map.of());
      }
      try
      {
         return (JsonUtils.toObject(body, Map.class));
      }
      catch(Exception e)
      {
         throw (new QBadRequestException("The request body is not valid JSON."));
      }
   }



   /*******************************************************************************
    ** One endpoint's work.
    *******************************************************************************/
   @FunctionalInterface
   interface Handler
   {
      /***************************************************************************
       **
       ***************************************************************************/
      Object handle(AlertsApi api, Map<String, Object> body) throws Exception;
   }
}
