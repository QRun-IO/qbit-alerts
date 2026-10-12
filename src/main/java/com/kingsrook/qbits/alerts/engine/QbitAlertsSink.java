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
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import com.kingsrook.qbits.alerts.AlertsRuntime;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.Resolution;
import com.kingsrook.qbits.alerts.spi.AlertResolution;
import com.kingsrook.qbits.alerts.spi.AlertSeverity;
import com.kingsrook.qbits.alerts.spi.AlertSignal;
import com.kingsrook.qbits.alerts.spi.AlertSinkInterface;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** The qbit's sink (design section 3.2): producers' signals go into a bounded
 ** in-memory queue drained by a virtual thread into the alerts tables, so a
 ** producer never waits on the database. If the queue overflows, the signal
 ** is dropped and counted and alerts.sink.overflow is raised once; past 1,000
 ** signals a minute for one type, signals are counted, not stored, and
 ** alerts.producer.flood is raised (section 7.5).
 **
 ** With synchronousSink (tests), signals are processed on the caller's
 ** thread.
 *******************************************************************************/
public class QbitAlertsSink implements AlertSinkInterface
{
   private static final QLogger LOG = QLogger.getLogger(QbitAlertsSink.class);

   public static final String OVERFLOW_TYPE = "alerts.sink.overflow";
   public static final String FLOOD_TYPE    = "alerts.producer.flood";

   static final int QUEUE_CAPACITY      = 10_000;
   static final int FLOOD_PER_MINUTE    = 1_000;

   private final AlertsRuntime          runtime;
   private final BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
   private final AtomicBoolean          started = new AtomicBoolean(false);
   private final AtomicBoolean          overflowRaised = new AtomicBoolean(false);
   private final AtomicLong             dropped = new AtomicLong();
   private final Map<String, AtomicInteger> perMinute = new ConcurrentHashMap<>();
   private volatile long                minute = -1;
   private volatile Thread              drainThread;



   /*******************************************************************************
    ** Constructor
    *******************************************************************************/
   public QbitAlertsSink(AlertsRuntime runtime)
   {
      this.runtime = runtime;
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public void raise(AlertSignal signal)
   {
      if(signal == null || signal.getAlertTypeName() == null)
      {
         return;
      }
      if(signal.getOccurredAt() == null)
      {
         signal.setOccurredAt(runtime.now());
      }
      if(isFlooding(signal.getAlertTypeName()))
      {
         dropped.incrementAndGet();
         return;
      }
      submit(() -> runtime.getEngine().raise(signal));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public void resolve(AlertResolution resolution)
   {
      if(resolution == null || resolution.getAlertTypeName() == null)
      {
         return;
      }
      submit(() -> runtime.getEngine().resolve(resolution.getAlertTypeName(), resolution.getEffectiveDedupKey(), Resolution.PRODUCER, null, resolution.getNote()));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public void heartbeat(String heartbeatKey, Instant at)
   {
      Instant when = at == null ? runtime.now() : at;
      submit(() -> runtime.getEngine().heartbeat(heartbeatKey, when));
   }



   /*******************************************************************************
    ** Process everything queued now, on this thread (tests, shutdown).
    *******************************************************************************/
   public void flush()
   {
      Runnable work;
      while((work = queue.poll()) != null)
      {
         work.run();
      }
   }



   /*******************************************************************************
    ** Stop the drain thread (queued signals are processed first).
    *******************************************************************************/
   public void stop()
   {
      flush();
      Thread thread = drainThread;
      if(thread != null)
      {
         thread.interrupt();
      }
      started.set(false);
   }



   /*******************************************************************************
    ** Signals dropped (overflow or flood) since startup.
    *******************************************************************************/
   public long getDroppedCount()
   {
      return (dropped.get());
   }



   /*******************************************************************************
    ** Queue (or, synchronously, run) one piece of engine work.
    *******************************************************************************/
   private void submit(EngineWork work)
   {
      Runnable runnable = () ->
      {
         try
         {
            work.run();
         }
         catch(Exception e)
         {
            LOG.warn("Error recording an alert signal", e);
         }
      };

      if(Boolean.TRUE.equals(runtime.getConfig().getSynchronousSink()))
      {
         runnable.run();
         return;
      }

      startIfNeeded();
      if(!queue.offer(runnable))
      {
         dropped.incrementAndGet();
         if(overflowRaised.compareAndSet(false, true))
         {
            LOG.warn("The alerts queue is full; dropping signals", logPair("capacity", QUEUE_CAPACITY));
            Thread.ofVirtual().start(() -> safely(() -> runtime.getEngine().raise(new AlertSignal(OVERFLOW_TYPE)
               .withSeverity(AlertSeverity.ERROR)
               .withTitle("Alert signals are being dropped")
               .withSummary("The alerts queue was full, so some signals were not recorded."))));
         }
      }
   }



   /*******************************************************************************
    ** Whether a type is over its per-minute limit (raises the flood alert the
    ** first time it is).
    *******************************************************************************/
   private boolean isFlooding(String alertTypeName)
   {
      long now = runtime.now().getEpochSecond() / 60;
      if(now != minute)
      {
         perMinute.clear();
         minute = now;
      }
      int count = perMinute.computeIfAbsent(alertTypeName, key -> new AtomicInteger()).incrementAndGet();
      if(count == FLOOD_PER_MINUTE + 1 && !FLOOD_TYPE.equals(alertTypeName))
      {
         submit(() -> runtime.getEngine().raise(new AlertSignal(FLOOD_TYPE)
            .withDedupKey(alertTypeName)
            .withSeverity(AlertSeverity.WARNING)
            .withTitle("A producer is flooding alert signals")
            .withSummary("More than " + FLOOD_PER_MINUTE + " signals in a minute for one type; the rest were counted, not stored.")
            .withAttribute("alertType", alertTypeName)));
      }
      return (count > FLOOD_PER_MINUTE);
   }



   /*******************************************************************************
    ** Start the drain thread once.
    *******************************************************************************/
   private void startIfNeeded()
   {
      if(started.compareAndSet(false, true))
      {
         drainThread = Thread.ofVirtual().name("alerts-sink").start(() ->
         {
            while(!Thread.currentThread().isInterrupted())
            {
               try
               {
                  Runnable work = queue.poll(1, TimeUnit.SECONDS);
                  if(work != null)
                  {
                     work.run();
                  }
               }
               catch(InterruptedException e)
               {
                  Thread.currentThread().interrupt();
               }
            }
         });
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static void safely(EngineWork work)
   {
      try
      {
         work.run();
      }
      catch(Exception e)
      {
         LOG.warn("Error raising an alerts meta-alert", e);
      }
   }



   /*******************************************************************************
    ** Engine work that may throw.
    *******************************************************************************/
   @FunctionalInterface
   interface EngineWork
   {
      /***************************************************************************
       **
       ***************************************************************************/
      void run() throws Exception;
   }
}
