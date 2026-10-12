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


import com.kingsrook.qbits.alerts.AlertsQBitConfig;
import com.kingsrook.qbits.alerts.AlertsSecurity;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.MetaDataProducerMultiOutput;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaDataInterface;
import com.kingsrook.qqq.backend.core.model.metadata.joins.QJoinMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.possiblevalues.QPossibleValueSource;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitComponentMetaDataProducer;
import com.kingsrook.qqq.backend.core.model.metadata.security.QSecurityKeyType;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;


/*******************************************************************************
 ** Produces the Alerts tables, their joins, possible-value sources,
 ** record-view child lists and security key types.
 *******************************************************************************/
public class AlertsTablesMetaDataProducer extends QBitComponentMetaDataProducer<MetaDataProducerMultiOutput, AlertsQBitConfig>
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public MetaDataProducerMultiOutput produce(QInstance qInstance) throws QException
   {
      AlertsQBitConfig            config = getQBitConfig();
      MetaDataProducerMultiOutput output = new MetaDataProducerMultiOutput();
      for(QSecurityKeyType keyType : AlertsSecurity.buildSecurityKeyTypes())
      {
         output.add(keyType);
      }
      for(QTableMetaData table : AlertsTableMetaDataHelper.buildAllTables(config))
      {
         output.add(table);
      }
      for(QJoinMetaData join : AlertsTableMetaDataHelper.buildJoins())
      {
         output.add(join);
      }
      for(QPossibleValueSource source : AlertsTableMetaDataHelper.buildPossibleValueSources())
      {
         output.add(source);
      }
      for(QWidgetMetaDataInterface widget : AlertsTableMetaDataHelper.buildChildListWidgets())
      {
         output.add(widget);
      }
      return (output);
   }



   /*******************************************************************************
    ** tables first
    *******************************************************************************/
   @Override
   public int getSortOrder()
   {
      return (100);
   }
}
