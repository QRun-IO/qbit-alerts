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

package com.kingsrook.qbits.alerts.engine;


import java.time.Instant;
import com.kingsrook.qbits.alerts.AlertsRuntime;
import com.kingsrook.qbits.alerts.spi.AlertResolution;
import com.kingsrook.qbits.alerts.spi.AlertSignal;
import com.kingsrook.qbits.alerts.spi.AlertSinkInterface;
import com.kingsrook.qqq.backend.core.context.QContext;


/*******************************************************************************
 ** The sink registered in the alerts meta-data: a stateless, loadable
 ** (no-argument) front for the installed runtime's QbitAlertsSink. With no
 ** runtime in the context the calls are dropped (the core SPI's caller has
 ** already logged them).
 *******************************************************************************/
public class AlertsSinkAdapter implements AlertSinkInterface
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public void raise(AlertSignal signal)
   {
      AlertsRuntime runtime = AlertsRuntime.find(QContext.getQInstance());
      if(runtime != null)
      {
         runtime.sink().raise(signal);
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public void resolve(AlertResolution resolution)
   {
      AlertsRuntime runtime = AlertsRuntime.find(QContext.getQInstance());
      if(runtime != null)
      {
         runtime.sink().resolve(resolution);
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public void heartbeat(String heartbeatKey, Instant at)
   {
      AlertsRuntime runtime = AlertsRuntime.find(QContext.getQInstance());
      if(runtime != null)
      {
         runtime.sink().heartbeat(heartbeatKey, at);
      }
   }
}
