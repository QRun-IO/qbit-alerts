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
import com.kingsrook.qbits.alerts.AlertsRuntime;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qqq.middleware.javalin.QJavalinMetaData;
import com.kingsrook.qqq.middleware.javalin.QJavalinRouteProviderInterface;
import com.kingsrook.qqq.middleware.javalin.QJavalinUtils;
import io.javalin.config.JavalinConfig;
import io.javalin.http.ContentType;
import io.javalin.http.Context;


/*******************************************************************************
 ** Machine webhook routes. The UI routes are registered through AlertsUiApi.
 *******************************************************************************/
public class AlertsRouteProvider implements QJavalinRouteProviderInterface
{
   private static final QLogger LOG = QLogger.getLogger(AlertsRouteProvider.class);

   public static final String WEBHOOK_PATH = "/qqq/hooks/alerts/alertmanager";
   public static final String LEGACY_SUNSET = "Tue, 13 Apr 2027 00:00:00 GMT";
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
      config.routes.post(WEBHOOK_PATH, this::ingestAlertmanager);
      if(Boolean.TRUE.equals(AlertsRuntime.find(qInstance).getConfig().getLegacyAlertmanagerPath()))
      {
         config.routes.post(BASE_PATH + "/ingest/alertmanager", context ->
         {
            context.header("Deprecation", "true");
            context.header("Sunset", LEGACY_SUNSET);
            LOG.warn("Deprecated Alertmanager webhook called; migrate to " + WEBHOOK_PATH);
            ingestAlertmanager(context);
         });
      }
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



}
