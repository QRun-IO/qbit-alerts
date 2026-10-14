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

package com.kingsrook.qbits.alerts.engine;


import java.io.Serializable;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.alerts.AlertsRuntime;
import com.kingsrook.qbits.alerts.model.AlertsGroup;
import com.kingsrook.qbits.alerts.model.AlertsMonitor;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues;
import com.kingsrook.qbits.alerts.model.AlertsType;
import com.kingsrook.qqq.backend.core.alerts.AlertDefaultMonitor;
import com.kingsrook.qqq.backend.core.alerts.AlertSeverity;
import com.kingsrook.qqq.backend.core.alerts.AlertsInstanceMetaData;
import com.kingsrook.qqq.backend.core.alerts.DetailLevel;
import com.kingsrook.qqq.backend.core.alerts.QAlertTypeMetaData;
import com.kingsrook.qqq.backend.core.alerts.ResolveMode;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Discovery (design section 3.3): upserts every declared alert type into
 ** alerts_type (installed = true, metadata fields refreshed; the admin fields
 ** enabled, severityOverride, groupId and notify are kept), creates missing
 ** groups, seeds default monitors once, and marks types no longer declared
 ** installed = false. Types are never deleted, so history keeps its labels.
 ** A signal with an undeclared type is recorded under the "unregistered"
 ** type, so a typo never loses a signal.
 *******************************************************************************/
public class AlertTypeSync
{
   private static final QLogger LOG = QLogger.getLogger(AlertTypeSync.class);

   public static final String UNREGISTERED  = "unregistered";
   public static final String DEFAULT_GROUP = "other";

   private final AlertsRuntime runtime;

   private volatile boolean synced = false;



   /*******************************************************************************
    ** Constructor
    *******************************************************************************/
   public AlertTypeSync(AlertsRuntime runtime)
   {
      this.runtime = runtime;
   }



   /*******************************************************************************
    ** Sync once per runtime (the first signal, evaluation or page triggers it).
    *******************************************************************************/
   public void syncIfNeeded() throws QException
   {
      if(!synced)
      {
         synchronized(this)
         {
            if(!synced)
            {
               sync();
               synced = true;
            }
         }
      }
   }



   /*******************************************************************************
    ** Upsert every declared type, seed monitors, mark the rest uninstalled.
    *******************************************************************************/
   public void sync() throws QException
   {
      runtime.asSystem(() ->
      {
         AlertsInstanceMetaData metaData = AlertsInstanceMetaData.of(runtime.getQInstance());
         Set<String>            declared = new HashSet<>();
         if(metaData != null)
         {
            for(QAlertTypeMetaData type : metaData.getAlertTypes().values())
            {
               upsertType(type);
               declared.add(type.getName());
            }
         }

         for(QRecord row : AlertsDb.query(AlertsType.TABLE_NAME, AlertsDb.eq("installed", true)))
         {
            String name = row.getValueString("name");
            if(!declared.contains(name) && !UNREGISTERED.equals(name))
            {
               AlertsDb.update(AlertsType.TABLE_NAME, new QRecord().withValue("id", row.getValue("id")).withValue("installed", false));
            }
         }
         LOG.info("Alert types synced", logPair("declared", declared.size()));
         return (null);
      });
   }



   /*******************************************************************************
    ** The alerts_type row for a signal's type: the declared one (synced on
    ** demand) or, for an undeclared name, the "unregistered" type. Runs in the
    ** caller's (system) context.
    *******************************************************************************/
   public QRecord typeFor(String alertTypeName) throws QException
   {
      QRecord row = AlertsDb.first(AlertsType.TABLE_NAME, AlertsDb.eq("name", alertTypeName));
      if(row != null)
      {
         return (row);
      }

      AlertsInstanceMetaData metaData = AlertsInstanceMetaData.of(runtime.getQInstance());
      QAlertTypeMetaData     declared = metaData == null ? null : metaData.getAlertType(alertTypeName);
      if(declared != null)
      {
         return (upsertType(declared));
      }

      LOG.warn("Alert raised with an undeclared type; recording it as unregistered", logPair("alertType", alertTypeName));
      QRecord unregistered = AlertsDb.first(AlertsType.TABLE_NAME, AlertsDb.eq("name", UNREGISTERED));
      if(unregistered == null)
      {
         unregistered = upsertType(new QAlertTypeMetaData()
            .withName(UNREGISTERED)
            .withLabel("Unregistered alert type")
            .withSourceName("alerts")
            .withGroupName(DEFAULT_GROUP)
            .withDefaultSeverity(AlertSeverity.WARNING)
            .withDescription("Signals whose type no installed qbit declares. The alert's Type Name says which name was used.")
            .withHowToFix("Declare the type with QAlertTypeMetaData in the producing qbit, or fix the type name it raises."));
      }
      return (unregistered);
   }



   /*******************************************************************************
    ** Insert or refresh one type (and its group and seeded monitors).
    *******************************************************************************/
   QRecord upsertType(QAlertTypeMetaData type) throws QException
   {
      Map<String, Serializable> metadata = new LinkedHashMap<>();
      metadata.put("label", StringUtils.hasContent(type.getLabel()) ? type.getLabel() : type.getName());
      metadata.put("sourceName", type.getEffectiveSourceName());
      metadata.put("defaultSeverity", type.getDefaultSeverity() == null ? AlertSeverity.WARNING.name() : type.getDefaultSeverity().name());
      metadata.put("description", type.getDescription());
      metadata.put("howToFix", type.getHowToFix());
      metadata.put("subjectTableName", type.getSubjectTableName());
      metadata.put("visibilityPermission", type.getVisibilityPermission());
      metadata.put("installed", true);

      QRecord existing = AlertsDb.first(AlertsType.TABLE_NAME, AlertsDb.eq("name", type.getName()));
      QRecord row;
      if(existing == null)
      {
         QRecord record = new QRecord()
            .withValue("name", type.getName())
            .withValue("groupId", ensureGroup(type.getGroupName()))
            .withValue("enabled", true)
            .withValue("notify", true)
            .withValue("resolveMode", (type.getResolveMode() == null ? ResolveMode.PRODUCER : type.getResolveMode()).name())
            .withValue("quietPeriodMinutes", type.getQuietPeriod() == null ? null : (int) type.getQuietPeriod().toMinutes())
            .withValue("externalDetailCeiling", (type.getExternalDetailCeiling() == null ? DetailLevel.STANDARD : type.getExternalDetailCeiling()).name())
            .withValue("openCount", 0);
         metadata.forEach(record::setValue);
         row = AlertsDb.insert(AlertsType.TABLE_NAME, record);
      }
      else
      {
         QRecord record = new QRecord().withValue("id", existing.getValue("id"));
         metadata.forEach(record::setValue);
         if(existing.getValue("groupId") == null)
         {
            record.setValue("groupId", ensureGroup(type.getGroupName()));
         }
         AlertsDb.update(AlertsType.TABLE_NAME, record);
         row = AlertsDb.byId(AlertsType.TABLE_NAME, existing.getValue("id"));
      }

      for(AlertDefaultMonitor monitor : type.getDefaultMonitors())
      {
         seedMonitor(row, type, monitor);
      }
      return (row);
   }



   /*******************************************************************************
    ** The id of the group with this name, creating it if missing.
    *******************************************************************************/
   public Integer ensureGroup(String groupName) throws QException
   {
      String  name     = StringUtils.hasContent(groupName) ? groupName : DEFAULT_GROUP;
      QRecord existing = AlertsDb.first(AlertsGroup.TABLE_NAME, AlertsDb.eq("name", name));
      if(existing != null)
      {
         return (existing.getValueInteger("id"));
      }
      QRecord inserted = AlertsDb.insert(AlertsGroup.TABLE_NAME, new QRecord()
         .withValue("name", name)
         .withValue("label", name.length() <= 3 ? name.toUpperCase(java.util.Locale.ROOT) : AlertsPossibleValues.label(name.replace('-', '_').replace('.', '_').toUpperCase(java.util.Locale.ROOT))));
      return (inserted.getValueInteger("id"));
   }



   /*******************************************************************************
    ** Insert a seeded monitor once (matched by name and the seeded flag).
    *******************************************************************************/
   void seedMonitor(QRecord typeRow, QAlertTypeMetaData type, AlertDefaultMonitor monitor) throws QException
   {
      String name = StringUtils.hasContent(monitor.getName()) ? monitor.getName() : type.getName();
      QRecord existing = AlertsDb.first(AlertsMonitor.TABLE_NAME, AlertsDb.eq("name", name).withCriteria("seeded", QCriteriaOperator.EQUALS, true));
      if(existing != null)
      {
         return;
      }
      AlertsDb.insert(AlertsMonitor.TABLE_NAME, new QRecord()
         .withValue("name", name)
         .withValue("alertTypeId", typeRow.getValue("id"))
         .withValue("kind", monitor.getKind() == null ? null : monitor.getKind().name())
         .withValue("enabled", true)
         .withValue("seeded", true)
         .withValue("sourceName", type.getEffectiveSourceName())
         .withValue("description", "Seeded by " + (type.getEffectiveSourceName() == null ? "the alert type" : type.getEffectiveSourceName()) + " for " + type.getName() + ".")
         .withValue("definition", JsonUtils.toJson(monitor.getDefinition()))
         .withValue("perRecord", Boolean.TRUE.equals(monitor.getPerRecord()))
         .withValue("severity", monitor.getSeverity() == null ? null : monitor.getSeverity().name())
         .withValue("scheduleSeconds", monitor.getScheduleSeconds() == null ? 300 : monitor.getScheduleSeconds())
         .withValue("forMinutes", monitor.getForMinutes() == null ? 0 : monitor.getForMinutes())
         .withValue("consecutiveFailures", 0));
   }
}
