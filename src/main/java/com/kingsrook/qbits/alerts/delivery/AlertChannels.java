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

package com.kingsrook.qbits.alerts.delivery;


import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.alerts.AlertsRuntime;
import com.kingsrook.qbits.alerts.engine.AlertsDb;
import com.kingsrook.qbits.alerts.model.AlertsChannel;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.ChannelType;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.HealthStatus;
import com.kingsrook.qqq.backend.core.actions.customizers.QCodeLoader;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** The channel types (built-in in-app, email, email list, Slack, Teams,
 ** webhook, PagerDuty and Alertmanager, plus any the config adds) and the two default channel rows every install has: "in-app"
 ** (the bell; always available) and "email" (a person's own address).
 *******************************************************************************/
public class AlertChannels
{
   private static final QLogger LOG = QLogger.getLogger(AlertChannels.class);

   public static final String DEFAULT_IN_APP = "in-app";
   public static final String DEFAULT_EMAIL  = "email";

   private final AlertsRuntime                      runtime;
   private final Map<String, AlertChannelInterface> types = new LinkedHashMap<>();
   private final AlertSecretResolver                secrets = new AlertSecretResolver();



   /*******************************************************************************
    ** Constructor
    *******************************************************************************/
   public AlertChannels(AlertsRuntime runtime)
   {
      this.runtime = runtime;
      register(new InAppChannel());
      register(new EmailChannel(runtime.getConfig(), ChannelType.EMAIL));
      register(new EmailChannel(runtime.getConfig(), ChannelType.EMAIL_LIST));
      for(AlertChannelInterface external : com.kingsrook.qbits.alerts.delivery.channels.ExternalChannels.all())
      {
         register(external);
      }
      for(QCodeReference reference : runtime.getConfig().getChannelTypes())
      {
         try
         {
            AlertChannelInterface channel = QCodeLoader.getAdHoc(AlertChannelInterface.class, reference);
            if(channel != null)
            {
               register(channel);
            }
         }
         catch(Exception e)
         {
            LOG.warn("Could not load an alerts channel type", e, logPair("channelType", reference.getName()));
         }
      }
   }



   /*******************************************************************************
    ** Add (or replace) a channel type.
    *******************************************************************************/
   public void register(AlertChannelInterface channel)
   {
      types.put(channel.getType(), channel);
   }



   /*******************************************************************************
    ** The implementation for a channel type, or null.
    *******************************************************************************/
   public AlertChannelInterface forType(String channelType)
   {
      return (channelType == null ? null : types.get(channelType));
   }



   /*******************************************************************************
    ** The registered type names.
    *******************************************************************************/
   public List<String> typeNames()
   {
      return (List.copyOf(types.keySet()));
   }



   /*******************************************************************************
    ** The secret resolver.
    *******************************************************************************/
   public AlertSecretResolver getSecrets()
   {
      return (secrets);
   }



   /*******************************************************************************
    ** The default in-app or email channel row, created if missing. Runs in the
    ** caller's (system) context.
    *******************************************************************************/
   public QRecord defaultChannel(ChannelType channelType) throws QException
   {
      String  name     = channelType == ChannelType.IN_APP ? DEFAULT_IN_APP : DEFAULT_EMAIL;
      QRecord existing = AlertsDb.first(AlertsChannel.TABLE_NAME, AlertsDb.eq("name", name));
      if(existing != null)
      {
         return (existing);
      }
      return (AlertsDb.insert(AlertsChannel.TABLE_NAME, new QRecord()
         .withValue("name", name)
         .withValue("label", channelType == ChannelType.IN_APP ? "In-App (Bell)" : "Email (Personal Address)")
         .withValue("channelType", channelType.name())
         .withValue("enabled", true)
         .withValue("detailLevel", channelType == ChannelType.IN_APP ? "FULL" : "MINIMAL")
         .withValue("minSeverity", "INFO")
         .withValue("rateLimitPerHour", channelType == ChannelType.IN_APP ? 0 : 60)
         .withValue("healthStatus", HealthStatus.HEALTHY.name())
         .withValue("consecutiveFailures", 0)));
   }



   /*******************************************************************************
    ** Make sure both default channels exist.
    *******************************************************************************/
   public void ensureDefaults() throws QException
   {
      runtime.asSystem(() ->
      {
         defaultChannel(ChannelType.IN_APP);
         defaultChannel(ChannelType.EMAIL);
         return (null);
      });
   }
}
