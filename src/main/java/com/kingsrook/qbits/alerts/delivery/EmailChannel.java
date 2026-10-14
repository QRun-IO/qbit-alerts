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
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.alerts.AlertsQBitConfig;
import com.kingsrook.qbits.alerts.email.AlertsEmail;
import com.kingsrook.qbits.alerts.email.AlertsEmailSender;
import com.kingsrook.qbits.alerts.metadata.AlertsProcessesMetaDataProducer;
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
 ** the emails. When qbit-email is produced in the instance, each recipient gets
 ** their own message through it (template alerts_alert, NOTIFICATION); the
 ** messaging provider is then not needed. Without either, the channel fails
 ** permanently and says why.
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
      if(!AlertsEmail.isActive())
      {
         if(!StringUtils.hasContent(config.getMessagingProviderName()))
         {
            problems.add("Email is not configured: set messagingProviderName in the Alerts QBit config.");
         }
         if(!StringUtils.hasContent(config.getFromAddress()))
         {
            problems.add("Email has no From address: set fromAddress in the Alerts QBit config.");
         }
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

      if(AlertsEmail.isActive())
      {
         return (deliverThroughEmailQBit(message, addresses));
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
    ** One qbit-email message per address. A retry is safe: the idempotency key
    ** makes the already-queued ones DUPLICATE. Refused is permanent;
    ** suppressed (the person unsubscribed) is reported, not retried.
    *******************************************************************************/
   private DeliveryResult deliverThroughEmailQBit(AlertMessage message, List<String> addresses)
   {
      List<Map<String, Serializable>> items = new ArrayList<>();
      for(AlertMessage.Entry entry : message.entries())
      {
         Map<String, Serializable> item = new LinkedHashMap<>();
         put(item, "title", entry.title());
         put(item, "severity", entry.severity());
         put(item, "typeLabel", entry.typeLabel());
         put(item, "group", entry.group());
         put(item, "summary", entry.summary());
         put(item, "itemCount", entry.itemCount());
         items.add(item);
      }
      String  base       = StringUtils.hasContent(config.getPublicBaseUrl()) ? config.getPublicBaseUrl().replaceAll("/+$", "") : "";
      Instant now        = config.getRuntime() == null ? Instant.now() : config.getRuntime().now();
      String  process    = AlertsProcessesMetaDataProducer.DELIVER_NOTIFICATIONS;
      String  problem    = null;
      int     suppressed = 0;
      Integer firstId    = null;
      try
      {
         for(String address : addresses)
         {
            AlertsEmailSender.Result result = "DIGEST".equals(message.kind())
               ? AlertsEmailSender.sendAlertDigest(address, AlertsProcessesMetaDataProducer.SEND_DIGESTS, message.idempotencyKey(), message.alertIds().get(0), message.alertIds().size(),
                  digestItems(message), base + "/app/alerts_subscription")
               : AlertsEmailSender.sendAlert(address, process, message.subject(), message.severity(), message.kind(), items, message.link(),
               base + "/app/alerts_subscription", message.alertIds(), now);
            firstId = firstId == null ? result.messageId() : firstId;
            if(result.status() == AlertsEmailSender.Status.REFUSED && problem == null)
            {
               problem = AlertRedactor.redact(result.detail());
            }
            suppressed += result.status() == AlertsEmailSender.Status.SUPPRESSED ? 1 : 0;
         }
      }
      catch(Exception e)
      {
         return (DeliveryResult.retry(AlertRedactor.redact(e.getClass().getSimpleName() + ": " + e.getMessage())));
      }
      if(problem != null)
      {
         return (DeliveryResult.failed("Email was refused: " + problem));
      }
      if(suppressed == addresses.size())
      {
         return (DeliveryResult.failed("Email was suppressed: the recipient has unsubscribed."));
      }
      return (DeliveryResult.sent(firstId == null ? null : String.valueOf(firstId)));
   }



   /*******************************************************************************
    ** Keep the same sectioned, redacted content in the shared digest template.
    *******************************************************************************/
   private static List<Map<String, Serializable>> digestItems(AlertMessage message)
   {
      Map<String, Serializable> item = new LinkedHashMap<>();
      item.put("title", message.subject());
      item.put("body", message.text());
      item.put("link", message.link());
      item.put("linkLabel", "Open alerts");
      return (List.of(item));
   }



   /*******************************************************************************
    ** Put a value in a template item unless it is null.
    *******************************************************************************/
   private static void put(Map<String, Serializable> item, String name, Serializable value)
   {
      if(value != null)
      {
         item.put(name, value);
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
