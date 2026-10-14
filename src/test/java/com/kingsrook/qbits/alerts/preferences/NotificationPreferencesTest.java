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

package com.kingsrook.qbits.alerts.preferences;


import java.io.Serializable;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.alerts.AlertsQBitConfig;
import com.kingsrook.qbits.alerts.AlertsQBitProducer;
import com.kingsrook.qbits.alerts.BaseAlertsTest;
import com.kingsrook.qbits.alerts.api.AlertsApi;
import com.kingsrook.qbits.alerts.engine.AlertsDb;
import com.kingsrook.qbits.alerts.model.AlertsNotificationPreference;
import com.kingsrook.qbits.alerts.model.AlertsSubscription;
import com.kingsrook.qbits.alerts.model.AlertsUserPreference;
import com.kingsrook.qqq.backend.core.actions.preferences.UserPreferencesAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QNotFoundException;
import com.kingsrook.qqq.backend.core.instances.QInstanceEnricher;
import com.kingsrook.qqq.backend.core.model.audits.AuditsMetaDataProvider;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.MetaDataProducerMultiOutput;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceChange;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceRequestContext;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceRowChange;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferencesMetaData;
import com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSBackendMetaData;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Real core action round trips, ownership, validation and field auditing.
 *******************************************************************************/
class NotificationPreferencesTest extends BaseAlertsTest
{
   private final UserPreferencesAction action = new UserPreferencesAction();



   /*******************************************************************************
    ** Defaults, all settings, partial changes and isolation persist in real tables.
    *******************************************************************************/
   @Test
   void roundTrip() throws Exception
   {
      QRecord mine = subscription(VIEWER, "USER", "ALL");
      QRecord other = subscription("viewer-2", "USER", "GROUP");
      subscription(VIEWER, "TEAM", "ALL");
      loginAs("viewer-2");
      action.patchSection("notifications", Map.of("values", Map.of("digestHour", 7), "lists", Map.of("categories", List.of(row("JOB", Map.of("emailMode", "IMMEDIATE"))))));
      Map<String, Serializable> otherSettings = new HashMap<>(all(AlertsUserPreference.TABLE_NAME).get(0).getValues());
      Map<String, Serializable> otherCategory = new HashMap<>(all(AlertsNotificationPreference.TABLE_NAME).get(0).getValues());
      loginAs(VIEWER).setPermissions(Set.of(AlertsQBitConfig.PERMISSION_VIEW));
      qInstance.setEnforcePermissionsInCoreActions(true);
      var defaults = action.getSection("notifications");
      assertThat(defaults.getSection().getAppName()).isNull();
      assertThat(defaults.getValues().getValues()).containsEntry("digestHour", 8).containsEntry("alertInAppEnabled", true).containsEntry("alertEmailEnabled", true);
      assertThat(defaults.getValues().getLists().get("categories")).hasSize(7).allSatisfy(category ->
      {
         assertThat(category.getValues()).containsEntry("inApp", true).containsEntry("inAppMinSeverity", "INFO").containsEntry("emailMode", "OFF").containsEntry("emailMinSeverity", "WARNING");
      });
      assertThat(defaults.getValues().getLists().get("subscriptions")).singleElement().satisfies(rule -> assertThat(rule.getLabel()).isEqualTo("All alerts"));
      action.patchSection("notifications", Map.of("values", Map.of("pausedUntil", "2026-11-01T12:00:00Z", "digestHour", 22, "timeZone", "America/Chicago",
         "alertInAppEnabled", false, "alertEmailEnabled", false), "lists", Map.of(
            "categories", List.of(row("JOB", Map.of("inApp", false, "inAppMinSeverity", "ERROR", "emailMode", "DAILY_DIGEST", "emailMinSeverity", "CRITICAL"))),
            "subscriptions", List.of(row(mine.getValueString("id"), Map.of("enabled", false, "minSeverity", "ERROR", "deliveryMode", "DIGEST_DAILY"))))));
      var loaded = action.getSection("notifications").getValues();
      assertThat(loaded.getValues()).containsEntry("digestHour", 22).containsEntry("timeZone", "America/Chicago")
         .containsEntry("pausedUntil", Instant.parse("2026-11-01T12:00:00Z")).containsEntry("alertInAppEnabled", false).containsEntry("alertEmailEnabled", false);
      assertThat(where(AlertsUserPreference.TABLE_NAME, AlertsDb.eq("userId", VIEWER))).singleElement().satisfies(record ->
         assertThat(record.getValues()).containsEntry("inAppEnabled", false).containsEntry("emailEnabled", false).containsEntry("quietBypassSeverity", "CRITICAL"));
      assertThat(where(AlertsNotificationPreference.TABLE_NAME, AlertsDb.eq("userId", VIEWER))).singleElement().satisfies(record ->
         assertThat(record.getValues()).containsEntry("category", "JOB").containsEntry("inAppMuted", true).containsEntry("inAppMinSeverity", "ERROR")
            .containsEntry("emailMode", "DAILY_DIGEST").containsEntry("emailMinSeverity", "CRITICAL"));
      assertThat(where(AlertsSubscription.TABLE_NAME, AlertsDb.eq("id", mine.getValue("id")))).singleElement().satisfies(record ->
         assertThat(record.getValues()).containsEntry("enabled", false).containsEntry("minSeverity", "ERROR").containsEntry("deliveryMode", "DIGEST_DAILY"));
      Map<String, Object> clearMinimum = new HashMap<>();
      clearMinimum.put("inAppMinSeverity", null);
      action.patchSection("notifications", Map.of("lists", Map.of("categories", List.of(row("JOB", clearMinimum)))));
      assertThat(action.getSection("notifications").getValues().getLists().get("categories")).filteredOn(row -> row.getKey().equals("JOB"))
         .singleElement().satisfies(row -> assertThat(row.getValues()).containsEntry("inAppMinSeverity", "INFO").containsEntry("inApp", false));
      assertThat(where(AlertsUserPreference.TABLE_NAME, AlertsDb.eq("userId", "viewer-2")).get(0).getValues()).isEqualTo(otherSettings);
      assertThat(where(AlertsNotificationPreference.TABLE_NAME, AlertsDb.eq("userId", "viewer-2")).get(0).getValues()).isEqualTo(otherCategory);
      loginAs("viewer-2");
      assertThat(action.getSection("notifications").getValues().getLists().get("subscriptions")).singleElement()
         .satisfies(row -> assertThat(row.getKey()).isEqualTo(other.getValueString("id")));
      assertThat(where(AlertsSubscription.TABLE_NAME, AlertsDb.eq("id", other.getValue("id"))).get(0).getValueBoolean("enabled")).isTrue();
   }



   /*******************************************************************************
    ** Invalid and foreign row keys fail even for admins with all-access keys.
    *******************************************************************************/
   @Test
   void rejectsUnsafeChanges() throws Exception
   {
      QRecord other = subscription("viewer-2", "USER", "ALL");
      QRecord team = subscription(ADMIN, "TEAM", "ALL");
      for(String key : List.of(other.getValueString("id"), team.getValueString("id"), "99999", "bogus"))
      {
         assertThatThrownBy(() -> action.patchSection("notifications", Map.of("lists", Map.of("subscriptions", List.of(row(key, Map.of("enabled", false)))))))
            .isInstanceOf(QBadRequestException.class);
      }
      for(String field : List.of("inApp", "inAppMinSeverity"))
      {
         assertThatThrownBy(() -> action.patchSection("notifications", Map.of("lists", Map.of("categories", List.of(row("SECURITY", Map.of(field, field.equals("inApp") ? false : "ERROR")))))))
            .isInstanceOf(QBadRequestException.class).hasMessageContaining("Security notices can't be muted.");
      }
      assertThatThrownBy(() -> action.patchSection("notifications", Map.of("values", Map.of("timeZone", "Not/AZone")))).isInstanceOf(QBadRequestException.class);
      assertThatThrownBy(() -> action.patchSection("notifications", Map.of("values", Map.of("digestHour", 24)))).isInstanceOf(QBadRequestException.class);
      assertThatThrownBy(() -> action.patchSection("notifications", Map.of("userId", "viewer-2"))).isInstanceOf(QBadRequestException.class);
      var context = new UserPreferenceRequestContext(action.getSection("notifications").getSection(), QContext.getQSession());
      var provider = new AlertsNotificationPreferencesProvider();
      assertThat(provider.validate(context, new UserPreferenceChange().withLists(Map.of("subscriptions", List.of(new UserPreferenceRowChange().withKey(other.getValueString("id"))))))).isNotEmpty();
      assertThat(provider.validate(context, new UserPreferenceChange().withLists(Map.of("categories", List.of(new UserPreferenceRowChange().withKey("SECURITY").withValues(Map.of("inApp", false))))))).isNotEmpty();
      loginAs("nobody");
      assertThat(action.listSections()).isEmpty();
      assertThatThrownBy(() -> action.getSection("notifications")).isInstanceOf(QNotFoundException.class);
      assertThatThrownBy(() -> action.patchSection("notifications", Map.of())).isInstanceOf(QNotFoundException.class);
   }



   /*******************************************************************************
    ** Registration is idempotent, configurable, and drives the inbox shortcut.
    *******************************************************************************/
   @Test
   void registrationAndSummary() throws Exception
   {
      AlertsNotificationPreferencesProvider.register(qInstance);
      assertThat(UserPreferencesMetaData.of(qInstance).getSections()).hasSize(1);
      assertThat(new AlertsApi(runtime).inboxSummary()).containsEntry("preferencesSection", "notifications");
      assertThat(qInstance.getPossibleValueSource("alerts_digest_hour").getEnumValues()).hasSize(24);
      assertThat(qInstance.getPossibleValueSource("alerts_digest_hour").getEnumValues().get(0).getLabel()).isEqualTo("12:00 AM");
      assertThat(qInstance.getPossibleValueSource("alerts_digest_hour").getEnumValues().get(23).getLabel()).isEqualTo("11:00 PM");
      UserPreferencesMetaData.of(qInstance).getSections().clear();
      new AlertsQBitProducer().withQBitConfig(config.withPreferencesSectionEnabled(false)).postProduceActions(new MetaDataProducerMultiOutput(), qInstance);
      assertThat(new AlertsApi(runtime).inboxSummary()).containsEntry("preferencesSection", null);
      assertThat(action.listSections()).isEmpty();
   }



   /*******************************************************************************
    ** Standard FIELD audits retain both user identity and client channel.
    *******************************************************************************/
   @Test
   void audits() throws Exception
   {
      new AuditsMetaDataProvider().withClientChannelField(true).defineAll(qInstance, BACKEND_NAME, null);
      new QInstanceEnricher(qInstance).enrich();
      try(Connection connection = ConnectionManager.getConnection((RDBMSBackendMetaData) qInstance.getBackend(BACKEND_NAME)); Statement statement = connection.createStatement())
      {
         for(String table : List.of("auditDetail", "audit", "auditUser", "auditTable"))
         {
            statement.execute("DROP TABLE IF EXISTS " + table);
         }
         statement.execute("CREATE TABLE auditUser (id INTEGER AUTO_INCREMENT PRIMARY KEY, name VARCHAR(200), createDate TIMESTAMP, modifyDate TIMESTAMP)");
         statement.execute("CREATE TABLE auditTable (id INTEGER AUTO_INCREMENT PRIMARY KEY, name VARCHAR(200), label VARCHAR(200), createDate TIMESTAMP, modifyDate TIMESTAMP)");
         statement.execute("CREATE TABLE audit (id BIGINT AUTO_INCREMENT PRIMARY KEY, auditTableId INTEGER, auditUserId INTEGER, recordId INTEGER, message VARCHAR(250), timestamp TIMESTAMP, clientChannel VARCHAR(100))");
         statement.execute("CREATE TABLE auditDetail (id BIGINT AUTO_INCREMENT PRIMARY KEY, auditId BIGINT, message VARCHAR(250), fieldName VARCHAR(100), oldValue VARCHAR(250), newValue VARCHAR(250))");
      }
      QRecord rule = subscription(VIEWER, "USER", "ALL");
      loginAs(VIEWER).withClientChannel("cli/test").setPermissions(Set.of(AlertsQBitConfig.PERMISSION_VIEW));
      qInstance.setEnforcePermissionsInCoreActions(true);
      action.patchSection("notifications", Map.of("values", Map.of("digestHour", 17), "lists", Map.of("categories", List.of(row("JOB", Map.of("emailMode", "IMMEDIATE"))),
         "subscriptions", List.of(row(rule.getValueString("id"), Map.of("enabled", false))))));
      action.patchSection("notifications", Map.of("lists", Map.of("categories", List.of(row("JOB", Map.of("emailMode", "DAILY_DIGEST"))))));
      List<QRecord> audits = all("audit").stream().filter(record -> "cli/test".equals(record.getValueString("clientChannel"))).toList();
      assertThat(audits).hasSize(5);
      QRecord user = where("auditUser", AlertsDb.eq("name", "Name of " + VIEWER)).get(0);
      assertThat(audits).allSatisfy(audit -> assertThat(audit.getValue("auditUserId")).isEqualTo(user.getValue("id")));
      assertThat(all("auditDetail")).anyMatch(detail -> "digestHour".equals(detail.getValueString("fieldName")))
         .anyMatch(detail -> "emailMode".equals(detail.getValueString("fieldName"))).anyMatch(detail -> "enabled".equals(detail.getValueString("fieldName")));
   }



   /*******************************************************************************
    ** A raw API row patch.
    *******************************************************************************/
   private Map<String, Object> row(String key, Map<String, ?> values)
   {
      return (Map.of("key", key, "values", values));
   }



   /*******************************************************************************
    ** Seed a subscription as the engine would.
    *******************************************************************************/
   private QRecord subscription(String userId, String type, String scope) throws Exception
   {
      return (runtime.asSystem(() -> AlertsDb.insert(AlertsSubscription.TABLE_NAME, new QRecord().withValue("userId", userId)
         .withValue("subscriberType", type).withValue("scopeType", scope).withValue("scopeValue", "widgets"))));
   }
}
