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


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.alerts.AlertsPermissionChecker;
import com.kingsrook.qbits.alerts.AlertsQBitConfig;
import com.kingsrook.qbits.alerts.model.AlertsAlert;
import com.kingsrook.qbits.alerts.model.AlertsChannel;
import com.kingsrook.qbits.alerts.model.AlertsDelivery;
import com.kingsrook.qbits.alerts.model.AlertsEscalationPolicy;
import com.kingsrook.qbits.alerts.model.AlertsGroup;
import com.kingsrook.qbits.alerts.model.AlertsMonitor;
import com.kingsrook.qbits.alerts.model.AlertsMute;
import com.kingsrook.qbits.alerts.model.AlertsNotification;
import com.kingsrook.qbits.alerts.model.AlertsNotificationPreference;
import com.kingsrook.qbits.alerts.model.AlertsSubscription;
import com.kingsrook.qbits.alerts.model.AlertsTeam;
import com.kingsrook.qbits.alerts.model.AlertsType;
import com.kingsrook.qbits.alerts.model.AlertsUserPreference;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.MetaDataProducerMultiOutput;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitComponentMetaDataProducer;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitProductionContext;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;


/*******************************************************************************
 ** The top-level Alerts app (design section 6.1). Its home is the Alerts
 ** dashboard; its nav lists the main tables in the design's order. The
 ** timeline, alert records, team members and notification recipients are not in the nav:
 ** they are child lists and relations on their parents, and stay standard
 ** tables reachable by URL and the API. Tables a user can't read are hidden
 ** by permission. No processes in the nav.
 *******************************************************************************/
public class AlertsAppMetaDataProducer extends QBitComponentMetaDataProducer<QAppMetaData, AlertsQBitConfig>
{
   /** the main tables, in nav order. */
   public static final List<String> MAIN_TABLES = List.of(AlertsAlert.TABLE_NAME, AlertsNotification.TABLE_NAME, AlertsMonitor.TABLE_NAME, AlertsType.TABLE_NAME, AlertsGroup.TABLE_NAME,
      AlertsSubscription.TABLE_NAME, AlertsUserPreference.TABLE_NAME, AlertsNotificationPreference.TABLE_NAME, AlertsTeam.TABLE_NAME, AlertsChannel.TABLE_NAME, AlertsEscalationPolicy.TABLE_NAME, AlertsMute.TABLE_NAME, AlertsDelivery.TABLE_NAME);



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public QAppMetaData produce(QInstance qInstance) throws QException
   {
      QAppMetaData app = new QAppMetaData()
         .withName(AlertsQBitConfig.APP_NAME)
         .withLabel("Alerts")
         .withIcon(new QIcon("notifications_active"))
         .withPermissionRules(AlertsPermissionChecker.rules(AlertsQBitConfig.PERMISSION_VIEW))
         .withWidgets(new ArrayList<>(AlertsHomeWidgetsMetaDataProducer.homeWidgetNames()));

      MetaDataProducerMultiOutput produced = QBitProductionContext.peekMetaDataProducerMultiOutput();
      if(produced != null)
      {
         for(String tableName : MAIN_TABLES)
         {
            QTableMetaData table = produced.get(QTableMetaData.class, tableName);
            if(table != null)
            {
               app.withChild(table);
            }
         }
      }
      return (app);
   }



   /*******************************************************************************
    ** last, so the tables and widgets exist in the output
    *******************************************************************************/
   @Override
   public int getSortOrder()
   {
      return (900);
   }
}
