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


import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.alerts.spi.AlertSignal;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;


/*******************************************************************************
 ** A CODE monitor's check (design section 5.3): return every condition that
 ** is failing now. The engine raises them (on the monitor's type unless a
 ** signal names another) and resolves this monitor's open alerts whose keys
 ** the check did not return (a full-state diff). Checks run as the system
 ** user, with a timeout; a check that throws marks the monitor CHECK_FAILED.
 *******************************************************************************/
public interface AlertCheckInterface
{
   /***************************************************************************
    ** Every currently-failing condition. Keys not returned are resolved.
    ***************************************************************************/
   List<AlertSignal> check(Context context) throws QException;



   /*******************************************************************************
    ** What a check gets.
    **
    ** @param monitor       the alerts_monitor row
    ** @param alertTypeName the monitor's alert type
    ** @param params        the definition's "params" object (may be empty)
    ** @param now           the evaluation time
    *******************************************************************************/
   record Context(QRecord monitor, String alertTypeName, Map<String, Serializable> params, Instant now)
   {
   }
}
