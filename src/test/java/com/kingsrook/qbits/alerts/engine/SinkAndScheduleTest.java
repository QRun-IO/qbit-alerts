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


import java.util.List;
import com.kingsrook.qbits.alerts.AlertsQBitConfig;
import com.kingsrook.qbits.alerts.BaseAlertsTest;
import com.kingsrook.qbits.alerts.metadata.AlertsProcessesMetaDataProducer;
import com.kingsrook.qbits.alerts.model.AlertsAlert;
import com.kingsrook.qbits.alerts.model.AlertsHeartbeat;
import com.kingsrook.qbits.alerts.processes.AlertsScheduledSteps;
import com.kingsrook.qqq.backend.core.actions.processes.RunProcessAction;
import com.kingsrook.qqq.backend.core.alerts.AlertResolution;
import com.kingsrook.qqq.backend.core.alerts.AlertSeverity;
import com.kingsrook.qqq.backend.core.alerts.AlertSignal;
import com.kingsrook.qqq.backend.core.alerts.AlertsInstanceMetaData;
import com.kingsrook.qqq.backend.core.alerts.QAlertTypeMetaData;
import com.kingsrook.qqq.backend.core.alerts.QAlerts;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessOutput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The asynchronous sink (queue, flush, flood protection), the registered
 ** sink adapter, and the two hidden scheduled processes.
 *******************************************************************************/
class SinkAndScheduleTest extends BaseAlertsTest
{

   /*******************************************************************************
    ** A host using only core reaches the qbit through instance metadata and
    ** the loadable adapter, including the asynchronous queue and persistence.
    *******************************************************************************/
   @Test
   void testCoreProducerReachesInstalledSink() throws Exception
   {
      AlertsInstanceMetaData metadata = AlertsInstanceMetaData.of(qInstance);
      assertThat(metadata.getSink().getName()).isEqualTo(AlertsSinkAdapter.class.getName());
      assertThat(qInstance.getSupplementalMetaData("alertsQBitStandIn")).isNull();
      metadata.withAlertType(new QAlertTypeMetaData().withName("host.backup.failed").withDefaultSeverity(AlertSeverity.ERROR));
      long failures = QAlerts.getProducerFailureCount();

      QAlerts.raise(new AlertSignal("host.backup.failed").withDedupKey("nightly").withTitle("Nightly backup failed"));
      QAlerts.heartbeat("host.backup.nightly");
      runtime.getSink().stop();

      List<QRecord> alerts = all(AlertsAlert.TABLE_NAME);
      assertThat(alerts).hasSize(1);
      assertThat(alerts.get(0).getValueString("title")).isEqualTo("Nightly backup failed");
      assertThat(alerts.get(0).getValueString("severity")).isEqualTo("ERROR");
      assertThat(alerts.get(0).getValueString("status")).isEqualTo("OPEN");
      assertThat(all(AlertsHeartbeat.TABLE_NAME)).hasSize(1);

      QAlerts.resolve("host.backup.failed", "nightly", "Retry succeeded");
      runtime.getSink().stop();
      assertThat(all(AlertsAlert.TABLE_NAME).get(0).getValueString("status")).isEqualTo("RESOLVED");
      assertThat(QAlerts.getProducerFailureCount()).isEqualTo(failures);
   }



   /*******************************************************************************
    ** Process signals on the background queue in these tests.
    *******************************************************************************/
   @Override
   protected void customizeConfig(AlertsQBitConfig config)
   {
      config.withSynchronousSink(false);
   }



   /*******************************************************************************
    ** stop() returns only after every signal submitted before it is recorded,
    ** including one the drain thread already took off the queue (regression:
    ** stop() used to flush an empty queue and return while the drain thread
    ** was still writing, so a resolve could be lost).
    *******************************************************************************/
   @Test
   void testStopWaitsForInFlightSignals() throws Exception
   {
      AlertsSinkAdapter adapter = new AlertsSinkAdapter();
      for(int i = 0; i < 25; i++)
      {
         String type = "test.stop.thing" + i;
         adapter.raise(new AlertSignal(type).withTitle("Stop " + i));
         runtime.getSink().stop();
         adapter.resolve(new AlertResolution(type, null, "done"));
         runtime.getSink().stop();
      }

      List<QRecord> alerts = all(AlertsAlert.TABLE_NAME);
      assertThat(alerts).hasSize(25);
      assertThat(alerts).allSatisfy(alert -> assertThat(alert.getValueString("status")).isEqualTo("RESOLVED"));
   }



   /*******************************************************************************
    ** Signals queue and are drained (by the thread, or by flush).
    *******************************************************************************/
   @Test
   void testQueueAndAdapter() throws Exception
   {
      AlertsSinkAdapter adapter = new AlertsSinkAdapter();
      adapter.raise(new AlertSignal("test.queue.thing").withTitle("Queued"));
      adapter.heartbeat("queue.beat", null);
      runtime.getSink().flush();
      for(int i = 0; i < 50 && all(AlertsAlert.TABLE_NAME).isEmpty(); i++)
      {
         Thread.sleep(50);
      }
      assertThat(all(AlertsAlert.TABLE_NAME)).hasSize(1);

      adapter.resolve(new AlertResolution("test.queue.thing", null, "done"));
      runtime.getSink().stop();
      List<QRecord> alerts = all(AlertsAlert.TABLE_NAME);
      assertThat(alerts.get(0).getValueString("status")).isEqualTo("RESOLVED");
      assertThat(all(AlertsHeartbeat.TABLE_NAME)).hasSize(1);
   }



   /*******************************************************************************
    ** Past 1,000 signals a minute for a type, the rest are counted, not stored.
    *******************************************************************************/
   @Test
   void testFlood()
   {
      for(int i = 0; i < 1005; i++)
      {
         runtime.getSink().raise(new AlertSignal("test.flood.thing").withDedupKey("same"));
      }
      assertThat(runtime.getSink().getDroppedCount()).isEqualTo(5);
      runtime.getSink().stop();
   }



   /*******************************************************************************
    ** The scheduled processes run their steps.
    *******************************************************************************/
   @Test
   void testScheduledProcesses() throws Exception
   {
      RunProcessOutput evaluated = new RunProcessAction().execute(new RunProcessInput().withProcessName(AlertsProcessesMetaDataProducer.EVALUATE_MONITORS));
      assertThat(evaluated.getValues().get(AlertsScheduledSteps.OUTPUT_COUNT)).isEqualTo(0);
      RunProcessOutput delivered = new RunProcessAction().execute(new RunProcessInput().withProcessName(AlertsProcessesMetaDataProducer.DELIVER_NOTIFICATIONS));
      assertThat(delivered.getValues().get(AlertsScheduledSteps.OUTPUT_COUNT)).isEqualTo(0);
   }
}
