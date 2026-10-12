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
 ** STAND-IN for the QQQ core alerts producer SPI (QRun-IO/qqq#1108), which is
 ** not yet in a released QQQ 4.1.0 RC. These classes are copies of
 ** com.kingsrook.qqq.backend.core.alerts (AlertSignal, AlertSeverity,
 ** QAlertTypeMetaData and friends) under this package, so the qbit can be
 ** built and tested against 4.1.0-RC.4 now.
 **
 ** Replace when the RC with qqq#1108 ships: delete this package, change the
 ** imports to com.kingsrook.qqq.backend.core.alerts, and register
 ** QbitAlertsSink in core's AlertsInstanceMetaData (see AlertsQBitProducer).
 ** Until then, producers in a host reach the qbit through
 ** AlertsRuntime.sink() (or its monitors), not through core's QAlerts.
 *******************************************************************************/
package com.kingsrook.qbits.alerts.spi;
