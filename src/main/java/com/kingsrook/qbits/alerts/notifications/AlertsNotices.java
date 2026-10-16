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


package com.kingsrook.qbits.alerts.notifications;


import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import com.kingsrook.qqq.backend.core.alerts.AlertLink;
import com.kingsrook.qqq.backend.core.alerts.NotificationAudience;
import com.kingsrook.qqq.backend.core.alerts.NotificationCategory;
import com.kingsrook.qqq.backend.core.alerts.NotificationSeverity;
import com.kingsrook.qqq.backend.core.alerts.NotificationSignal;
import com.kingsrook.qqq.backend.core.alerts.QAlerts;


/*******************************************************************************
 ** Ready-made notifications for the automatic sources hosts wire up
 ** (docs/NOTIFICATIONS.md 6.1): security notices, shares and assignments,
 ** and release announcements. Each one is a NotificationSignal sent through
 ** core QAlerts (queued; it never throws into the caller). With no Alerts
 ** qbit installed core uses its logging sink.
 **
 ** Core has no sign-in or API key events yet, so a host calls the security
 ** notices from its authentication customizer or API key screens. There is
 ** no notice for creating an API key: the person who created it just did it.
 *******************************************************************************/
public final class AlertsNotices
{
   private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneOffset.UTC);



   /*******************************************************************************
    ** Helpers only.
    *******************************************************************************/
   private AlertsNotices()
   {
   }



   /*******************************************************************************
    ** A sign-in from a new device or place (where: e.g. "Firefox on Windows,
    ** near Columbus, OH"). Call it only for a device or address not seen for
    ** this user recently: the host has the sign-in history, this qbit does not.
    *******************************************************************************/
   public static void newSignIn(String userId, String where)
   {
      send(new NotificationSignal("New sign-in to your account")
         .withBody("A sign-in from " + where + ". If this wasn't you, change your password now.")
         .withSeverity(NotificationSeverity.WARNING), userId, null);
   }



   /*******************************************************************************
    ** An API key expires soon (one notification per key, refreshed on repeat).
    *******************************************************************************/
   public static void apiKeyExpiring(String userId, String keyLabel, Instant expiresAt)
   {
      send(new NotificationSignal("API key expires " + DATE.format(expiresAt) + ": " + keyLabel)
         .withBody("Create a new key and update the clients that use this one before it expires.")
         .withSeverity(NotificationSeverity.WARNING), userId, "apikey-expiring:" + keyLabel);
   }



   /*******************************************************************************
    ** The user's permissions or roles changed (what: e.g. "You were added to
    ** the Editors role").
    *******************************************************************************/
   public static void permissionChanged(String userId, String what)
   {
      send(new NotificationSignal("Your access changed")
         .withBody(what + ". If you didn't expect this, contact an administrator.")
         .withSeverity(NotificationSeverity.INFO), userId, null);
   }



   /*******************************************************************************
    ** Something was shared with or assigned to a user (a notebook, a record
    ** lock, an approval waiting for them).
    *******************************************************************************/
   public static void shared(String userId, String title, AlertLink link, String sourceName)
   {
      QAlerts.notify(new NotificationSignal(title).withCategory(NotificationCategory.SHARE).withSourceName(sourceName).withLink(link)
         .withAudience(NotificationAudience.user(userId)));
   }



   /*******************************************************************************
    ** A release announcement to every user ("Version 2.4 is out").
    *******************************************************************************/
   public static void release(String version, String notesMarkdown, AlertLink link)
   {
      QAlerts.notify(new NotificationSignal("Version " + version + " is out").withBody(notesMarkdown).withSeverity(NotificationSeverity.SUCCESS)
         .withCategory(NotificationCategory.RELEASE).withSourceName("release").withSourceKey("release:" + version).withLink(link)
         .withAudience(NotificationAudience.allUsers()));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void send(NotificationSignal signal, String userId, String sourceKey)
   {
      if(userId == null)
      {
         return;
      }
      QAlerts.notify(signal.withCategory(NotificationCategory.SECURITY).withSourceName("security").withSourceKey(sourceKey)
         .withAudience(NotificationAudience.user(userId)));
   }
}
