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

package com.kingsrook.qbits.alerts.model;


import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.List;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.data.QRecordEntity;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Every entity round-trips through its setters, fluent setters, QRecord and
 ** the QRecord constructor.
 *******************************************************************************/
class EntitiesTest
{
   static final List<Class<? extends QRecordEntity>> ENTITIES = List.of(AlertsAlert.class, AlertsChannel.class, AlertsEvent.class, AlertsGroup.class, AlertsHeartbeat.class,
      AlertsInboxItem.class, AlertsMonitor.class, AlertsMute.class, AlertsNotification.class, AlertsRecordRef.class, AlertsSubscription.class, AlertsTeam.class,
      AlertsTeamMember.class, AlertsType.class, AlertsUserPreference.class);



   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void testRoundTrip() throws Exception
   {
      for(Class<? extends QRecordEntity> entityClass : ENTITIES)
      {
         QRecordEntity viaSetters = entityClass.getConstructor().newInstance();
         QRecordEntity viaWithers = entityClass.getConstructor().newInstance();
         int           n          = 0;
         for(Field field : entityClass.getDeclaredFields())
         {
            if(Modifier.isStatic(field.getModifiers()))
            {
               continue;
            }
            Object value = switch(field.getType().getSimpleName())
            {
               case "Integer" -> ++n;
               case "Boolean" -> Boolean.TRUE;
               case "Instant" -> Instant.parse("2026-10-12T12:00:00Z").plusSeconds(++n);
               default -> field.getName() + "-" + (++n);
            };
            String suffix = Character.toUpperCase(field.getName().charAt(0)) + field.getName().substring(1);
            entityClass.getMethod("set" + suffix, field.getType()).invoke(viaSetters, value);
            Object returned = entityClass.getMethod("with" + suffix, field.getType()).invoke(viaWithers, value);
            assertThat(returned).isSameAs(viaWithers);
            Method getter = entityClass.getMethod("get" + suffix);
            assertThat(getter.invoke(viaSetters)).as(entityClass.getSimpleName() + "." + field.getName()).isEqualTo(value);
         }

         QRecord       record = viaSetters.toQRecord();
         QRecordEntity copy   = entityClass.getConstructor(QRecord.class).newInstance(record);
         assertThat(copy.toQRecord().getValues()).as(entityClass.getSimpleName()).isEqualTo(record.getValues());
         assertThat(entityClass.getField("TABLE_NAME").get(null)).asString().startsWith("alerts_");
      }
   }
}
