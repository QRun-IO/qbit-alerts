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


import java.time.Duration;
import java.util.List;
import com.kingsrook.qbits.alerts.BaseAlertsTest;
import com.kingsrook.qbits.alerts.model.AlertsAlert;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.Status;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The sink's overflow alert: raised once with a stable key, resolved by the
 ** drain thread once the queue has stayed below half full for a minute, and
 ** raised again by a later overflow.
 *******************************************************************************/
class SinkOverflowTest extends BaseAlertsTest
{

   /*******************************************************************************
    ** Raise once, clear after a sustained drain, raise again.
    *******************************************************************************/
   @Test
   void testOverflowRaisesOnceAndClears() throws Exception
   {
      QbitAlertsSink sink = runtime.getSink();
      sink.checkOverflowCleared();
      assertThat(overflowAlerts()).isEmpty();

      sink.overflowed();
      sink.overflowed();
      List<QRecord> alerts = overflowAlerts();
      assertThat(alerts).hasSize(1);
      QRecord alert = alerts.get(0);
      assertThat(alert.getValueString("dedupKey")).isEqualTo(QbitAlertsSink.OVERFLOW_DEDUP_KEY);
      assertThat(alert.getValueString("severity")).isEqualTo("ERROR");
      assertThat(alert.getValueString("status")).isEqualTo(Status.OPEN.name());
      assertThat(sink.isOverflowRaised()).isTrue();

      //////////////////////////////////////////////////////////////////
      // the drain must be sustained: a new overflow restarts the wait //
      //////////////////////////////////////////////////////////////////
      sink.checkOverflowCleared();
      clock.advance(Duration.ofSeconds(45));
      sink.overflowed();
      sink.checkOverflowCleared();
      clock.advance(Duration.ofSeconds(45));
      sink.checkOverflowCleared();
      assertThat(overflowAlerts().get(0).getValueString("status")).isEqualTo(Status.OPEN.name());

      clock.advance(Duration.ofSeconds(20));
      sink.checkOverflowCleared();
      assertThat(overflowAlerts().get(0).getValueString("status")).isEqualTo(Status.RESOLVED.name());
      assertThat(sink.isOverflowRaised()).isFalse();

      ////////////////////////////////////////////
      // a later overflow raises it again       //
      ////////////////////////////////////////////
      clock.advance(Duration.ofHours(2));
      sink.overflowed();
      assertThat(overflowAlerts()).filteredOn(a -> Status.OPEN.name().equals(a.getValueString("status"))).hasSize(1);
      assertThat(sink.isOverflowRaised()).isTrue();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private List<QRecord> overflowAlerts() throws Exception
   {
      return (where(AlertsAlert.TABLE_NAME, AlertsDb.eq("alertTypeName", QbitAlertsSink.OVERFLOW_TYPE)));
   }
}
