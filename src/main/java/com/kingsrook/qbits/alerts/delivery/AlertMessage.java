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

package com.kingsrook.qbits.alerts.delivery;


import java.util.List;


/*******************************************************************************
 ** A rendered, redacted message for one channel and recipient: one alert, or
 ** a batch of several that arrived together ("3 new alerts").
 **
 ** @param subject   one line (email subject, chat headline)
 ** @param text      plain-text body
 ** @param html      simple HTML body (escaped)
 ** @param severity  the highest severity in the message
 ** @param kind      RAISED, RESOLVED, REMINDER, ESCALATED, DIGEST or TEST
 ** @param alertIds  the alerts in the message
 ** @param link      a link to the alert (or the Alerts list) in the app
 *******************************************************************************/
public record AlertMessage(String subject, String text, String html, String severity, String kind, List<Integer> alertIds, String link)
{
}
