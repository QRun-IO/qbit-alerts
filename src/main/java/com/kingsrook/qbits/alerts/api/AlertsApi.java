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


import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.alerts.AlertsPreferences;
import com.kingsrook.qbits.alerts.AlertsQBitConfig;
import com.kingsrook.qbits.alerts.AlertsRuntime;
import com.kingsrook.qbits.alerts.AlertsSecurity;
import com.kingsrook.qbits.alerts.delivery.AlertMessageRenderer;
import com.kingsrook.qbits.alerts.engine.AlertsDb;
import com.kingsrook.qbits.alerts.home.AlertsHome;
import com.kingsrook.qbits.alerts.model.AlertsAlert;
import com.kingsrook.qbits.alerts.model.AlertsGroup;
import com.kingsrook.qbits.alerts.model.AlertsInboxItem;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.ScopeType;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.Status;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.SubscriberType;
import com.kingsrook.qbits.alerts.model.AlertsRecordRef;
import com.kingsrook.qbits.alerts.model.AlertsSubscription;
import com.kingsrook.qbits.alerts.spi.AlertSeverity;
import com.kingsrook.qqq.backend.core.actions.permissions.PermissionsHelper;
import com.kingsrook.qqq.backend.core.actions.permissions.TablePermissionSubType;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.AbstractActionInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterOrderBy;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** The bell and record-panel endpoints' logic (design sections 6.3, 6.4),
 ** independent of the HTTP layer. Everything is read and written in the
 ** caller's session (record locks: their own inbox, alerts they can see).
 *******************************************************************************/
public class AlertsApi
{
   static final int INBOX_ITEMS        = 10;
   static final int PANEL_ROWS         = 5;
   static final int RESOLVED_LOOKBACK  = 30;

   private final AlertsRuntime runtime;



   /*******************************************************************************
    ** Constructor
    *******************************************************************************/
   public AlertsApi(AlertsRuntime runtime)
   {
      this.runtime = runtime;
   }



   /*******************************************************************************
    ** Check alerts.view and set up the session (keys, settings).
    *******************************************************************************/
   public String requireViewer() throws QException
   {
      QSession session = QContext.getQSession();
      if(session == null || !session.hasPermission(AlertsQBitConfig.PERMISSION_VIEW))
      {
         throw (new QPermissionDeniedException("Alerts are not available to you."));
      }
      AlertsSecurity.ensureSessionKeys(runtime.getQInstance(), session);
      AlertsPreferences.ensure(runtime, session);
      return (AlertsSecurity.userId(session));
   }



   /*******************************************************************************
    ** The bell: unread count, highest unread severity, the latest items.
    *******************************************************************************/
   public Map<String, Object> inboxSummary() throws QException
   {
      String userId = requireViewer();
      Instant now   = runtime.now();

      List<QRecord> items = userId == null ? List.of() : AlertsDb.query(AlertsInboxItem.TABLE_NAME, AlertsDb.eq("userId", userId)
         .withCriteria("archivedAt", QCriteriaOperator.IS_BLANK)
         .withOrderBy(new QFilterOrderBy("deliveredAt", false))
         .withLimit(200));

      Map<Integer, QRecord> alerts = new HashMap<>();
      List<Integer>         ids    = items.stream().map(item -> item.getValueInteger("alertId")).toList();
      if(!ids.isEmpty())
      {
         for(QRecord alert : AlertsDb.query(AlertsAlert.TABLE_NAME, new QQueryFilter().withCriteria("id", QCriteriaOperator.IN, ids)))
         {
            alerts.put(alert.getValueInteger("id"), alert);
         }
      }
      Map<Integer, String> groups = groupLabels();

      int                       unread  = 0;
      AlertSeverity             highest = null;
      List<Map<String, Object>> latest  = new ArrayList<>();
      for(QRecord item : items)
      {
         QRecord alert = alerts.get(item.getValueInteger("alertId"));
         if(alert == null)
         {
            continue;
         }
         boolean isUnread = item.getValue("readAt") == null;
         if(isUnread)
         {
            unread++;
            AlertSeverity severity = AlertSeverity.fromLabel(alert.getValueString("severity"), AlertSeverity.WARNING);
            highest = highest == null || severity.compareTo(highest) > 0 ? severity : highest;
         }
         if(latest.size() < INBOX_ITEMS)
         {
            Map<String, Object> row = alertRow(alert, groups, now);
            row.put("unread", isUnread);
            row.put("deliveredAt", item.getValueInstant("deliveredAt") == null ? null : item.getValueInstant("deliveredAt").toString());
            latest.add(row);
         }
      }

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("unreadCount", unread);
      result.put("highestUnreadSeverity", highest == null ? null : highest.name());
      result.put("items", latest);
      result.put("alertsHref", "/app/" + AlertsQBitConfig.APP_NAME);
      result.put("pollSeconds", 60);
      return (result);
   }



   /*******************************************************************************
    ** Mark one alert's inbox item read (alertId), or all of the user's.
    *******************************************************************************/
   public Map<String, Object> markRead(Map<String, Object> body) throws QException
   {
      String userId = requireViewer();
      if(userId == null)
      {
         return (Map.of("updated", 0));
      }
      QQueryFilter filter = AlertsDb.eq("userId", userId).withCriteria("readAt", QCriteriaOperator.IS_BLANK);
      Object       alertId = body == null ? null : body.get("alertId");
      if(alertId != null)
      {
         filter.withCriteria("alertId", QCriteriaOperator.EQUALS, Integer.valueOf(String.valueOf(alertId)));
      }
      else if(body == null || !Boolean.TRUE.equals(body.get("all")))
      {
         throw (new QBadRequestException("Send alertId, or all: true."));
      }

      Instant       now     = runtime.now();
      List<QRecord> updates = new ArrayList<>();
      for(QRecord item : AlertsDb.query(AlertsInboxItem.TABLE_NAME, filter.withLimit(500)))
      {
         updates.add(new QRecord().withValue("id", item.getValue("id")).withValue("readAt", now));
      }
      if(!updates.isEmpty())
      {
         UpdateAction.executeForRecords(new UpdateInput(AlertsInboxItem.TABLE_NAME).withRecords(updates));
      }
      return (Map.of("updated", updates.size()));
   }



   /*******************************************************************************
    ** The "Open alerts" panel for a record: visible open alerts that reference
    ** it (at most 5), how many were resolved in 30 days, and links. visible is
    ** false when there is nothing to show (the panel is then hidden).
    *******************************************************************************/
   public Map<String, Object> recordPanel(String tableName, String recordId) throws QException
   {
      requireViewer();
      if(!StringUtils.hasContent(tableName) || !StringUtils.hasContent(recordId) || runtime.getQInstance().getTable(tableName) == null)
      {
         throw (new QBadRequestException("Send a table name and record id."));
      }
      if(!PermissionsHelper.hasTablePermission(new AbstractActionInput(), tableName, TablePermissionSubType.READ))
      {
         throw (new QPermissionDeniedException("You can't read that table."));
      }

      Instant       now     = runtime.now();
      List<Integer> openIds = new ArrayList<>();
      List<Integer> allIds  = new ArrayList<>();
      for(QRecord ref : AlertsDb.query(AlertsRecordRef.TABLE_NAME, AlertsDb.eq("tableName", tableName).withCriteria("recordId", QCriteriaOperator.EQUALS, recordId).withLimit(500)))
      {
         allIds.add(ref.getValueInteger("alertId"));
         if(Boolean.TRUE.equals(ref.getValueBoolean("active")))
         {
            openIds.add(ref.getValueInteger("alertId"));
         }
      }
      for(QRecord alert : AlertsDb.query(AlertsAlert.TABLE_NAME, AlertsDb.eq("subjectTableName", tableName).withCriteria("subjectRecordId", QCriteriaOperator.EQUALS, recordId).withLimit(500)))
      {
         allIds.add(alert.getValueInteger("id"));
         openIds.add(alert.getValueInteger("id"));
      }

      List<QRecord> open = openIds.isEmpty() ? new ArrayList<>() : new ArrayList<>(AlertsDb.query(AlertsAlert.TABLE_NAME, new QQueryFilter()
         .withCriteria("id", QCriteriaOperator.IN, openIds)
         .withCriteria("status", QCriteriaOperator.IN, Status.openNames())));
      open.sort((a, b) -> AlertSeverity.fromLabel(b.getValueString("severity"), AlertSeverity.INFO).compareTo(AlertSeverity.fromLabel(a.getValueString("severity"), AlertSeverity.INFO)));
      int resolved = allIds.isEmpty() ? 0 : AlertsDb.count(AlertsAlert.TABLE_NAME, new QQueryFilter()
         .withCriteria("id", QCriteriaOperator.IN, allIds)
         .withCriteria("status", QCriteriaOperator.EQUALS, Status.RESOLVED.name())
         .withCriteria("resolvedAt", QCriteriaOperator.GREATER_THAN_OR_EQUALS, now.minus(RESOLVED_LOOKBACK, ChronoUnit.DAYS)));

      Map<Integer, String>      groups = groupLabels();
      List<Map<String, Object>> rows   = new ArrayList<>();
      for(QRecord alert : open.subList(0, Math.min(PANEL_ROWS, open.size())))
      {
         rows.add(alertRow(alert, groups, now));
      }

      QSession session = QContext.getQSession();
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("visible", !open.isEmpty() || resolved > 0);
      result.put("openCount", open.size());
      result.put("open", rows);
      result.put("resolvedLast30Days", resolved);
      result.put("viewAllHref", allIds.isEmpty() ? null : AlertsHome.listUrl(new QQueryFilter().withCriteria("id", QCriteriaOperator.IN, dedupe(allIds))));
      result.put("canManage", session.hasPermission(AlertsQBitConfig.PERMISSION_MANAGE));
      result.put("watching", isWatching(tableName, recordId));
      return (result);
   }



   /*******************************************************************************
    ** "Watch alerts for this record": a RECORD-scope subscription for the user.
    *******************************************************************************/
   public Map<String, Object> watchRecord(Map<String, Object> body) throws QException
   {
      String userId    = requireViewer();
      String tableName = body == null ? null : (String) body.get("tableName");
      Object recordId  = body == null ? null : body.get("recordId");
      if(userId == null || !StringUtils.hasContent(tableName) || recordId == null || runtime.getQInstance().getTable(tableName) == null)
      {
         throw (new QBadRequestException("Send a table name and record id."));
      }
      if(isWatching(tableName, String.valueOf(recordId)))
      {
         return (Map.of("watching", true, "created", false));
      }
      List<QRecord> inserted = InsertAction.executeForRecords(new InsertInput(AlertsSubscription.TABLE_NAME).withRecord(new QRecord()
         .withValue("subscriberType", SubscriberType.USER.name())
         .withValue("userId", userId)
         .withValue("scopeType", ScopeType.RECORD.name())
         .withValue("scopeValue", tableName)
         .withValue("scopeRecordId", String.valueOf(recordId))
         .withValue("minSeverity", AlertSeverity.WARNING.name())
         .withValue("events", "RAISED,ESCALATED,REMINDER,RESOLVED")
         .withValue("deliveryMode", "IMMEDIATE")
         .withValue("enabled", true)));
      if(CollectionUtils.nullSafeHasContents(inserted.get(0).getErrors()))
      {
         throw (new QBadRequestException(inserted.get(0).getErrors().get(0).getMessage()));
      }
      return (Map.of("watching", true, "created", true, "subscriptionId", inserted.get(0).getValue("id")));
   }



   /*******************************************************************************
    ** Whether the user already has a RECORD subscription for this record.
    *******************************************************************************/
   private boolean isWatching(String tableName, String recordId) throws QException
   {
      String userId = AlertsSecurity.userId(QContext.getQSession());
      if(userId == null || !PermissionsHelper.hasTablePermission(new AbstractActionInput(), AlertsSubscription.TABLE_NAME, TablePermissionSubType.READ))
      {
         return (false);
      }
      return (AlertsDb.count(AlertsSubscription.TABLE_NAME, AlertsDb.eq("userId", userId)
         .withCriteria("scopeType", QCriteriaOperator.EQUALS, ScopeType.RECORD.name())
         .withCriteria("scopeValue", QCriteriaOperator.EQUALS, tableName)
         .withCriteria("scopeRecordId", QCriteriaOperator.EQUALS, recordId)) > 0);
   }



   /*******************************************************************************
    ** One alert as the bell and panel show it.
    *******************************************************************************/
   private static Map<String, Object> alertRow(QRecord alert, Map<Integer, String> groups, Instant now)
   {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("alertId", alert.getValueInteger("id"));
      row.put("title", alert.getValueString("title"));
      row.put("severity", alert.getValueString("severity"));
      row.put("status", alert.getValueString("status"));
      row.put("group", groups.get(alert.getValueInteger("groupId")));
      row.put("age", AlertMessageRenderer.age(alert.getValueInstant("firstRaisedAt"), now));
      row.put("raisedAt", alert.getValueInstant("firstRaisedAt") == null ? null : alert.getValueInstant("firstRaisedAt").toString());
      row.put("href", "/app/" + AlertsAlert.TABLE_NAME + "/" + alert.getValue("id"));
      return (row);
   }



   /*******************************************************************************
    ** Group labels by id (empty without read permission).
    *******************************************************************************/
   private static Map<Integer, String> groupLabels() throws QException
   {
      Map<Integer, String> labels = new HashMap<>();
      if(PermissionsHelper.hasTablePermission(new AbstractActionInput(), AlertsGroup.TABLE_NAME, TablePermissionSubType.READ))
      {
         for(QRecord group : AlertsDb.query(AlertsGroup.TABLE_NAME, new QQueryFilter()))
         {
            labels.put(group.getValueInteger("id"), group.getValueString("label"));
         }
      }
      return (labels);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static List<Serializable> dedupe(List<Integer> ids)
   {
      return (new ArrayList<>(new java.util.LinkedHashSet<>(ids)));
   }
}
