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


import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.alerts.AlertsQBitConfig;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.ChannelType;
import com.kingsrook.qqq.backend.core.actions.messaging.SendMessageAction;
import com.kingsrook.qqq.backend.core.model.actions.messaging.Content;
import com.kingsrook.qqq.backend.core.model.actions.messaging.MultiParty;
import com.kingsrook.qqq.backend.core.model.actions.messaging.Party;
import com.kingsrook.qqq.backend.core.model.actions.messaging.SendMessageInput;
import com.kingsrook.qqq.backend.core.model.actions.messaging.email.EmailContentRole;
import com.kingsrook.qqq.backend.core.model.actions.messaging.email.EmailPartyRole;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.core.utils.StringUtils;


/*******************************************************************************
 ** Email to a person (EMAIL: the address in their settings) or to a fixed
 ** list (EMAIL_LIST: settings {"addresses": ["ops@example.com", ...]}), sent
 ** through QQQ's SendMessageAction and the configured messaging provider
 ** (SMTP or SES). Plain text plus simple HTML, with a link to change or stop
 ** the emails. Without a messaging provider the channel fails permanently and
 ** says why.
 *******************************************************************************/
public class EmailChannel implements AlertChannelInterface
{
   private final AlertsQBitConfig config;
   private final String           type;



   /*******************************************************************************
    ** Constructor
    *******************************************************************************/
   public EmailChannel(AlertsQBitConfig config, ChannelType type)
   {
      this.config = config;
      this.type = type.name();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public String getType()
   {
      return (type);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public List<String> validate(QRecord channel, AlertSecretResolver secrets)
   {
      List<String> problems = new ArrayList<>();
      if(!StringUtils.hasContent(config.getMessagingProviderName()))
      {
         problems.add("Email is not configured: set messagingProviderName in the Alerts QBit config.");
      }
      if(!StringUtils.hasContent(config.getFromAddress()))
      {
         problems.add("Email has no From address: set fromAddress in the Alerts QBit config.");
      }
      if(ChannelType.EMAIL_LIST.name().equals(type) && listAddresses(channel).isEmpty())
      {
         problems.add("Add the list's addresses to the channel's settings: {\"addresses\": [\"ops@example.com\"]}.");
      }
      return (problems);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public DeliveryResult deliver(AlertMessage message, QRecord channel, String recipientAddress, AlertSecretResolver secrets)
   {
      List<String> problems = validate(channel, secrets);
      if(!problems.isEmpty())
      {
         return (DeliveryResult.failed(problems.get(0)));
      }

      List<String> addresses = ChannelType.EMAIL_LIST.name().equals(type) ? listAddresses(channel) : (recipientAddress == null ? List.of() : List.of(recipientAddress));
      if(addresses.isEmpty())
      {
         return (DeliveryResult.failed("No email address."));
      }

      try
      {
         MultiParty to = new MultiParty();
         for(String address : addresses)
         {
            to.addParty(new Party().withAddress(address).withRole(EmailPartyRole.TO));
         }
         new SendMessageAction().execute(new SendMessageInput()
            .withMessagingProviderName(config.getMessagingProviderName())
            .withFrom(new Party().withAddress(config.getFromAddress()).withRole(EmailPartyRole.FROM))
            .withTo(to)
            .withSubject(message.subject())
            .withContent(new Content().withContentRole(EmailContentRole.TEXT).withBody(message.text()))
            .withContent(new Content().withContentRole(EmailContentRole.HTML).withBody(message.html())));
         return (DeliveryResult.sent(null));
      }
      catch(Exception e)
      {
         return (DeliveryResult.retry(AlertRedactor.redact(e.getClass().getSimpleName() + ": " + e.getMessage())));
      }
   }



   /*******************************************************************************
    ** The addresses in an email list channel's settings.
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   static List<String> listAddresses(QRecord channel)
   {
      List<String> addresses = new ArrayList<>();
      String       settings  = channel == null ? null : channel.getValueString("settings");
      if(!StringUtils.hasContent(settings))
      {
         return (addresses);
      }
      try
      {
         Map<String, Serializable> map   = JsonUtils.toObject(settings, Map.class);
         Object                    value = map.get("addresses");
         if(value instanceof List<?> list)
         {
            for(Object address : list)
            {
               if(address != null && StringUtils.hasContent(String.valueOf(address)))
               {
                  addresses.add(String.valueOf(address).trim());
               }
            }
         }
      }
      catch(Exception e)
      {
         //////////////////////////////////////////////////////
         // unreadable settings: no addresses (validate says) //
         //////////////////////////////////////////////////////
      }
      return (addresses);
   }
}
