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


import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;


/*******************************************************************************
 ** STAND-IN(core=QRun-IO/qqq#1125, removeBy=4.1.0-RC.6)
 **
 ** Who a notification is for, resolved to users when it is sent (stand-in for
 ** core's com.kingsrook.qqq.backend.core.alerts.NotificationAudience).
 *******************************************************************************/
public class NotificationAudience implements Serializable
{
   /*******************************************************************************
    ** STAND-IN(core=QRun-IO/qqq#1125, removeBy=4.1.0-RC.6)
    **
    ** The kinds of audience.
    *******************************************************************************/
   public enum Type
   {
      ALL_USERS, ROLES, PERMISSION, USERS
   }



   private final Type         type;
   private final List<String> values;



   /*******************************************************************************
    ** Constructor (blank values are dropped; values are trimmed).
    *******************************************************************************/
   public NotificationAudience(Type type, List<String> values)
   {
      this.type = type == null ? Type.USERS : type;
      this.values = values == null ? List.of() : values.stream().filter(Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
   }



   /*******************************************************************************
    ** Every user.
    *******************************************************************************/
   public static NotificationAudience allUsers()
   {
      return (new NotificationAudience(Type.ALL_USERS, null));
   }



   /*******************************************************************************
    ** Users with any of these roles or groups.
    *******************************************************************************/
   public static NotificationAudience roles(String... roles)
   {
      return (new NotificationAudience(Type.ROLES, roles == null ? null : Arrays.asList(roles)));
   }



   /*******************************************************************************
    ** Users whose session holds this permission.
    *******************************************************************************/
   public static NotificationAudience permission(String permission)
   {
      return (new NotificationAudience(Type.PERMISSION, permission == null ? null : List.of(permission)));
   }



   /*******************************************************************************
    ** These users.
    *******************************************************************************/
   public static NotificationAudience users(String... userIds)
   {
      return (new NotificationAudience(Type.USERS, userIds == null ? null : Arrays.asList(userIds)));
   }



   /*******************************************************************************
    ** One user.
    *******************************************************************************/
   public static NotificationAudience user(String userId)
   {
      return (users(userId));
   }



   /*******************************************************************************
    ** From the stored form: a type name and a comma- or line-separated value.
    ** An unknown type is null.
    *******************************************************************************/
   public static NotificationAudience parse(String type, String value)
   {
      for(Type candidate : Type.values())
      {
         if(type != null && candidate.name().equalsIgnoreCase(type.trim()))
         {
            List<String> values = new ArrayList<>();
            if(value != null)
            {
               values.addAll(Arrays.asList(value.split("[,\\n]")));
            }
            return (new NotificationAudience(candidate, values));
         }
      }
      return (null);
   }



   /*******************************************************************************
    ** The stored form of the values (null when there are none).
    *******************************************************************************/
   public String valuesAsString()
   {
      return (values.isEmpty() ? null : String.join(",", values));
   }



   /*******************************************************************************
    ** Getter for type
    *******************************************************************************/
   public Type getType()
   {
      return (this.type);
   }



   /*******************************************************************************
    ** Getter for values
    *******************************************************************************/
   public List<String> getValues()
   {
      return (this.values);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public boolean equals(Object o)
   {
      return (o instanceof NotificationAudience other && type == other.type && values.equals(other.values));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public int hashCode()
   {
      return (Objects.hash(type, values));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public String toString()
   {
      return (type + (values.isEmpty() ? "" : values.toString()));
   }
}
