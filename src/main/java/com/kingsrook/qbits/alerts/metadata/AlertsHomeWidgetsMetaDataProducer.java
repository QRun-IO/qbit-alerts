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

package com.kingsrook.qbits.alerts.metadata;


import java.util.List;
import com.kingsrook.qbits.alerts.AlertsPermissionChecker;
import com.kingsrook.qbits.alerts.AlertsQBitConfig;
import com.kingsrook.qbits.alerts.home.AlertsHome;
import com.kingsrook.qbits.alerts.home.AlertsHomeWidgetRenderer;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.WidgetType;
import com.kingsrook.qqq.backend.core.model.metadata.MetaDataProducerMultiOutput;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitComponentMetaDataProducer;


/*******************************************************************************
 ** The Alerts home dashboard (design section 6.2), in page order, all full
 ** width: setup (admins), open by severity, my alerts, needs action, by group, trend, noisy
 ** types, delivery health (admins) and recently resolved.
 *******************************************************************************/
public class AlertsHomeWidgetsMetaDataProducer extends QBitComponentMetaDataProducer<MetaDataProducerMultiOutput, AlertsQBitConfig>
{
   public static final String SETUP    = "alertsHomeSetup";
   public static final String SEVERITY = "alertsHomeOpenBySeverity";
   public static final String MINE     = "alertsHomeMyAlerts";
   public static final String ACTION   = "alertsHomeNeedsAction";
   public static final String BY_GROUP = "alertsHomeByGroup";
   public static final String TREND    = "alertsHomeTrend";
   public static final String NOISY    = "alertsHomeNoisy";
   public static final String DELIVERY = "alertsHomeDeliveryHealth";
   public static final String RESOLVED = "alertsHomeRecentlyResolved";
   public static final String NOTICES  = "alertsHomeNotifications";



   /*******************************************************************************
    ** The home widget names, in page order.
    *******************************************************************************/
   public static List<String> homeWidgetNames()
   {
      return (List.of(SETUP, SEVERITY, MINE, ACTION, BY_GROUP, TREND, NOISY, DELIVERY, NOTICES, RESOLVED));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public MetaDataProducerMultiOutput produce(QInstance qInstance) throws QException
   {
      String html  = WidgetType.HTML.getType();
      String table = WidgetType.TABLE.getType();

      MetaDataProducerMultiOutput output = new MetaDataProducerMultiOutput();
      output.add(widget(SETUP, "Setup", html, AlertsHome.KIND_SETUP, AlertsQBitConfig.PERMISSION_ADMIN));
      output.add(widget(SEVERITY, "Open Alerts", html, AlertsHome.KIND_SEVERITY, AlertsQBitConfig.PERMISSION_VIEW));
      output.add(widget(MINE, "My Alerts", table, AlertsHome.KIND_MINE, AlertsQBitConfig.PERMISSION_VIEW));
      output.add(widget(ACTION, "Needs Action", table, AlertsHome.KIND_ACTION, AlertsQBitConfig.PERMISSION_VIEW));
      output.add(widget(BY_GROUP, "By Group", table, AlertsHome.KIND_BY_GROUP, AlertsQBitConfig.PERMISSION_VIEW));
      output.add(widget(TREND, "Opened and Resolved, 30 Days", WidgetType.LINE_CHART.getType(), AlertsHome.KIND_TREND, AlertsQBitConfig.PERMISSION_VIEW));
      output.add(widget(NOISY, "Noisiest Alert Types, 7 Days", table, AlertsHome.KIND_NOISY, AlertsQBitConfig.PERMISSION_VIEW));
      output.add(widget(DELIVERY, "Delivery Health", html, AlertsHome.KIND_DELIVERY, AlertsQBitConfig.PERMISSION_ADMIN));
      output.add(widget(NOTICES, "Notifications", html, AlertsHome.KIND_NOTIFICATIONS, AlertsQBitConfig.PERMISSION_NOTIFICATION_ADMIN));
      output.add(widget(RESOLVED, "Recently Resolved", table, AlertsHome.KIND_RESOLVED, AlertsQBitConfig.PERMISSION_VIEW));
      return (output);
   }



   /*******************************************************************************
    ** One full-width card widget.
    *******************************************************************************/
   private static QWidgetMetaData widget(String name, String label, String type, String kind, String permission)
   {
      return (new QWidgetMetaData()
         .withName(name)
         .withLabel(label)
         .withType(type)
         .withGridColumns(12)
         .withIsCard(true)
         .withShowReloadButton(true)
         .withCodeReference(new QCodeReference(AlertsHomeWidgetRenderer.class))
         .withDefaultValue(AlertsHomeWidgetRenderer.KIND, kind)
         .withPermissionRules(AlertsPermissionChecker.rules(permission)));
   }



   /*******************************************************************************
    ** after the tables and processes, before the app
    *******************************************************************************/
   @Override
   public int getSortOrder()
   {
      return (300);
   }
}
