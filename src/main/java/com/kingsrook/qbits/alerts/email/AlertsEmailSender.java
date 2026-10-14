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

package com.kingsrook.qbits.alerts.email;


import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.kingsrook.qbits.alerts.model.AlertsAlert;
import com.kingsrook.qbits.alerts.model.AlertsNotification;
import com.kingsrook.qbits.email.EmailRuntime;
import com.kingsrook.qbits.email.outbox.EmailCategory;
import com.kingsrook.qbits.email.outbox.EmailRequest;
import com.kingsrook.qbits.email.outbox.EmailSendResult;
import com.kingsrook.qbits.email.outbox.EmailService;
import com.kingsrook.qbits.email.template.EmailTemplateDefinition;
import com.kingsrook.qbits.email.template.EmailTemplates;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;


/*******************************************************************************
 ** The one place that names qbit-email classes. Callers reach it only after
 ** {@link AlertsEmail#isActive()}. It registers the alerts templates on first
 ** use (never overwriting people's edits) and sends one message per recipient
 ** through {@code EmailService}, all in the NOTIFICATION category:
 ** - alerts_alert: an alert channel email (one alert or a batch);
 ** - alerts_notification: one user notification;
 ** - alerts_digest: a user's daily digest of notifications.
 ** Titles, summaries and bodies passed as variables are the existing
 ** redacted, external-safe text. No record labels are passed.
 *******************************************************************************/
public final class AlertsEmailSender
{
   public static final String SENDER_QBIT = "qbit-alerts";

   public static final String TEMPLATE_ALERT        = "alerts_alert";
   public static final String TEMPLATE_NOTIFICATION = "alerts_notification";
   public static final String TEMPLATE_DIGEST       = "alerts_digest";



   /*******************************************************************************
    ** The outcome of one send, in this qbit's own terms.
    **
    ** @param status    what qbit-email did
    ** @param messageId the email_message id (null when refused)
    ** @param detail    plain reasons (refusals), or null
    *******************************************************************************/
   public record Result(Status status, Integer messageId, String detail)
   {
   }



   /*******************************************************************************
    ** Mirrors qbit-email's send statuses.
    *******************************************************************************/
   public enum Status
   {
      QUEUED, DUPLICATE, SUPPRESSED, REFUSED
   }



   /*******************************************************************************
    ** Utility class.
    *******************************************************************************/
   private AlertsEmailSender()
   {
   }



   /*******************************************************************************
    ** Whether the email qbit is produced in the instance.
    *******************************************************************************/
   static boolean isInstalled(QInstance qInstance)
   {
      return (EmailRuntime.config(qInstance) != null);
   }



   /*******************************************************************************
    ** An alert channel email to one recipient. The key carries the message kind
    ** and the minute so a reminder, or an alert that reopens, is not mistaken
    ** for a repeat of the first email.
    **
    ** @param to          the address
    ** @param process     the sending process name
    ** @param subject     the rendered one-line subject
    ** @param severity    the highest severity
    ** @param messageKind RAISED, RESOLVED, REMINDER, ESCALATED, DIGEST or TEST
    ** @param items       one map per alert: title, severity, typeLabel, group, summary, itemCount
    ** @param link        the alert (or list) in the app, when absolute
    ** @param settingsUrl where to change or stop the emails, when absolute
    ** @param alertIds    the alerts in the message
    ** @param now         the time, to tell repeats apart
    *******************************************************************************/
   public static Result sendAlert(String to, String process, String subject, String severity, String messageKind, List<Map<String, Serializable>> items, String link, String settingsUrl,
                                  List<Integer> alertIds, Instant now) throws QException
   {
      ensure(TEMPLATE_ALERT);
      List<Integer> ids   = alertIds == null ? List.of() : alertIds.stream().filter(id -> id != null).toList();
      Integer       first = ids.isEmpty() ? null : ids.get(0);
      String        idPart = ids.isEmpty() ? "test" : String.join("-", ids.stream().map(String::valueOf).toList());
      String        key   = "alerts:alert:" + idPart + ":" + hash(to) + ":" + messageKind + ":" + (now.getEpochSecond() / 60);

      EmailRequest request = base(to, process, key)
         .withTemplateName(TEMPLATE_ALERT)
         .withVariable("subject", subject)
         .withVariable("severity", severity)
         .withVariable("kind", messageKind)
         .withVariable("items", (Serializable) items);
      if(first != null)
      {
         request.withLinkedRecord(AlertsAlert.TABLE_NAME, first);
      }
      withUrl(request, "link", link);
      withUrl(request, "settings_url", settingsUrl);
      return (result(EmailService.send(request)));
   }



   /*******************************************************************************
    ** One user notification to one recipient.
    **
    ** @param to             the address
    ** @param process        the sending process name
    ** @param notificationId the notification row
    ** @param item           title, body (absent for SECURITY), link and linkLabel
    ** @param settingsUrl    where to change these emails, when absolute
    *******************************************************************************/
   public static Result sendNotification(String to, String process, Integer notificationId, Map<String, Serializable> item, String settingsUrl) throws QException
   {
      ensure(TEMPLATE_NOTIFICATION);
      EmailRequest request = base(to, process, "alerts:notification:" + notificationId + ":" + hash(to))
         .withTemplateName(TEMPLATE_NOTIFICATION)
         .withLinkedRecord(AlertsNotification.TABLE_NAME, notificationId);
      for(Map.Entry<String, Serializable> entry : item.entrySet())
      {
         if("link".equals(entry.getKey()))
         {
            withUrl(request, "link", (String) entry.getValue());
         }
         else
         {
            request.withVariable(entry.getKey(), entry.getValue());
         }
      }
      withUrl(request, "settings_url", settingsUrl);
      return (result(EmailService.send(request)));
   }



   /*******************************************************************************
    ** A user's digest.
    **
    ** @param to                  the address
    ** @param process             the sending process name
    ** @param userId              the user
    ** @param windowStart         the start of the digest window (the digest hour)
    ** @param firstNotificationId the notification row the message links to
    ** @param items               one map per notification: title, body, link, linkLabel
    ** @param settingsUrl         where to change these emails, when absolute
    *******************************************************************************/
   public static Result sendDigest(String to, String process, String userId, Instant windowStart, Integer firstNotificationId, List<Map<String, Serializable>> items,
                                   String settingsUrl) throws QException
   {
      ensure(TEMPLATE_DIGEST);
      EmailRequest request = base(to, process, "alerts:digest:" + userId + ":" + windowStart)
         .withTemplateName(TEMPLATE_DIGEST)
         .withVariable("count", items.size())
         .withVariable("items", (Serializable) items)
         .withLinkedRecord(AlertsNotification.TABLE_NAME, firstNotificationId);
      withUrl(request, "settings_url", settingsUrl);
      return (result(EmailService.send(request)));
   }



   /*******************************************************************************
    ** The parts every message has: one recipient, the sender and the key.
    *******************************************************************************/
   private static EmailRequest base(String to, String process, String idempotencyKey)
   {
      return (new EmailRequest().withTo(to).withCategory(EmailCategory.NOTIFICATION).withSender(SENDER_QBIT, process).withIdempotencyKey(idempotencyKey));
   }



   /*******************************************************************************
    ** Pass a URL only when it is absolute http(s); the template schema refuses
    ** relative links, and they are useless in a mail anyway.
    *******************************************************************************/
   private static void withUrl(EmailRequest request, String name, String url)
   {
      if(url != null && (url.startsWith("https://") || url.startsWith("http://")))
      {
         request.withVariable(name, url);
      }
   }



   /*******************************************************************************
    ** Translate qbit-email's result.
    *******************************************************************************/
   private static Result result(EmailSendResult sent)
   {
      String detail = sent.reasons() == null || sent.reasons().isEmpty() ? null : String.join("; ", sent.reasons());
      return (new Result(Status.valueOf(sent.status().name()), sent.messageId(), detail));
   }



   /*******************************************************************************
    ** A short, stable, non-reversible id for an address (for idempotency keys,
    ** so no address is stored in them).
    *******************************************************************************/
   static String hash(String address)
   {
      try
      {
         byte[]        digest = MessageDigest.getInstance("SHA-256").digest(address.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
         StringBuilder hex    = new StringBuilder();
         for(int i = 0; i < 8; i++)
         {
            hex.append(String.format("%02x", digest[i]));
         }
         return (hex.toString());
      }
      catch(NoSuchAlgorithmException e)
      {
         throw (new IllegalStateException(e));
      }
   }



   /*******************************************************************************
    ** Register the template when it does not exist yet (people's edits are
    ** never overwritten).
    *******************************************************************************/
   private static void ensure(String name) throws QException
   {
      EmailTemplates.ensure(switch(name)
      {
         case TEMPLATE_ALERT -> new EmailTemplateDefinition()
            .withName(TEMPLATE_ALERT).withLabel("Alerts: alert").withCategory(EmailCategory.NOTIFICATION).withKind("MARKDOWN").withOwnerQbit(SENDER_QBIT)
            .withSubject("{{subject}}")
            .withBody("""
               {{#each items}}**{{this.title}}**

               {{this.severity}} - {{this.typeLabel}}{{#if this.group}} / {{this.group}}{{/if}}

               {{#if this.summary}}{{this.summary}}

               {{/if}}{{#if this.itemCount}}{{this.itemCount}} records

               {{/if}}{{/each}}{{#if link}}[Open in Alerts]({{link}}){.button}

               {{/if}}{{#if settings_url}}[Change or stop these emails]({{settings_url}})

               {{/if}}Personal data and secrets are removed from alert messages.""")
            .withVariable("subject", "Subject", "STRING", true, null)
            .withVariable("severity", "Highest severity", "STRING", false, null)
            .withVariable("kind", "Message kind", "STRING", false, null)
            .withVariable("items", "Alerts", "LIST", true, null)
            .withVariable("link", "Link", "URL", false, null)
            .withVariable("settings_url", "Settings link", "URL", false, null)
            .withSampleVariables("{\"subject\":\"[ERROR] Export failed\",\"severity\":\"ERROR\",\"kind\":\"RAISED\",\"items\":[{\"title\":\"Export failed\",\"severity\":\"ERROR\","
               + "\"typeLabel\":\"Jobs\",\"summary\":\"3 records\"}],\"link\":\"https://example.com/app/alerts_alert/1\",\"settings_url\":\"https://example.com/app/alerts_subscription\"}");
         case TEMPLATE_NOTIFICATION -> new EmailTemplateDefinition()
            .withName(TEMPLATE_NOTIFICATION).withLabel("Alerts: notification").withCategory(EmailCategory.NOTIFICATION).withKind("MARKDOWN").withOwnerQbit(SENDER_QBIT)
            .withSubject("{{title}}")
            .withBody("""
               {{#if body}}{{body}}

               {{/if}}{{#if link}}[{{linkLabel|Open}}]({{link}}){.button}

               {{/if}}{{#if settings_url}}Change these emails in [My Notification Settings]({{settings_url}}).{{/if}}""")
            .withVariable("title", "Title", "STRING", true, null)
            .withVariable("body", "Body", "STRING", false, null)
            .withVariable("link", "Link", "URL", false, null)
            .withVariable("linkLabel", "Link label", "STRING", false, null)
            .withVariable("settings_url", "Settings link", "URL", false, null)
            .withSampleVariables("{\"title\":\"Export failed\",\"body\":\"The nightly export did not finish.\",\"link\":\"https://example.com/app/widget\",\"linkLabel\":\"Open\","
               + "\"settings_url\":\"https://example.com/app/alerts_notification_preference\"}");
         case TEMPLATE_DIGEST -> new EmailTemplateDefinition()
            .withName(TEMPLATE_DIGEST).withLabel("Alerts: daily digest").withCategory(EmailCategory.NOTIFICATION).withKind("MARKDOWN").withOwnerQbit(SENDER_QBIT)
            .withSubject("{{count}} new notifications")
            .withBody("""
               {{#each items}}**{{this.title}}**

               {{#if this.body}}{{this.body}}

               {{/if}}{{#if this.link}}[{{this.linkLabel|Open}}]({{this.link}})

               {{/if}}{{/each}}{{#if settings_url}}Change these emails in [My Notification Settings]({{settings_url}}).{{/if}}""")
            .withVariable("count", "Notification count", "NUMBER", true, null)
            .withVariable("items", "Notifications", "LIST", true, null)
            .withVariable("settings_url", "Settings link", "URL", false, null)
            .withSampleVariables("{\"count\":2,\"items\":[{\"title\":\"Version 2.5 is out\",\"body\":\"Dark mode everywhere.\",\"link\":\"https://example.com/app/widget\","
               + "\"linkLabel\":\"Open\"},{\"title\":\"Export failed\"}],\"settings_url\":\"https://example.com/app/alerts_notification_preference\"}");
         default -> throw (new IllegalArgumentException("Unknown template " + name));
      });
   }
}
