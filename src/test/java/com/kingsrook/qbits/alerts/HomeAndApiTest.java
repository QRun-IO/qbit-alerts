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


import java.time.Duration;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.alerts.api.AlertsApi;
import com.kingsrook.qbits.alerts.engine.AlertsDb;
import com.kingsrook.qbits.alerts.home.AlertSummaryLoader;
import com.kingsrook.qbits.alerts.home.AlertsHome;
import com.kingsrook.qbits.alerts.metadata.AlertsHomeWidgetsMetaDataProducer;
import com.kingsrook.qbits.alerts.model.AlertsChannel;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.Resolution;
import com.kingsrook.qbits.alerts.model.AlertsSubscription;
import com.kingsrook.qqq.backend.core.actions.dashboard.RenderWidgetAction;
import com.kingsrook.qqq.backend.core.alerts.AlertRecordRef;
import com.kingsrook.qqq.backend.core.alerts.AlertSeverity;
import com.kingsrook.qqq.backend.core.alerts.AlertSignal;
import com.kingsrook.qqq.backend.core.alerts.AlertsInstanceMetaData;
import com.kingsrook.qqq.backend.core.alerts.QAlertTypeMetaData;
import com.kingsrook.qqq.backend.core.exceptions.QBadRequestException;
import com.kingsrook.qqq.backend.core.exceptions.QPermissionDeniedException;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetInput;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetOutput;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.ChartData;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.QWidgetData;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.RawHTML;
import com.kingsrook.qqq.backend.core.model.dashboard.widgets.TableData;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** The Alerts home widgets (real data and honest empty states), the summary
 ** loader for other pages, and the bell / record-panel API.
 *******************************************************************************/
class HomeAndApiTest extends BaseAlertsTest
{
   static final String TYPE = "test.widget.broken";



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   protected void customizeInstance(QInstance qInstance)
   {
      AlertsInstanceMetaData.ofOrWithNew(qInstance)
         .withAlertType(new QAlertTypeMetaData().withName(TYPE).withLabel("Widget broken").withGroupName("widgets").withSubjectTableName(WIDGET).withDefaultSeverity(AlertSeverity.ERROR));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private QWidgetData render(String widgetName) throws Exception
   {
      RenderWidgetOutput output = new RenderWidgetAction().execute(new RenderWidgetInput().withWidgetMetaData(qInstance.getWidget(widgetName)));
      return (output.getWidgetData());
   }



   /*******************************************************************************
    ** Empty install: every widget renders an honest empty state.
    *******************************************************************************/
   @Test
   void testEmptyHome() throws Exception
   {
      for(String name : AlertsHomeWidgetsMetaDataProducer.homeWidgetNames())
      {
         QWidgetData data = render(name);
         assertThat(data).as(name).isNotNull();
         if(data instanceof TableData table)
         {
            assertThat(table.getRows()).as(name).isEmpty();
            assertThat(table.getNoRowsFoundHTML()).as(name).isNotBlank();
         }
      }
      assertThat(((RawHTML) render(AlertsHomeWidgetsMetaDataProducer.SEVERITY)).getHtml()).contains("No open alerts").contains("No scheduler is configured");
      assertThat(((RawHTML) render(AlertsHomeWidgetsMetaDataProducer.DELIVERY)).getHtml()).contains("delivered normally");
   }



   /*******************************************************************************
    ** With data: tiles count by severity, lists sort by severity then age,
    ** groups, trend, noisy types, delivery health and recent resolutions.
    *******************************************************************************/
   @Test
   void testHomeWithData() throws Exception
   {
      runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("a").withTitle("Widget A broken"));
      clock.advance(Duration.ofMinutes(5));
      runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("b").withTitle("Widget B broken").withSeverity(AlertSeverity.CRITICAL));
      runtime.getEngine().raise(new AlertSignal("test.plain.thing").withDedupKey("c").withTitle("Plain thing").withSeverity(AlertSeverity.WARNING));
      QRecord resolved = runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("d").withTitle("Widget D broken"));
      clock.advance(Duration.ofMinutes(90));
      runtime.getEngine().resolveById(resolved.getValueInteger("id"), Resolution.MANUAL, ADMIN, "fixed");
      runtime.getEngine().assign(resolved.getValueInteger("id"), ADMIN, null, ADMIN);
      runtime.getChannels().ensureDefaults();
      QRecord email = runtime.asSystem(() -> AlertsDb.first(AlertsChannel.TABLE_NAME, AlertsDb.eq("name", "email")));
      runtime.asSystem(() -> AlertsDb.update(AlertsChannel.TABLE_NAME, new QRecord().withValue("id", email.getValue("id")).withValue("healthStatus", "PROBATION").withValue("lastError", "smtp down")));

      String tiles = ((RawHTML) render(AlertsHomeWidgetsMetaDataProducer.SEVERITY)).getHtml();
      assertThat(tiles).contains("Critical").contains("3 open alerts you can see");

      TableData action = (TableData) render(AlertsHomeWidgetsMetaDataProducer.ACTION);
      assertThat(action.getRows()).hasSize(3);
      assertThat(String.valueOf(action.getRows().get(0).get("alert"))).contains("Widget B broken");

      TableData groups = (TableData) render(AlertsHomeWidgetsMetaDataProducer.BY_GROUP);
      assertThat(groups.getRows()).hasSize(2);

      ChartData trend = (ChartData) render(AlertsHomeWidgetsMetaDataProducer.TREND);
      assertThat(trend.getSublabel()).contains("Mean time to resolve");

      TableData noisy = (TableData) render(AlertsHomeWidgetsMetaDataProducer.NOISY);
      assertThat(noisy.getRows()).isNotEmpty();

      TableData recent = (TableData) render(AlertsHomeWidgetsMetaDataProducer.RESOLVED);
      assertThat(recent.getRows()).hasSize(1);
      assertThat(String.valueOf(recent.getRows().get(0).get("duration"))).isEqualTo("1 h 30 min");

      QWidgetData delivery = render(AlertsHomeWidgetsMetaDataProducer.DELIVERY);
      assertThat(((TableData) delivery).getRows()).hasSize(1);

      TableData mine = (TableData) render(AlertsHomeWidgetsMetaDataProducer.MINE);
      assertThat(mine.getRows()).isEmpty();
      assertThat(mine.getNoRowsFoundHTML()).contains("Nothing needs you");

      //////////////////////////////////////////////////////////////
      // an outsider can't read the widget table: one alert only   //
      //////////////////////////////////////////////////////////////
      loginAs(OUTSIDER);
      assertThat(((RawHTML) render(AlertsHomeWidgetsMetaDataProducer.SEVERITY)).getHtml()).contains("1 open alert you can see");

      loginAs(ADMIN);
      List<AlertSummaryLoader.GroupSummary> summaries = AlertSummaryLoader.summarize(AlertSeverity.ERROR);
      assertThat(summaries).hasSize(1);
      assertThat(summaries.get(0).line()).isEqualTo("Widgets · 1 critical, 1 error");
      assertThat(AlertSummaryLoader.needsAttentionHtml()).contains("All alerts").contains("Widgets");
      assertThat(AlertSummaryLoader.summarize(null)).hasSize(2);
      assertThat(AlertsHome.load("nope", runtime)).isInstanceOf(RawHTML.class);
   }



   /*******************************************************************************
    ** The bell: unread count, highest severity, latest items; mark read.
    *******************************************************************************/
   @Test
   void testInbox() throws Exception
   {
      AlertsApi api = new AlertsApi(runtime);
      assertThat(api.inboxSummary().get("unreadCount")).isEqualTo(0);

      runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("a").withTitle("Widget A broken"));
      runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("b").withTitle("Widget B broken").withSeverity(AlertSeverity.CRITICAL));

      Map<String, Object> summary = api.inboxSummary();
      assertThat(summary.get("unreadCount")).isEqualTo(2);
      assertThat(summary.get("highestUnreadSeverity")).isEqualTo("CRITICAL");
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> items = (List<Map<String, Object>>) summary.get("items");
      assertThat(items).hasSize(2);
      assertThat(items.get(0)).containsKeys("id", "title", "href", "source", "category", "age", "unread", "alertId");

      api.markRead(Map.of("alertId", items.get(0).get("alertId")));
      assertThat(api.inboxSummary().get("unreadCount")).isEqualTo(1);
      assertThat(api.markRead(Map.of("all", true)).get("updated")).isEqualTo(1);
      assertThat(api.inboxSummary().get("unreadCount")).isEqualTo(0);
      assertThatThrownBy(() -> api.markRead(Map.of())).isInstanceOf(QBadRequestException.class);

      loginAs("nobody-1");
      assertThatThrownBy(api::inboxSummary).isInstanceOf(QPermissionDeniedException.class);
   }



   /*******************************************************************************
    ** The record panel: hidden with nothing to show; open alerts and recent
    ** resolutions otherwise; legacy watch rows and collaboration capability.
    *******************************************************************************/
   @Test
   void testRecordPanel() throws Exception
   {
      AlertsApi     api     = new AlertsApi(runtime);
      List<Integer> widgets = insertWidgets("BROKEN", "OK");
      assertThat(api.recordPanel(WIDGET, String.valueOf(widgets.get(1))).get("visible")).isEqualTo(false);

      runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("a").withTitle("Widget A broken").withRecordRef(AlertRecordRef.of(WIDGET, widgets.get(0))));
      QRecord gone = runtime.getEngine().raise(new AlertSignal(TYPE).withDedupKey("z").withTitle("Old problem").withRecordRef(AlertRecordRef.of(WIDGET, widgets.get(0))).withRecordRef(AlertRecordRef.of(WIDGET, widgets.get(1))));
      runtime.getEngine().resolveById(gone.getValueInteger("id"), Resolution.MANUAL, ADMIN, "done");

      Map<String, Object> panel = api.recordPanel(WIDGET, String.valueOf(widgets.get(0)));
      assertThat(panel.get("visible")).isEqualTo(true);
      assertThat(panel.get("openCount")).isEqualTo(1);
      assertThat(panel.get("resolvedLast30Days")).isEqualTo(1);
      assertThat(panel.get("canManage")).isEqualTo(true);
      assertThat(panel.get("watching")).isEqualTo(false);

      Map<String, Object> other = api.recordPanel(WIDGET, String.valueOf(widgets.get(1)));
      assertThat(other.get("visible")).isEqualTo(true);
      assertThat(other.get("openCount")).isEqualTo(0);

      assertThat(panel.get("follow")).isEqualTo(Map.of("available", false));
      com.kingsrook.qqq.middleware.javalin.specs.extensions.MiddlewareSpecExtensionsMetaData.ofOrWithNew(qInstance)
         .withExtension(new com.kingsrook.qqq.middleware.javalin.specs.extensions.MiddlewareSpecExtension().withName("collaboration").withSemanticVersion("1.0.0"));
      assertThat(api.recordPanel(WIDGET, String.valueOf(widgets.get(0))).get("follow")).isEqualTo(Map.of("available", true));
      runtime.asSystem(() -> AlertsDb.insert(AlertsSubscription.TABLE_NAME, new QRecord()
         .withValue("subscriberType", "USER").withValue("userId", ADMIN).withValue("scopeType", "RECORD")
         .withValue("scopeValue", WIDGET).withValue("scopeRecordId", String.valueOf(widgets.get(0)))
         .withValue("enabled", true)));
      assertThat(api.recordPanel(WIDGET, String.valueOf(widgets.get(0))).get("watching")).isEqualTo(true);
      assertThat(all(AlertsSubscription.TABLE_NAME)).anyMatch(s -> "RECORD".equals(s.getValueString("scopeType")));

      assertThatThrownBy(() -> api.recordPanel("nope", "1")).isInstanceOf(QBadRequestException.class);
      loginAs(OUTSIDER);
      assertThatThrownBy(() -> api.recordPanel(WIDGET, "1")).isInstanceOf(QPermissionDeniedException.class);
   }
}
