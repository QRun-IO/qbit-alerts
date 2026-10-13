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
 ** How a notification looks to its reader: INFO and SUCCESS are good news,
 ** WARNING and ERROR need a look, CRITICAL makes the badge red (stand-in for
 ** core's com.kingsrook.qqq.backend.core.alerts.NotificationSeverity).
 *******************************************************************************/
public enum NotificationSeverity
{
   INFO, SUCCESS, WARNING, ERROR, CRITICAL;



   /*******************************************************************************
    ** The severity for a name (any case), or the fallback.
    *******************************************************************************/
   public static NotificationSeverity fromName(String name, NotificationSeverity fallback)
   {
      if(name != null)
      {
         for(NotificationSeverity severity : values())
         {
            if(severity.name().equalsIgnoreCase(name.trim()))
            {
               return (severity);
            }
         }
      }
      return (fallback);
   }
}
