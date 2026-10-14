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


/*******************************************************************************
 ** STAND-IN(core=QRun-IO/qqq#1125, removeBy=4.1.0-RC.6)
 **
 ** Only NotificationSignal, NotificationAudience, NotificationCategory,
 ** NotificationSeverity and AlertLink remain here. Core 4.1.0-RC.5 ships the
 ** alert SPI, but not the notification API planned in QRun-IO/qqq#1125.
 **
 ** Released qbit-easypost-tracking 0.1.x calls AlertsRuntime.sink().notify
 ** with these types. AlertLink remains because NotificationSignal's public
 ** signatures use it. Keep their binary signatures until producers migrate
 ** to core QAlerts.notify in RC.6; no local sink interface is needed because
 ** AlertsRuntime.sink() still returns QbitAlertsSink.
 **
 ** Alerts use com.kingsrook.qqq.backend.core.alerts. AlertsQBitProducer
 ** registers AlertsSinkAdapter through core AlertsInstanceMetaData, so host
 ** QAlerts.raise/resolve/heartbeat calls reach this qbit's runtime.
 *******************************************************************************/
package com.kingsrook.qbits.alerts.spi;
