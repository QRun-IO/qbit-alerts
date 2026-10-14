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


import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.alerts.api.AlertsRouteProvider;
import com.kingsrook.qqq.backend.core.alerts.AlertLink;
import com.kingsrook.qqq.backend.core.alerts.AlertRecordRef;
import com.kingsrook.qqq.backend.core.alerts.AlertSignal;
import com.kingsrook.qqq.backend.core.alerts.NotificationAudience;
import com.kingsrook.qqq.backend.core.alerts.NotificationCategory;
import com.kingsrook.qqq.backend.core.alerts.NotificationSignal;
import com.kingsrook.qqq.backend.core.alerts.QAlerts;
import com.kingsrook.qqq.backend.core.instances.AbstractQQQApplication;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.middleware.javalin.QApplicationJavalinServer;
import com.kingsrook.qqq.middleware.javalin.specs.UiApiRouteAuditor;
import com.kingsrook.qqq.middleware.javalin.specs.v1.MiddlewareVersionV1;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Real HTTP coverage of the extension, permission boundaries and machine webhook.
 *******************************************************************************/
class AlertsApiHttpTest extends BaseAlertsTest
{
   private static final String TOKEN_PROPERTY = "alerts.test.http.token";
   private static final String TOKEN = "test-webhook-token";
   private QApplicationJavalinServer server;
   private int port;



   /*******************************************************************************
    ** Give the HTTP mock session the permissions of its test cookie identity.
    *******************************************************************************/
   public static class HttpSessionCustomizer extends TestSessionCustomizer
   {
      /***************************************************************************
       ** Test identities are carried in sessionUUID cookies.
       ***************************************************************************/
      @Override
      public void customizeSession(QInstance instance, QSession session, Map<String, Object> context)
      {
         QSession granted = sessionFor(session.getUuid());
         session.setUser(granted.getUser());
         session.setPermissions(granted.getPermissions());
         AlertsSecurity.applyToSession(instance, session);
      }
   }



   /*******************************************************************************
    ** Shut down even after a failed assertion or startup.
    *******************************************************************************/
   @AfterEach
   void stopServer()
   {
      if(server != null)
      {
         server.stop();
      }
      System.clearProperty(TOKEN_PROPERTY);
   }



   /*******************************************************************************
    ** Every application server runs strict; the legacy alias is tested separately.
    *******************************************************************************/
   private void start() throws Exception
   {
      qInstance.getAuthentication().setCustomizer(new QCodeReference(HttpSessionCustomizer.class));
      server = new QApplicationJavalinServer(new AbstractQQQApplication()
      {
         /***************************************************************************
          ** Use the test database and produced metadata.
          ***************************************************************************/
         @Override
         public QInstance defineQInstance()
         {
            return (qInstance);
         }
      }).withPort(0).withServeFrontendNext(false).withServeFrontendMaterialDashboard(false)
         .withServeLegacyUnversionedMiddlewareAPI(false).withStrictUiApiRoutes(true)
         .withMiddlewareVersionList(List.of(new MiddlewareVersionV1()));
      server.start();
      port = server.getBoundPort();
   }



   /*******************************************************************************
    ** Send through Javalin with an explicit user and optional bearer authorization.
    *******************************************************************************/
   private HttpResponse<String> request(String method, String path, String body, String user, String authorization) throws Exception
   {
      HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
         .header("Cookie", "sessionUUID=" + user);
      if(authorization != null)
      {
         request.header("Authorization", authorization);
      }
      if(body != null)
      {
         request.header("Content-Type", "application/json");
      }
      request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
      try(HttpClient client = HttpClient.newHttpClient())
      {
         return (client.send(request.build(), HttpResponse.BodyHandlers.ofString()));
      }
   }



   /*******************************************************************************
    ** Parse success bodies while reporting any unexpected framework failure.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   private Map<String, Object> json(HttpResponse<String> response) throws Exception
   {
      assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
      return (JsonUtils.toObject(response.body(), Map.class));
   }



   /*******************************************************************************
    ** A core producer reaches the strict HTTP inbox through the background sink.
    *******************************************************************************/
   @Test
   @SuppressWarnings("unchecked")
   void coreNotificationReachesInbox() throws Exception
   {
      config.withSynchronousSink(false);
      start();
      long failures = QAlerts.getProducerFailureCount();
      QAlerts.notify(new NotificationSignal("Shipment delivered").withBody("Your shipment arrived.")
         .withCategory(NotificationCategory.JOB).withSourceName("easypost-tracking")
         .withLink(AlertLink.toRecord(WIDGET, 5, "Shipment"))
         .withAudience(NotificationAudience.user("viewer-1")));
      runtime.getSink().stop();

      String base = "/qqq/v1/alerts/inbox";
      Map<String, Object> summary = json(request("GET", base + "/summary", null, "viewer-1", null));
      assertThat(summary).containsEntry("unreadCount", 1).containsEntry("highestUnreadSeverity", "INFO");
      List<Map<String, Object>> items = (List<Map<String, Object>>) summary.get("items");
      assertThat(items).singleElement().satisfies(item -> assertThat(item)
         .containsEntry("title", "Shipment delivered").containsEntry("category", "JOB")
         .containsEntry("source", "easypost-tracking").containsEntry("href", "/app/" + WIDGET + "/5"));
      Map<String, Object> page = json(request("GET", base, null, "viewer-1", null));
      assertThat((List<Map<String, Object>>) page.get("items")).singleElement()
         .satisfies(item -> assertThat(item).containsEntry("body", "Your shipment arrived."));
      assertThat(json(request("GET", base + "/summary", null, "viewer-2", null))).containsEntry("unreadCount", 0);
      assertThat(QAlerts.getProducerFailureCount()).isEqualTo(failures);
   }



   /*******************************************************************************
    ** Existing paths, selectors, paging, null fields and additive client affordances.
    *******************************************************************************/
   @Test
   @SuppressWarnings("unchecked")
   void strictUiRoutes() throws Exception
   {
      new com.kingsrook.qbits.alerts.api.AlertsApi(runtime).inboxSummary();
      Integer widget = insertWidgets("BROKEN").get(0);
      var alert = runtime.getEngine().raise(new AlertSignal("test.http.problem").withDedupKey("http")
         .withTitle("Needs attention").withSeverity(com.kingsrook.qqq.backend.core.alerts.AlertSeverity.ERROR).withRecordRef(AlertRecordRef.of(WIDGET, widget)));
      start();
      String base = "/qqq/v1/alerts";
      Map<String, Object> summary = json(request("GET", base + "/inbox/summary", null, ADMIN, null));
      assertThat(summary).containsEntry("inboxHref", "/app/notifications").containsEntry("notificationsHref", "/app/notifications");
      List<Map<String, Object>> items = (List<Map<String, Object>>) summary.get("items");
      assertThat(items).hasSize(1);
      Map<String, Object> item = items.get(0);
      assertThat(item).containsKeys("href", "actions", "alertId");
      assertThat((List<Map<String, Object>>) item.get("actions")).extracting(a -> a.get("operationId")).containsExactly("alertsInboxRead", "alertsInboxDismiss");
      String target = JsonUtils.toJson(Map.of("id", item.get("id")));
      assertThat(json(request("POST", base + "/inbox/read", target, ADMIN, null))).containsEntry("updated", 1);
      assertThat(json(request("POST", base + "/inbox/unread", target, ADMIN, null))).containsEntry("updated", 1);
      assertThat(json(request("GET", base + "/inbox?status=unread&offset=bad&limit=1000", null, ADMIN, null)))
         .containsEntry("total", 1).containsEntry("offset", 0).containsEntry("limit", 100);
      Map<String, Object> panel = json(request("GET", base + "/record-panel?tableName=widget&recordId=" + widget, null, ADMIN, null));
      assertThat(panel).containsEntry("follow", Map.of("available", false)).containsEntry("watching", false);
      Map<String, Object> row = ((List<Map<String, Object>>) panel.get("open")).get(0);
      assertThat((List<Map<String, Object>>) row.get("actions")).extracting(a -> a.get("processName")).containsExactly("alertsAcknowledge", "alertsSnooze");
      assertThat(((List<Map<String, Object>>) row.get("actions")).get(0).get("href"))
         .isEqualTo("/app/alertsAcknowledge?recordsParam=recordIds&recordIds=" + alert.getValue("id") + "&tableName=alerts_alert");
      Map<String, Object> viewerPanel = json(request("GET", base + "/record-panel?tableName=widget&recordId=" + widget, null, VIEWER, null));
      assertThat(((List<Map<String, Object>>) viewerPanel.get("open")).get(0).get("actions")).isEqualTo(List.of());
      assertThat(json(request("POST", base + "/inbox/dismiss", target, ADMIN, null))).containsEntry("updated", 1);
      assertThat(json(request("GET", base + "/inbox/summary", null, ADMIN, null))).containsEntry("highestUnreadSeverity", null);
      assertThat(request("POST", base + "/record-panel/watch", "{}", ADMIN, null).statusCode()).isEqualTo(404);
      HttpResponse<String> invalidJson = request("POST", base + "/inbox/read", "not json", ADMIN, null);
      assertThat(invalidJson.statusCode()).isEqualTo(400);
      assertThat(invalidJson.body()).contains("The request body is not valid JSON.");
      assertThat(request("POST", base + "/inbox/read", null, ADMIN, null).statusCode()).isEqualTo(400);
      assertThat(request("GET", base + "/inbox/summary", null, "nobody", null).statusCode()).isEqualTo(403);
      assertThat(request("GET", base + "/record-panel?tableName=widget&recordId=1", null, OUTSIDER, null).statusCode()).isEqualTo(403);
      String openapi = request("GET", "/qqq/v1/openapi.yaml", null, ADMIN, null).body();
      assertThat(openapi).contains("alertsInboxSummary", "alertsRecordPanel").doesNotContain("/record-panel/watch", "/ingest/alertmanager");
   }



   /*******************************************************************************
    ** The canonical webhook uses the same secret and body validation outside v1.
    *******************************************************************************/
   @Test
   void canonicalWebhook() throws Exception
   {
      System.setProperty(TOKEN_PROPERTY, TOKEN);
      config.withAlertmanagerIngestTokenSecretRef("${prop." + TOKEN_PROPERTY + "}");
      start();
      assertThat(request("POST", AlertsRouteProvider.BASE_PATH + "/ingest/alertmanager", "{}", ADMIN, "Bearer " + TOKEN).statusCode()).isEqualTo(404);
      assertThat(request("POST", AlertsRouteProvider.WEBHOOK_PATH, "{}", "nobody", null).statusCode()).isEqualTo(401);
      assertThat(request("POST", AlertsRouteProvider.WEBHOOK_PATH, "{}", "nobody", "Bearer wrong").statusCode()).isEqualTo(401);
      HttpResponse<String> response = request("POST", AlertsRouteProvider.WEBHOOK_PATH, "{\"alerts\":[]}", "nobody", "Bearer " + TOKEN);
      assertThat(json(response)).containsEntry("raised", 0).containsEntry("resolved", 0).containsEntry("ignored", 0);
      assertThat(response.headers().firstValue("Deprecation")).isEmpty();
      assertThat(request("POST", AlertsRouteProvider.WEBHOOK_PATH, "[]", "nobody", "Bearer " + TOKEN).statusCode()).isEqualTo(400);
   }



   /*******************************************************************************
    ** Test the migration-only raw provider and prove strict mode refuses its alias.
    *******************************************************************************/
   @Test
   void legacyWebhook() throws Exception
   {
      System.setProperty(TOKEN_PROPERTY, TOKEN);
      config.withAlertmanagerIngestTokenSecretRef("${prop." + TOKEN_PROPERTY + "}").withLegacyAlertmanagerPath(true);
      AlertsRouteProvider provider = new AlertsRouteProvider();
      provider.setQInstance(qInstance);
      Javalin app = Javalin.create(provider::acceptJavalinConfig).start(0);
      try
      {
         port = app.port();
         String alias = AlertsRouteProvider.BASE_PATH + "/ingest/alertmanager";
         for(String path : List.of(alias, AlertsRouteProvider.WEBHOOK_PATH))
         {
            assertThat(request("POST", path, "{}", "nobody", "Bearer wrong").statusCode()).isEqualTo(401);
            HttpResponse<String> response = request("POST", path, "{\"alerts\":[]}", "nobody", "Bearer " + TOKEN);
            assertThat(json(response)).containsEntry("raised", 0);
            if(path.equals(alias))
            {
               assertThat(response.headers().firstValue("Deprecation")).contains("true");
               assertThat(response.headers().firstValue("Sunset")).contains(AlertsRouteProvider.LEGACY_SUNSET);
            }
         }
         assertThatThrownBy(() -> UiApiRouteAuditor.audit(app, List.of(new MiddlewareVersionV1()), true))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining(alias);
      }
      finally
      {
         app.stop();
      }
   }
}
