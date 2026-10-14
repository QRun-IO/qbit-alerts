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


import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.alerts.AlertsRuntime;
import com.kingsrook.qbits.alerts.engine.AlertsDb;
import com.kingsrook.qbits.alerts.model.AlertsChannel;
import com.kingsrook.qbits.alerts.model.AlertsEscalationPolicy;
import com.kingsrook.qbits.alerts.model.AlertsEscalationStep;
import com.kingsrook.qbits.alerts.model.AlertsGroup;
import com.kingsrook.qbits.alerts.model.AlertsMonitor;
import com.kingsrook.qbits.alerts.model.AlertsMute;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.ChannelType;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.HealthStatus;
import com.kingsrook.qbits.alerts.model.AlertsSubscription;
import com.kingsrook.qbits.alerts.model.AlertsTeam;
import com.kingsrook.qbits.alerts.model.AlertsType;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** The Setup card on the Alerts home: each configuration table, whether it is
 ** set up, and the next step. Reads only the qbit's own small configuration
 ** tables, in the signed-in administrator's session.
 *******************************************************************************/
public final class AlertsSetupStatus
{
   /*******************************************************************************
    ** OK: working. TODO: needs attention. DEFAULT: working on built-in defaults.
    ** OPTIONAL: a feature that is off until set up.
    *******************************************************************************/
   public enum State
   {
      OK, TODO, DEFAULT, OPTIONAL
   }



   /*******************************************************************************
    ** One line of the card. tableName is null for an item with no table.
    *******************************************************************************/
   public record Item(String label, String tableName, State state, String detail, String nextStep)
   {
   }



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private AlertsSetupStatus()
   {
   }



   /*******************************************************************************
    ** Build the items.
    *******************************************************************************/
   public static List<Item> load(AlertsRuntime runtime, Instant now) throws QException
   {
      List<Item> items = new ArrayList<>();

      /////////////
      // channels //
      /////////////
      List<QRecord> channels = AlertsDb.query(AlertsChannel.TABLE_NAME, new QQueryFilter());
      long          external = channels.stream().filter(c -> !List.of(ChannelType.IN_APP.name(), ChannelType.EMAIL.name()).contains(c.getValueString("channelType"))).count();
      long          unhealthy = channels.stream().filter(c -> HealthStatus.UNHEALTHY.name().equals(c.getValueString("healthStatus"))).count();
      if(channels.isEmpty())
      {
         items.add(new Item("Channels", AlertsChannel.TABLE_NAME, State.DEFAULT, "No channel rows yet: the bell and email channels are created when the first alert is routed.",
            "Include alerts-defaults.changelog.sql to create them now, then add Slack, Teams or a webhook if you want them."));
      }
      else if(unhealthy > 0)
      {
         items.add(new Item("Channels", AlertsChannel.TABLE_NAME, State.TODO, unhealthy + (unhealthy == 1 ? " channel is" : " channels are") + " failing.",
            "Open it, check its settings and secret reference, then Send test notification."));
      }
      else
      {
         items.add(new Item("Channels", AlertsChannel.TABLE_NAME, State.OK, plural(channels.size(), "channel") + (external == 0 ? ": the bell and email only." : ", " + external + " of them outside the bell and email."),
            external == 0 ? "Optional: add Slack, Teams, a webhook or PagerDuty." : null));
      }

      ///////////////////////
      // types and monitors //
      ///////////////////////
      int types = AlertsDb.count(AlertsType.TABLE_NAME, AlertsDb.eq("installed", true));
      items.add(types == 0
         ? new Item("Alert types", AlertsType.TABLE_NAME, State.TODO, "No installed alert type.", "Types are declared in code by the installed qbits (QAlertTypeMetaData); open the Alerts home once so they sync.")
         : new Item("Alert types", AlertsType.TABLE_NAME, State.OK, plural(types, "type") + " declared by the installed qbits.", null));

      int monitors = AlertsDb.count(AlertsMonitor.TABLE_NAME, AlertsDb.eq("enabled", true));
      items.add(monitors == 0
         ? new Item("Monitors", AlertsMonitor.TABLE_NAME, State.OPTIONAL, "No monitor is turned on: alerts come only from code that raises them.", "On an alert type, choose Create monitor, or add one with New.")
         : new Item("Monitors", AlertsMonitor.TABLE_NAME, State.OK, plural(monitors, "monitor") + " turned on.", null));

      /////////////////////////////////
      // groups, teams and owner teams //
      /////////////////////////////////
      List<QRecord> groups      = AlertsDb.query(AlertsGroup.TABLE_NAME, new QQueryFilter());
      long          ownedGroups = groups.stream().filter(g -> g.getValueInteger("ownerTeamId") != null).count();
      items.add(ownedGroups == 0
         ? new Item("Groups", AlertsGroup.TABLE_NAME, State.OPTIONAL, plural(groups.size(), "group") + ", none with an owner team.", "Give a group an Owner Team so alerts that nobody subscribed to still reach someone.")
         : new Item("Groups", AlertsGroup.TABLE_NAME, State.OK, plural(groups.size(), "group") + ", " + ownedGroups + " with an owner team.", null));

      int teams = AlertsDb.count(AlertsTeam.TABLE_NAME, new QQueryFilter());
      items.add(teams == 0
         ? new Item("Teams", AlertsTeam.TABLE_NAME, State.OPTIONAL, "No team.", "Add a team and its members to subscribe people together or to own a group.")
         : new Item("Teams", AlertsTeam.TABLE_NAME, State.OK, plural(teams, "team") + ".", null));

      /////////////////////////////////////////////////////
      // subscriptions: nobody hears anything without one //
      /////////////////////////////////////////////////////
      int subscriptions = AlertsDb.count(AlertsSubscription.TABLE_NAME, AlertsDb.eq("enabled", true));
      if(subscriptions > 0)
      {
         items.add(new Item("Subscriptions", AlertsSubscription.TABLE_NAME, State.OK, plural(subscriptions, "subscription") + " turned on.", null));
      }
      else
      {
         items.add(new Item("Subscriptions", AlertsSubscription.TABLE_NAME, ownedGroups > 0 ? State.DEFAULT : State.TODO, "No subscription: " + (ownedGroups > 0 ? "alerts reach only the owner teams of their groups." : "alerts reach nobody."),
            "Use Subscribe me on a type, group or monitor, or add one with New. An alerts admin gets an in-app subscription to errors on first visit."));
      }

      ///////////////////////////////////////
      // escalation, maintenance, scheduler //
      ///////////////////////////////////////
      int policies = AlertsDb.count(AlertsEscalationPolicy.TABLE_NAME, AlertsDb.eq("enabled", true));
      int steps    = AlertsDb.count(AlertsEscalationStep.TABLE_NAME, new QQueryFilter());
      items.add(policies == 0
         ? new Item("Escalation policies", AlertsEscalationPolicy.TABLE_NAME, State.OPTIONAL, "None: alerts are reminded, not escalated.", "Add a policy and its steps, then pick it on a type or group.")
         : steps == 0
            ? new Item("Escalation policies", AlertsEscalationPolicy.TABLE_NAME, State.TODO, plural(policies, "policy") + ", but no steps, so nothing escalates.", "Add steps to the policy.")
            : new Item("Escalation policies", AlertsEscalationPolicy.TABLE_NAME, State.OK, plural(policies, "policy") + ", " + plural(steps, "step") + ".", null));

      int mutes = AlertsDb.count(AlertsMute.TABLE_NAME, AlertsDb.eq("enabled", true).withCriteria("endsAt", QCriteriaOperator.GREATER_THAN, now));
      items.add(new Item("Mutes and maintenance", AlertsMute.TABLE_NAME, State.OPTIONAL, mutes == 0 ? "Nothing is muted." : plural(mutes, "mute") + " active or scheduled.",
         mutes == 0 ? "Add a window before planned maintenance." : null));

      items.add(runtime.getConfig().getSchedulerName() == null
         ? new Item("Scheduler", null, State.TODO, "No scheduler is configured, so monitors, delivery, digests and escalation only run when started by hand.", "Set schedulerName in the Alerts config.")
         : new Item("Scheduler", null, State.OK, "Monitors, delivery, digests and escalation run on the " + runtime.getConfig().getSchedulerName() + " scheduler.", null));
      return (items);
   }



   /*******************************************************************************
    ** "1 channel", "2 channels".
    *******************************************************************************/
   private static String plural(long count, String noun)
   {
      return (count + " " + noun + (count == 1 ? "" : "s"));
   }

}
