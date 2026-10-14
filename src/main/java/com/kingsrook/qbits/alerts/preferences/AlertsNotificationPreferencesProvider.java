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
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.kingsrook.qbits.alerts.AlertsQBitConfig;
import com.kingsrook.qbits.alerts.engine.AlertsDb;
import com.kingsrook.qbits.alerts.model.AlertsNotificationPreference;
import com.kingsrook.qbits.alerts.model.AlertsSubscription;
import com.kingsrook.qbits.alerts.model.AlertsUserPreference;
import com.kingsrook.qbits.alerts.spi.NotificationCategory;
import com.kingsrook.qbits.alerts.spi.NotificationSeverity;
import com.kingsrook.qqq.backend.core.actions.permissions.CorePermissionEnforcer;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.help.QHelpContent;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValue;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSourceType;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceChange;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceListMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceProviderInterface;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceRequestContext;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceRow;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceRowChange;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceSectionMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferenceValues;
import com.kingsrook.qqq.backend.core.model.metadata.preferences.UserPreferencesMetaData;


/*******************************************************************************
 ** The session user's notification settings, stored in the existing Alerts tables.
 *******************************************************************************/
public class AlertsNotificationPreferencesProvider implements UserPreferenceProviderInterface
{
   public static final String SECTION_NAME = "notifications";
   private static final String SECURITY_REASON = "Security notices can't be muted.";
   private static final Map<String, String> SCALARS = Map.of("pausedUntil", "pausedUntil", "digestHour", "digestHour", "timeZone", "timeZone",
      "alertInAppEnabled", "inAppEnabled", "alertEmailEnabled", "emailEnabled");



   /*******************************************************************************
    ** Register once; no app scope, so bell users need only alerts.view.
    *******************************************************************************/
   public static void register(QInstance instance)
   {
      if(isRegistered(instance))
      {
         return;
      }
      List<QPossibleValue<?>> hours = new ArrayList<>();
      for(int hour = 0; hour < 24; hour++)
      {
         hours.add(new QPossibleValue<>(hour, (hour % 12 == 0 ? 12 : hour % 12) + ":00 " + (hour < 12 ? "AM" : "PM")));
      }
      instance.addPossibleValueSource(new QPossibleValueSource().withName("alerts_digest_hour").withType(QPossibleValueSourceType.ENUM).withEnumValues(hours));
      UserPreferencesMetaData.ofOrWithNew(instance).withSection(new UserPreferenceSectionMetaData()
         .withName(SECTION_NAME).withLabel("Notifications").withIcon(new QIcon("notifications")).withOrder(20)
         .withDescription("What reaches you in the app and by email.").withPermissionName(AlertsQBitConfig.PERMISSION_VIEW)
         .withProvider(new QCodeReference(AlertsNotificationPreferencesProvider.class))
         .withField(new QFieldMetaData("pausedUntil", QFieldType.DATE_TIME).withLabel("Pause notifications until")
            .withHelpContent(new QHelpContent("Security notices still arrive.")))
         .withField(new QFieldMetaData("digestHour", QFieldType.INTEGER).withLabel("Daily digest time").withPossibleValueSourceName("alerts_digest_hour"))
         .withField(new QFieldMetaData("timeZone", QFieldType.STRING).withLabel("Time zone").withMaxLength(64))
         .withField(new QFieldMetaData("alertInAppEnabled", QFieldType.BOOLEAN).withLabel("Show alerts in the app"))
         .withField(new QFieldMetaData("alertEmailEnabled", QFieldType.BOOLEAN).withLabel("Email me alerts"))
         .withList(new UserPreferenceListMetaData().withName("categories").withLabel("Categories").withRowLabel("Category")
            .withField(new QFieldMetaData("inApp", QFieldType.BOOLEAN).withLabel("In-app"))
            .withField(enumField("inAppMinSeverity", "In-app from", "alerts_notification_severity"))
            .withField(enumField("emailMode", "Email", "alerts_email_mode"))
            .withField(enumField("emailMinSeverity", "Email from", "alerts_notification_severity")))
         .withList(new UserPreferenceListMetaData().withName("subscriptions").withLabel("Subscriptions").withRowLabel("Alert rule")
            .withField(new QFieldMetaData("enabled", QFieldType.BOOLEAN).withLabel("Enabled"))
            .withField(enumField("minSeverity", "From severity", "alerts_severity"))
            .withField(enumField("deliveryMode", "Delivery mode", "alerts_delivery_mode"))));
   }



   /*******************************************************************************
    ** Whether the section is actually installed in this instance.
    *******************************************************************************/
   public static boolean isRegistered(QInstance instance)
   {
      UserPreferencesMetaData metadata = UserPreferencesMetaData.of(instance);
      return (metadata != null && metadata.getSections().stream().anyMatch(section -> SECTION_NAME.equals(section.getName())));
   }



   /*******************************************************************************
    ** String enums use ordinary fields with an ENUM possible-value source.
    *******************************************************************************/
   private static QFieldMetaData enumField(String name, String label, String source)
   {
      return (new QFieldMetaData(name, QFieldType.STRING).withLabel(label).withPossibleValueSourceName(source));
   }



   /*******************************************************************************
    ** Load defaults and fixed category rows, plus only this user's subscriptions.
    *******************************************************************************/
   @Override
   public UserPreferenceValues load(UserPreferenceRequestContext context) throws QException
   {
      QRecord settings = userSettings(context);
      UserPreferenceValues result = new UserPreferenceValues();
      SCALARS.forEach((name, column) -> result.getValues().put(name, settings.getValue(column)));
      Map<String, QRecord> preferences = new LinkedHashMap<>();
      for(QRecord preference : AlertsDb.query(AlertsNotificationPreference.TABLE_NAME, AlertsDb.eq("userId", context.getUserId())))
      {
         preferences.put(preference.getValueString("category"), preference);
      }
      List<UserPreferenceRow> categories = new ArrayList<>();
      for(NotificationCategory category : NotificationCategory.values())
      {
         QRecord preference = preferences.get(category.name());
         Map<String, Serializable> values = new LinkedHashMap<>();
         values.put("inApp", preference == null || !Boolean.TRUE.equals(preference.getValueBoolean("inAppMuted")));
         for(String field : List.of("inAppMinSeverity", "emailMode", "emailMinSeverity"))
         {
            values.put(field, preference == null ? QContext.getQInstance().getTable(AlertsNotificationPreference.TABLE_NAME).getField(field).getDefaultValue() : preference.getValue(field));
         }

         ///////////////////////////////////////////////////////////////////////
         // no in-app threshold means everything; show it as the lowest level //
         ///////////////////////////////////////////////////////////////////////
         if(values.get("inAppMinSeverity") == null)
         {
            values.put("inAppMinSeverity", NotificationSeverity.INFO.name());
         }
         String label = QContext.getQInstance().getPossibleValueSource("alerts_notification_category").getEnumValues().stream()
            .filter(value -> category.name().equals(value.getId())).findFirst().orElseThrow().getLabel();
         UserPreferenceRow row = new UserPreferenceRow().withKey(category.name()).withLabel(label).withValues(values);
         if(category == NotificationCategory.SECURITY)
         {
            values.put("inApp", true);
            values.put("inAppMinSeverity", NotificationSeverity.INFO.name());
            row.withReadOnlyFieldNames(List.of("inApp", "inAppMinSeverity")).withReadOnlyReason(SECURITY_REASON);
         }
         categories.add(row);
      }
      result.getLists().put("categories", categories);
      List<UserPreferenceRow> subscriptions = new ArrayList<>();
      for(QRecord subscription : ownSubscriptions(context))
      {
         Map<String, Serializable> values = new LinkedHashMap<>();
         for(String field : List.of("enabled", "minSeverity", "deliveryMode"))
         {
            values.put(field, subscription.getValue(field));
         }
         subscriptions.add(new UserPreferenceRow().withKey(subscription.getValueString("id")).withLabel(scopeLabel(subscription)).withValues(values));
      }
      result.getLists().put("subscriptions", subscriptions);
      return (result);
   }



   /*******************************************************************************
    ** Validate domain rules; core invokes this outside its trusted store scope.
    *******************************************************************************/
   @Override
   public List<String> validate(UserPreferenceRequestContext context, UserPreferenceChange change) throws QException
   {
      List<String> errors = new ArrayList<>();
      Serializable zone = change.getValues().get("timeZone");
      if(zone != null)
      {
         try
         {
            ZoneId.of(zone.toString());
         }
         catch(DateTimeException e)
         {
            errors.add("Time zone: must be a valid time zone, such as America/Chicago.");
         }
      }
      for(UserPreferenceRowChange row : change.getLists().getOrDefault("categories", List.of()))
      {
         if("SECURITY".equals(row.getKey()) && (row.getValues().containsKey("inApp") || row.getValues().containsKey("inAppMinSeverity")))
         {
            errors.add(SECURITY_REASON);
         }
      }
      if(!change.getLists().getOrDefault("subscriptions", List.of()).isEmpty())
      {
         Set<String> ownIds = CorePermissionEnforcer.runTrusted("validate session user's alert subscriptions", () -> ownSubscriptions(context).stream()
            .map(row -> row.getValueString("id")).collect(Collectors.toSet()));
         for(UserPreferenceRowChange row : change.getLists().get("subscriptions"))
         {
            if(!ownIds.contains(row.getKey()))
            {
               errors.add("Alert rule: isn't one of your subscriptions.");
            }
         }
      }
      return (errors);
   }



   /*******************************************************************************
    ** Partial updates through normal actions preserve auditing and customizers.
    *******************************************************************************/
   @Override
   public void store(UserPreferenceRequestContext context, UserPreferenceChange change) throws QException
   {
      List<String> errors = validate(context, change);
      if(!errors.isEmpty())
      {
         throw (new QBadRequestException(String.join("; ", errors)));
      }
      if(!change.getValues().isEmpty())
      {
         QRecord update = new QRecord().withValue("id", userSettings(context).getValue("id"));
         change.getValues().forEach((field, value) -> update.setValue(SCALARS.get(field), value));
         AlertsDb.update(AlertsUserPreference.TABLE_NAME, update);
      }
      for(UserPreferenceRowChange row : change.getLists().getOrDefault("categories", List.of()))
      {
         QRecord existing = AlertsDb.first(AlertsNotificationPreference.TABLE_NAME, AlertsDb.eq("userId", context.getUserId())
            .withCriteria("category", QCriteriaOperator.EQUALS, row.getKey()));
         QRecord update = new QRecord();
         row.getValues().forEach((field, value) -> update.setValue("inApp".equals(field) ? "inAppMuted" : field,
            "inApp".equals(field) ? Boolean.FALSE.equals(value) : value));
         if(existing == null)
         {
            AlertsDb.insert(AlertsNotificationPreference.TABLE_NAME, update.withValue("userId", context.getUserId()).withValue("category", row.getKey()));
         }
         else
         {
            AlertsDb.update(AlertsNotificationPreference.TABLE_NAME, update.withValue("id", existing.getValue("id")));
         }
      }
      for(UserPreferenceRowChange row : change.getLists().getOrDefault("subscriptions", List.of()))
      {
         QRecord update = new QRecord().withValue("id", Integer.valueOf(row.getKey()));
         row.getValues().forEach(update::setValue);
         AlertsDb.update(AlertsSubscription.TABLE_NAME, update);
      }
   }



   /*******************************************************************************
    ** Create the same defaults as AlertsPreferences.ensure, retaining the user.
    *******************************************************************************/
   private QRecord userSettings(UserPreferenceRequestContext context) throws QException
   {
      QRecord settings = AlertsDb.first(AlertsUserPreference.TABLE_NAME, AlertsDb.eq("userId", context.getUserId()));
      if(settings == null)
      {
         String label = context.getSession().getUser().getFullName();
         settings = AlertsDb.insert(AlertsUserPreference.TABLE_NAME, new QRecord().withValue("userId", context.getUserId())
            .withValue("userLabel", label == null ? context.getUserId() : label).withValue("inAppEnabled", true).withValue("emailEnabled", true)
            .withValue("quietBypassSeverity", "CRITICAL").withValue("digestHour", 8));
      }
      return (settings);
   }



   /*******************************************************************************
    ** Never expose team or channel rules, even to an administrator.
    *******************************************************************************/
   private List<QRecord> ownSubscriptions(UserPreferenceRequestContext context) throws QException
   {
      return (AlertsDb.query(AlertsSubscription.TABLE_NAME, AlertsDb.eq("userId", context.getUserId()).withCriteria("subscriberType", QCriteriaOperator.EQUALS, "USER")));
   }



   /*******************************************************************************
    ** Readable scope without relying on access to the subscription's target table.
    *******************************************************************************/
   private String scopeLabel(QRecord subscription)
   {
      String value = subscription.getValueString("scopeValue");
      return (switch(subscription.getValueString("scopeType"))
      {
         case "ALL" -> "All alerts";
         case "TYPE" -> "Type: " + value;
         case "GROUP" -> "Group: " + value;
         case "RECORD" -> "Record: " + value + " #" + subscription.getValueString("scopeRecordId");
         case "MONITOR" -> "Monitor: " + value;
         default -> "Table: " + value;
      });
   }
}
