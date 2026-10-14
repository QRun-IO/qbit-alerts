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


import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.alerts.BaseAlertsTest;
import com.kingsrook.qbits.alerts.model.AlertsNotification;
import com.kingsrook.qqq.backend.core.actions.dashboard.RenderWidgetAction;
import com.kingsrook.qqq.backend.core.actions.dashboard.widgets.ChildRecordListRenderer;
import com.kingsrook.qqq.backend.core.alerts.NotificationAudience;
import com.kingsrook.qqq.backend.core.alerts.NotificationCategory;
import com.kingsrook.qqq.backend.core.alerts.NotificationSeverity;
import com.kingsrook.qqq.backend.core.alerts.NotificationSignal;
import com.kingsrook.qqq.backend.core.alerts.QAlerts;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetInput;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.ChildRecordListData;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.QWidgetData;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaDataInterface;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The record-view child lists serialize to JSON (the payload the record page
 ** fetches). The child table's meta-data carries the qbit config, whose
 ** runtime points back at the config: unless that back-reference is ignored,
 ** Jackson recurses until its nesting limit ("Error in JSON Serialization").
 *******************************************************************************/
class ChildListWidgetsJsonTest extends BaseAlertsTest
{

   /*******************************************************************************
    ** The qbit config itself serializes, without its runtime.
    *******************************************************************************/
   @Test
   void testConfigSerializesWithoutRuntime()
   {
      assertThat(config.getRuntime()).isNotNull();
      assertThat(JsonUtils.toJson(config)).doesNotContain("\"runtime\"").doesNotContain("\"audienceResolver\"");
   }



   /*******************************************************************************
    ** A notification's Recipients list, with recipient rows (the kof22 case).
    *******************************************************************************/
   @Test
   void testRecipientsChildListSerializes() throws Exception
   {
      QAlerts.notify(new NotificationSignal("Run backup now finished with errors").withSeverity(NotificationSeverity.ERROR)
         .withCategory(NotificationCategory.JOB).withAudience(NotificationAudience.users(VIEWER, ADMIN)));
      QRecord notification = runtime.asSystem(() -> all(AlertsNotification.TABLE_NAME)).get(0);

      loginAs(ADMIN);
      QWidgetMetaDataInterface widget = qInstance.getWidget(AlertsTableMetaDataHelper.JOIN_NOTIFICATION_RECIPIENT);
      assertThat(widget).isNotNull();

      ChildRecordListData data = (ChildRecordListData) render(widget, notification.getValue("id"));
      assertThat(data.getQueryOutput().getRecords()).hasSize(2);

      String json = JsonUtils.toJson(data);
      assertThat(json).contains("\"totalRows\":2").doesNotContain("\"runtime\"");
   }



   /*******************************************************************************
    ** Every child list in the qbit (rendered without a parent, as on an insert
    ** screen, which still sends the child table's meta-data).
    *******************************************************************************/
   @Test
   void testEveryChildListSerializes() throws Exception
   {
      loginAs(ADMIN);
      List<String> rendered = new ArrayList<>();
      for(QWidgetMetaDataInterface widget : qInstance.getWidgets().values())
      {
         if(widget.getCodeReference() != null && ChildRecordListRenderer.class.getName().equals(widget.getCodeReference().getName()))
         {
            String json = JsonUtils.toJson(render(widget, null));
            assertThat(json).as(widget.getName()).contains("\"childTableMetaData\"").doesNotContain("\"runtime\"");
            rendered.add(widget.getName());
         }
      }
      assertThat(rendered).contains(AlertsTableMetaDataHelper.JOIN_NOTIFICATION_RECIPIENT).hasSizeGreaterThan(5);
   }



   /*******************************************************************************
    ** Render a child-list widget as the record page does (its default values as
    ** query params, plus the parent id when there is one).
    *******************************************************************************/
   private QWidgetData render(QWidgetMetaDataInterface widget, Serializable id) throws Exception
   {
      Map<String, String> params = new HashMap<>();
      widget.getDefaultValues().forEach((key, value) -> params.put(key, value == null ? null : String.valueOf(value)));
      if(id != null)
      {
         params.put("id", String.valueOf(id));
      }

      RenderWidgetInput input = new RenderWidgetInput().withWidgetMetaData(widget);
      input.setQueryParams(params);
      return (new RenderWidgetAction().execute(input).getWidgetData());
   }
}
