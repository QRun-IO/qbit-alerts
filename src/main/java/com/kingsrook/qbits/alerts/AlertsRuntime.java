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

package com.kingsrook.qbits.alerts;


import java.time.Clock;
import java.time.Instant;
import com.kingsrook.qbits.alerts.delivery.AlertChannels;
import com.kingsrook.qbits.alerts.delivery.DeliveryWorker;
import com.kingsrook.qbits.alerts.engine.AlertRouter;
import com.kingsrook.qbits.alerts.engine.AlertTypeSync;
import com.kingsrook.qbits.alerts.engine.AlertsEngine;
import com.kingsrook.qbits.alerts.engine.MonitorEvaluator;
import com.kingsrook.qbits.alerts.engine.QbitAlertsSink;
import com.kingsrook.qqq.backend.core.context.CapturedContext;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.exceptions.QNotFoundException;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaData;


/*******************************************************************************
 ** One installed Alerts qbit at run time: its config and instance, the clock,
 ** and the engine parts (sink, engine, router, delivery worker, monitor
 ** evaluator, type sync). Built by the producer, kept on the config.
 *******************************************************************************/
public class AlertsRuntime
{
   private final AlertsQBitConfig config;
   private final QInstance        qInstance;

   private Clock clock = Clock.systemUTC();

   private final AlertTypeSync    typeSync;
   private final AlertsEngine     engine;
   private final AlertRouter      router;
   private final AlertChannels    channels;
   private final DeliveryWorker   deliveryWorker;
   private final MonitorEvaluator evaluator;
   private final QbitAlertsSink   sink;



   /*******************************************************************************
    ** Something to run as the system user.
    *******************************************************************************/
   @FunctionalInterface
   public interface SystemWork<R>
   {
      /***************************************************************************
       **
       ***************************************************************************/
      R run() throws QException;
   }



   /*******************************************************************************
    ** Constructor
    *******************************************************************************/
   public AlertsRuntime(AlertsQBitConfig config, QInstance qInstance)
   {
      this.config = config;
      this.qInstance = qInstance;
      this.typeSync = new AlertTypeSync(this);
      this.router = new AlertRouter(this);
      this.engine = new AlertsEngine(this);
      this.channels = new AlertChannels(this);
      this.deliveryWorker = new DeliveryWorker(this);
      this.evaluator = new MonitorEvaluator(this);
      this.sink = new QbitAlertsSink(this);
   }



   /*******************************************************************************
    ** The runtime of the Alerts qbit in the current context's instance.
    *******************************************************************************/
   public static AlertsRuntime get() throws QNotFoundException
   {
      return (get(QContext.getQInstance()));
   }



   /*******************************************************************************
    ** The runtime of the Alerts qbit installed in an instance.
    *******************************************************************************/
   public static AlertsRuntime get(QInstance qInstance) throws QNotFoundException
   {
      AlertsRuntime runtime = find(qInstance);
      if(runtime == null)
      {
         throw (new QNotFoundException("The Alerts qbit is not installed in this instance."));
      }
      return (runtime);
   }



   /*******************************************************************************
    ** The runtime, or null when the qbit is not installed.
    *******************************************************************************/
   public static AlertsRuntime find(QInstance qInstance)
   {
      if(qInstance == null || qInstance.getQBits() == null)
      {
         return (null);
      }
      for(QBitMetaData qBit : qInstance.getQBits().values())
      {
         if(qBit.getConfig() instanceof AlertsQBitConfig alertsConfig && alertsConfig.getRuntime() != null)
         {
            return (alertsConfig.getRuntime());
         }
      }
      return (null);
   }



   /*******************************************************************************
    ** Run work as the system user (the engine sees everything; what people are
    ** shown and sent is filtered separately).
    *******************************************************************************/
   public <R> R asSystem(SystemWork<R> work) throws QException
   {
      Object[] result = new Object[1];
      QContext.withTemporaryContext(new CapturedContext(qInstance, AlertsSecurity.systemSession()), () ->
      {
         result[0] = work.run();
      });
      @SuppressWarnings("unchecked")
      R typed = (R) result[0];
      return (typed);
   }



   /*******************************************************************************
    ** Now, by the runtime's clock.
    *******************************************************************************/
   public Instant now()
   {
      return (clock.instant());
   }



   /*******************************************************************************
    ** Getter for config
    *******************************************************************************/
   public AlertsQBitConfig getConfig()
   {
      return (this.config);
   }



   /*******************************************************************************
    ** Getter for qInstance
    *******************************************************************************/
   public QInstance getQInstance()
   {
      return (this.qInstance);
   }



   /*******************************************************************************
    ** Getter for clock
    *******************************************************************************/
   public Clock getClock()
   {
      return (this.clock);
   }



   /*******************************************************************************
    ** Setter for clock (tests)
    *******************************************************************************/
   public void setClock(Clock clock)
   {
      this.clock = clock;
   }



   /*******************************************************************************
    ** Getter for typeSync
    *******************************************************************************/
   public AlertTypeSync getTypeSync()
   {
      return (this.typeSync);
   }



   /*******************************************************************************
    ** Getter for engine
    *******************************************************************************/
   public AlertsEngine getEngine()
   {
      return (this.engine);
   }



   /*******************************************************************************
    ** Getter for router
    *******************************************************************************/
   public AlertRouter getRouter()
   {
      return (this.router);
   }



   /*******************************************************************************
    ** Getter for channels
    *******************************************************************************/
   public AlertChannels getChannels()
   {
      return (this.channels);
   }



   /*******************************************************************************
    ** Getter for deliveryWorker
    *******************************************************************************/
   public DeliveryWorker getDeliveryWorker()
   {
      return (this.deliveryWorker);
   }



   /*******************************************************************************
    ** Getter for evaluator
    *******************************************************************************/
   public MonitorEvaluator getEvaluator()
   {
      return (this.evaluator);
   }



   /*******************************************************************************
    ** The sink producers send signals to (until the core SPI ships, hosts call
    ** it directly: AlertsRuntime.get().sink().raise(...)).
    *******************************************************************************/
   public QbitAlertsSink sink()
   {
      return (this.sink);
   }
}
