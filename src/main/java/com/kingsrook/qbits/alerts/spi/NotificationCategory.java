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


package com.kingsrook.qbits.alerts.spi;


/*******************************************************************************
 ** What a notification is about (stand-in for core's
 ** com.kingsrook.qqq.backend.core.alerts.NotificationCategory). Users filter
 ** and mute by category; SECURITY can never be muted in-app.
 *******************************************************************************/
public enum NotificationCategory
{
   SYSTEM, JOB, SECURITY, RELEASE, MARKETING, SHARE, ALERT;



   /*******************************************************************************
    ** The category for a name (any case), or the fallback.
    *******************************************************************************/
   public static NotificationCategory fromName(String name, NotificationCategory fallback)
   {
      if(name != null)
      {
         for(NotificationCategory category : values())
         {
            if(category.name().equalsIgnoreCase(name.trim()))
            {
               return (category);
            }
         }
      }
      return (fallback);
   }
}
