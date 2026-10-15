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


import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.logging.QCollectingLogger;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.session.QSystemUserSession;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Background threads (the alerts sink, schedulers) run engine work through
 ** asSystem with no QContext of their own; the system session must be built
 ** from the runtime instance, not from the (empty) context.
 *******************************************************************************/
class AlertsSystemSessionTest extends BaseAlertsTest
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @Test
   void asSystemWithoutContextUsesTheRuntimeInstance() throws Exception
   {
      QContext.clear();
      QCollectingLogger collectingLogger = QLogger.activateCollectingLoggerForClass(QSystemUserSession.class);
      try
      {
         Boolean systemUser = runtime.asSystem(() -> QContext.getQInstance() != null && AlertsSecurity.isSystemUser());
         assertThat(systemUser).isTrue();
         assertThat(collectingLogger.getCollectedMessages())
            .noneMatch(m -> m.getMessage().contains("QInstance was not supplied"));
      }
      finally
      {
         QLogger.deactivateCollectingLoggerForClass(QSystemUserSession.class);
      }
   }
}
