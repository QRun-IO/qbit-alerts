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

package com.kingsrook.qbits.alerts.home;


import java.util.List;
import com.kingsrook.qbits.alerts.BaseAlertsTest;
import com.kingsrook.qbits.alerts.engine.AlertsDb;
import com.kingsrook.qbits.alerts.home.AlertsSetupStatus.Item;
import com.kingsrook.qbits.alerts.home.AlertsSetupStatus.State;
import com.kingsrook.qbits.alerts.model.AlertsChannel;
import com.kingsrook.qbits.alerts.model.AlertsGroup;
import com.kingsrook.qbits.alerts.model.AlertsSubscription;
import com.kingsrook.qbits.alerts.model.AlertsTeam;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The Setup card: states follow the real configuration, and the home widget
 ** renders for admins only.
 *******************************************************************************/
class AlertsSetupTest extends BaseAlertsTest
{

   /*******************************************************************************
    **
    *******************************************************************************/
   private Item item(String label) throws Exception
   {
      return (AlertsSetupStatus.load(runtime, clock.instant()).stream().filter(i -> i.label().equals(label)).findFirst().orElseThrow());
   }



   /*******************************************************************************
    ** A fresh install: defaults channels, nobody subscribed, no scheduler.
    *******************************************************************************/
   @Test
   void freshInstallNeedsSubscriptionsAndAScheduler() throws Exception
   {
      runtime.getTypeSync().syncIfNeeded();
      assertThat(item("Channels").state()).isEqualTo(State.DEFAULT);
      assertThat(item("Alert types").state()).isEqualTo(State.OK);
      assertThat(item("Subscriptions").state()).isEqualTo(State.TODO);
      assertThat(item("Scheduler").state()).isEqualTo(State.TODO);
      assertThat(item("Teams").state()).isEqualTo(State.OPTIONAL);
      assertThat(item("Groups").state()).isEqualTo(State.OPTIONAL);
      assertThat(AlertsSetupStatus.load(runtime, clock.instant())).allMatch(i -> i.tableName() == null || i.tableName().startsWith("alerts_"));
   }



   /*******************************************************************************
    ** Configuring a part moves its line.
    *******************************************************************************/
   @Test
   void configuringMovesTheState() throws Exception
   {
      runtime.getTypeSync().syncIfNeeded();
      runtime.getChannels().ensureDefaults();
      assertThat(item("Channels").state()).isEqualTo(State.OK);

      runtime.asSystem(() -> AlertsDb.insert(AlertsSubscription.TABLE_NAME, new QRecord().withValue("subscriberType", "USER").withValue("userId", "u")
         .withValue("scopeType", "ALL").withValue("minSeverity", "ERROR").withValue("enabled", true)));
      assertThat(item("Subscriptions").state()).isEqualTo(State.OK);

      Integer team = runtime.asSystem(() -> AlertsDb.insert(AlertsTeam.TABLE_NAME, new QRecord().withValue("name", "t").withValue("label", "T")).getValueInteger("id"));
      QRecord group = runtime.asSystem(() -> AlertsDb.first(AlertsGroup.TABLE_NAME, new com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter()));
      runtime.asSystem(() -> AlertsDb.update(AlertsGroup.TABLE_NAME, new QRecord().withValue("id", group.getValue("id")).withValue("ownerTeamId", team)));
      assertThat(item("Groups").state()).isEqualTo(State.OK);
      assertThat(item("Teams").state()).isEqualTo(State.OK);

      runtime.asSystem(() -> AlertsDb.update(AlertsChannel.TABLE_NAME, new QRecord().withValue("id", 1).withValue("healthStatus", "UNHEALTHY")));
      assertThat(item("Channels").state()).isEqualTo(State.TODO);
   }



   /*******************************************************************************
    ** The widget renders the card for an admin, with links to the tables.
    *******************************************************************************/
   @Test
   void widgetRendersForAdmins() throws Exception
   {
      runtime.getTypeSync().syncIfNeeded();
      String html = ((com.kingsrook.qqq.backend.core.model.dashboard.widgets.RawHTML) AlertsHome.load(AlertsHome.KIND_SETUP, runtime)).getHtml();
      assertThat(html).contains("Needs setup").contains("href=\"/app/alerts_subscription\"").contains("Next: ").contains("attention");
   }
}
