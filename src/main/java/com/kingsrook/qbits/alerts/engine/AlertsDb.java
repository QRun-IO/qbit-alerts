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
import java.util.List;
import com.kingsrook.qqq.backend.core.actions.tables.CountAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.QueryAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.count.CountInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QFilterCriteria;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QueryInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.statusmessages.QStatusMessage;
import com.kingsrook.qqq.backend.core.utils.CollectionUtils;


/*******************************************************************************
 ** Small helpers over QQQ's table actions, for the engine. They run in
 ** whatever context the caller set up (the engine uses the system user), so
 ** customizers, audits and ESB listeners apply as for any other write.
 *******************************************************************************/
public final class AlertsDb
{

   /*******************************************************************************
    ** Helpers only.
    *******************************************************************************/
   private AlertsDb()
   {
   }



   /*******************************************************************************
    ** Query a table.
    *******************************************************************************/
   public static List<QRecord> query(String tableName, QQueryFilter filter) throws QException
   {
      return (new QueryAction().execute(new QueryInput(tableName).withFilter(filter).withTransaction(QContext.getQBackendTransaction())).getRecords());
   }



   /*******************************************************************************
    ** The first row matching, or null.
    *******************************************************************************/
   public static QRecord first(String tableName, QQueryFilter filter) throws QException
   {
      List<QRecord> records = query(tableName, filter.withLimit(1));
      return (CollectionUtils.nullSafeIsEmpty(records) ? null : records.get(0));
   }



   /*******************************************************************************
    ** The row with this id, or null.
    *******************************************************************************/
   public static QRecord byId(String tableName, Serializable id) throws QException
   {
      if(id == null)
      {
         return (null);
      }
      return (first(tableName, new QQueryFilter(new QFilterCriteria("id", QCriteriaOperator.EQUALS, id))));
   }



   /*******************************************************************************
    ** Count rows.
    *******************************************************************************/
   public static int count(String tableName, QQueryFilter filter) throws QException
   {
      Integer count = new CountAction().execute(new CountInput(tableName).withFilter(filter).withTransaction(QContext.getQBackendTransaction())).getCount();
      return (count == null ? 0 : count);
   }



   /*******************************************************************************
    ** Insert one row; throws when the insert reports an error.
    *******************************************************************************/
   public static QRecord insert(String tableName, QRecord record) throws QException
   {
      List<QRecord> inserted = InsertAction.executeForRecords(new InsertInput(tableName).withRecord(record).withTransaction(QContext.getQBackendTransaction()));
      QRecord       result   = inserted.get(0);
      throwIfErrors(tableName, result);
      return (result);
   }



   /*******************************************************************************
    ** Update one row (it must carry its id); throws on an error.
    *******************************************************************************/
   public static QRecord update(String tableName, QRecord record) throws QException
   {
      List<QRecord> updated = UpdateAction.executeForRecords(new UpdateInput(tableName).withRecord(record).withTransaction(QContext.getQBackendTransaction()));
      QRecord       result  = updated.get(0);
      throwIfErrors(tableName, result);
      return (result);
   }



   /*******************************************************************************
    ** A filter: field equals value.
    *******************************************************************************/
   public static QQueryFilter eq(String field, Serializable value)
   {
      return (new QQueryFilter(new QFilterCriteria(field, QCriteriaOperator.EQUALS, value)));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void throwIfErrors(String tableName, QRecord record) throws QException
   {
      if(CollectionUtils.nullSafeHasContents(record.getErrors()))
      {
         QStatusMessage error = record.getErrors().get(0);
         throw (new QException("Could not write " + tableName + ": " + error.getMessage()));
      }
   }
}
