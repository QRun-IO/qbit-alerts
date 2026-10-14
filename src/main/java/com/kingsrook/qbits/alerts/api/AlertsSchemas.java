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
import java.util.Map;
import java.util.TreeMap;
import com.kingsrook.qqq.middleware.javalin.schemabuilder.ToSchema;
import com.kingsrook.qqq.openapi.model.Schema;
import com.kingsrook.qqq.openapi.model.Type;


/*******************************************************************************
 ** Explicit versioned wire schemas, independent of persistence entities.
 *******************************************************************************/
public final class AlertsSchemas
{
   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private AlertsSchemas()
   {
   }



   /*******************************************************************************
    ** The complete component set shared by the six operations.
    *******************************************************************************/
   public static Map<String, Schema> components()
   {
      return (new TreeMap<>(Map.ofEntries(
         Map.entry("AlertsActionV1", new AlertsActionV1(null, null, null, null, null).toSchema()),
         Map.entry("AlertsInboxItemV1", new AlertsInboxItemV1(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null).toSchema()),
         Map.entry("AlertsPanelAlertV1", new AlertsPanelAlertV1(null, null, null, null, null, null, null, null, null).toSchema()),
         Map.entry("AlertsFollowV1", new AlertsFollowV1(null).toSchema()),
         Map.entry("AlertsInboxSummaryV1", new AlertsInboxSummaryV1(null, null, null, null, null, null, null, null, null).toSchema()),
         Map.entry("AlertsInboxPageV1", new AlertsInboxPageV1(null, null, null, null).toSchema()),
         Map.entry("AlertsRecordPanelV1", new AlertsRecordPanelV1(null, null, null, null, null, null, null, null).toSchema()),
         Map.entry("AlertsMutationResultV1", new AlertsMutationResultV1(null).toSchema()),
         Map.entry("AlertsInboxTargetV1", new AlertsInboxTargetV1(null, null, null, null).toSchema()),
         Map.entry("AlertsUnreadTargetV1", new AlertsUnreadTargetV1(null, null, null).toSchema()),
         Map.entry("AlertsErrorV1", new AlertsErrorV1(null).toSchema()))));
   }



   /*******************************************************************************
    ** An available action: operationId targets an inbox operation with this item id; process actions provide a complete href.
    *******************************************************************************/
   public record AlertsActionV1(String name, String label, String operationId, String processName, String href) implements ToSchema
   {
      /*******************************************************************************
       ** Describe the public JSON shape.
       *******************************************************************************/
      @Override
      public Schema toSchema()
      {
         return (new Schema().withType(Type.OBJECT).withDescription("An available action: operationId targets an inbox operation with this item id; process actions provide a complete href.").withProperties(new TreeMap<>(Map.ofEntries(
            Map.entry("name", new Schema().withType(Type.STRING)),
            Map.entry("label", new Schema().withType(Type.STRING)),
            Map.entry("operationId", new Schema().withType(Type.STRING)),
            Map.entry("processName", new Schema().withType(Type.STRING)),
            Map.entry("href", new Schema().withType(Type.STRING))))));
      }
   }



   /*******************************************************************************
    ** A notification recipient. body is included only in page items and banners; alertId only for ALERT notifications.
    *******************************************************************************/
   public record AlertsInboxItemV1(Integer id, Integer notificationId, String title, String body, String severity, String category, String source, String href, String linkLabel, String deliveredAt, String age, Boolean unread, Boolean sticky, Integer alertId, List<AlertsActionV1> actions) implements ToSchema
   {
      /*******************************************************************************
       ** Describe the public JSON shape.
       *******************************************************************************/
      @Override
      public Schema toSchema()
      {
         return (new Schema().withType(Type.OBJECT).withDescription("A notification recipient. body is included only in page items and banners; alertId only for ALERT notifications.").withProperties(new TreeMap<>(Map.ofEntries(
            Map.entry("id", new Schema().withType(Type.INTEGER)),
            Map.entry("notificationId", new Schema().withType(Type.INTEGER)),
            Map.entry("title", new Schema().withType(Type.STRING).withNullable(true)),
            Map.entry("body", new Schema().withType(Type.STRING).withNullable(true)),
            Map.entry("severity", new Schema().withType(Type.STRING).withNullable(true)),
            Map.entry("category", new Schema().withType(Type.STRING).withNullable(true)),
            Map.entry("source", new Schema().withType(Type.STRING).withNullable(true)),
            Map.entry("href", new Schema().withType(Type.STRING).withNullable(true).withDescription("Existing safe link, or null.")),
            Map.entry("linkLabel", new Schema().withType(Type.STRING).withNullable(true)),
            Map.entry("deliveredAt", new Schema().withType(Type.STRING).withNullable(true)),
            Map.entry("age", new Schema().withType(Type.STRING)),
            Map.entry("unread", new Schema().withType(Type.BOOLEAN)),
            Map.entry("sticky", new Schema().withType(Type.BOOLEAN)),
            Map.entry("alertId", new Schema().withType(Type.INTEGER).withNullable(true)),
            Map.entry("actions", new Schema().withType(Type.ARRAY).withItems(new Schema().withRefToSchema("AlertsActionV1")))))));
      }
   }



   /*******************************************************************************
    ** A visible open alert with permission-aware process actions.
    *******************************************************************************/
   public record AlertsPanelAlertV1(Integer alertId, String title, String severity, String status, String group, String age, String raisedAt, String href, List<AlertsActionV1> actions) implements ToSchema
   {
      /*******************************************************************************
       ** Describe the public JSON shape.
       *******************************************************************************/
      @Override
      public Schema toSchema()
      {
         return (new Schema().withType(Type.OBJECT).withDescription("A visible open alert with permission-aware process actions.").withProperties(new TreeMap<>(Map.ofEntries(
            Map.entry("alertId", new Schema().withType(Type.INTEGER)),
            Map.entry("title", new Schema().withType(Type.STRING).withNullable(true)),
            Map.entry("severity", new Schema().withType(Type.STRING).withNullable(true)),
            Map.entry("status", new Schema().withType(Type.STRING).withNullable(true)),
            Map.entry("group", new Schema().withType(Type.STRING).withNullable(true)),
            Map.entry("age", new Schema().withType(Type.STRING)),
            Map.entry("raisedAt", new Schema().withType(Type.STRING).withNullable(true)),
            Map.entry("href", new Schema().withType(Type.STRING)),
            Map.entry("actions", new Schema().withType(Type.ARRAY).withItems(new Schema().withRefToSchema("AlertsActionV1")))))));
      }
   }



   /*******************************************************************************
    ** Capability discovery only. Next obtains the follow URL from its collaboration module.
    *******************************************************************************/
   public record AlertsFollowV1(Boolean available) implements ToSchema
   {
      /*******************************************************************************
       ** Describe the public JSON shape.
       *******************************************************************************/
      @Override
      public Schema toSchema()
      {
         return (new Schema().withType(Type.OBJECT).withDescription("Capability discovery only. Next obtains the follow URL from its collaboration module.").withProperties(new TreeMap<>(Map.ofEntries(
            Map.entry("available", new Schema().withType(Type.BOOLEAN).withDescription("True exactly when the collaboration UI API extension is registered."))))));
      }
   }



   /*******************************************************************************
    ** Bell summary, latest ten items and up to three sticky banners.
    *******************************************************************************/
   public record AlertsInboxSummaryV1(Integer unreadCount, String highestUnreadSeverity, List<AlertsInboxItemV1> items, List<AlertsInboxItemV1> banners, String notificationsHref, String inboxHref, String preferencesSection, String alertsHref, Integer pollSeconds) implements ToSchema
   {
      /*******************************************************************************
       ** Describe the public JSON shape.
       *******************************************************************************/
      @Override
      public Schema toSchema()
      {
         return (new Schema().withType(Type.OBJECT).withDescription("Bell summary, latest ten items and up to three sticky banners.").withProperties(new TreeMap<>(Map.ofEntries(
            Map.entry("unreadCount", new Schema().withType(Type.INTEGER)),
            Map.entry("highestUnreadSeverity", new Schema().withType(Type.STRING).withNullable(true)),
            Map.entry("items", new Schema().withType(Type.ARRAY).withItems(new Schema().withRefToSchema("AlertsInboxItemV1"))),
            Map.entry("banners", new Schema().withType(Type.ARRAY).withItems(new Schema().withRefToSchema("AlertsInboxItemV1"))),
            Map.entry("notificationsHref", new Schema().withType(Type.STRING).withDescription("Existing notifications page link; retained for compatibility.")),
            Map.entry("preferencesSection", new Schema().withType(Type.STRING).withNullable(true).withDescription("Name of the user Preferences section for notification settings (/qqq/v1/preferences/{name}), or null when not registered.")),
            Map.entry("inboxHref", new Schema().withType(Type.STRING).withDescription("Canonical inbox navigation link.")),
            Map.entry("alertsHref", new Schema().withType(Type.STRING)),
            Map.entry("pollSeconds", new Schema().withType(Type.INTEGER))))));
      }
   }



   /*******************************************************************************
    ** Filtered notification page.
    *******************************************************************************/
   public record AlertsInboxPageV1(List<AlertsInboxItemV1> items, Integer total, Integer offset, Integer limit) implements ToSchema
   {
      /*******************************************************************************
       ** Describe the public JSON shape.
       *******************************************************************************/
      @Override
      public Schema toSchema()
      {
         return (new Schema().withType(Type.OBJECT).withDescription("Filtered notification page.").withProperties(new TreeMap<>(Map.ofEntries(
            Map.entry("items", new Schema().withType(Type.ARRAY).withItems(new Schema().withRefToSchema("AlertsInboxItemV1"))),
            Map.entry("total", new Schema().withType(Type.INTEGER)),
            Map.entry("offset", new Schema().withType(Type.INTEGER)),
            Map.entry("limit", new Schema().withType(Type.INTEGER))))));
      }
   }



   /*******************************************************************************
    ** Open alerts, recent resolutions and collaboration capability. watching is deprecated legacy subscription state.
    *******************************************************************************/
   public record AlertsRecordPanelV1(Boolean visible, Integer openCount, List<AlertsPanelAlertV1> open, Integer resolvedLast30Days, String viewAllHref, Boolean canManage, Boolean watching, AlertsFollowV1 follow) implements ToSchema
   {
      /*******************************************************************************
       ** Describe the public JSON shape.
       *******************************************************************************/
      @Override
      public Schema toSchema()
      {
         return (new Schema().withType(Type.OBJECT).withDescription("Open alerts, recent resolutions and collaboration capability. watching is deprecated legacy subscription state.").withProperties(new TreeMap<>(Map.ofEntries(
            Map.entry("visible", new Schema().withType(Type.BOOLEAN)),
            Map.entry("openCount", new Schema().withType(Type.INTEGER)),
            Map.entry("open", new Schema().withType(Type.ARRAY).withItems(new Schema().withRefToSchema("AlertsPanelAlertV1"))),
            Map.entry("resolvedLast30Days", new Schema().withType(Type.INTEGER)),
            Map.entry("viewAllHref", new Schema().withType(Type.STRING).withNullable(true)),
            Map.entry("canManage", new Schema().withType(Type.BOOLEAN)),
            Map.entry("watching", new Schema().withType(Type.BOOLEAN).withDescription("Deprecated: existing RECORD subscriptions only; does not represent collaboration follow state.")),
            Map.entry("follow", new Schema().withRefToSchema("AlertsFollowV1"))))));
      }
   }



   /*******************************************************************************
    ** Number of the caller own recipient rows changed.
    *******************************************************************************/
   public record AlertsMutationResultV1(Integer updated) implements ToSchema
   {
      /*******************************************************************************
       ** Describe the public JSON shape.
       *******************************************************************************/
      @Override
      public Schema toSchema()
      {
         return (new Schema().withType(Type.OBJECT).withDescription("Number of the caller own recipient rows changed.").withProperties(new TreeMap<>(Map.ofEntries(
            Map.entry("updated", new Schema().withType(Type.INTEGER))))));
      }
   }



   /*******************************************************************************
    ** Select id, nonempty ids (first 500), legacy alertId, or all:true in that precedence. All scans are capped at 1000 rows.
    *******************************************************************************/
   public record AlertsInboxTargetV1(Integer id, List<Integer> ids, Integer alertId, Boolean all) implements ToSchema
   {
      /*******************************************************************************
       ** Describe the public JSON shape.
       *******************************************************************************/
      @Override
      public Schema toSchema()
      {
         return (new Schema().withType(Type.OBJECT).withDescription("Select id, nonempty ids (first 500), legacy alertId, or all:true in that precedence. All scans are capped at 1000 rows.").withProperties(new TreeMap<>(Map.ofEntries(
            Map.entry("id", new Schema().withType(Type.INTEGER)),
            Map.entry("ids", new Schema().withType(Type.ARRAY).withItems(new Schema().withType(Type.INTEGER))),
            Map.entry("alertId", new Schema().withType(Type.INTEGER)),
            Map.entry("all", new Schema().withType(Type.BOOLEAN))))));
      }
   }



   /*******************************************************************************
    ** Select id or nonempty ids (first 500), in that precedence. Legacy alertId is also accepted for compatibility. all is not supported.
    *******************************************************************************/
   public record AlertsUnreadTargetV1(Integer id, List<Integer> ids, Integer alertId) implements ToSchema
   {
      /*******************************************************************************
       ** Describe the public JSON shape.
       *******************************************************************************/
      @Override
      public Schema toSchema()
      {
         return (new Schema().withType(Type.OBJECT).withDescription("Select id or nonempty ids (first 500), in that precedence. Legacy alertId is also accepted for compatibility. all is not supported.").withProperties(new TreeMap<>(Map.ofEntries(
            Map.entry("id", new Schema().withType(Type.INTEGER)),
            Map.entry("ids", new Schema().withType(Type.ARRAY).withItems(new Schema().withType(Type.INTEGER))),
            Map.entry("alertId", new Schema().withType(Type.INTEGER))))));
      }
   }



   /*******************************************************************************
    ** Framework error response.
    *******************************************************************************/
   public record AlertsErrorV1(String error) implements ToSchema
   {
      /*******************************************************************************
       ** Describe the public JSON shape.
       *******************************************************************************/
      @Override
      public Schema toSchema()
      {
         return (new Schema().withType(Type.OBJECT).withDescription("Framework error response.").withProperties(new TreeMap<>(Map.ofEntries(
            Map.entry("error", new Schema().withType(Type.STRING).withNullable(true))))));
      }
   }
}
