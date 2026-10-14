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


import java.util.Set;
import com.kingsrook.qqq.backend.core.exceptions.QException;


/*******************************************************************************
 ** Host SPI for ROLE and IDP_GROUP team membership. Implementations use the
 ** host's public APIs and return user IDs, never display names. Return an empty
 ** set for an unknown source or value. Results must be bounded by the configured
 ** maxAudienceSize; routing still checks every user's alert visibility.
 *******************************************************************************/
@FunctionalInterface
public interface AlertAudienceResolver
{
   /*******************************************************************************
    ** Resolve the team's memberSource and memberSourceValue to user IDs.
    *******************************************************************************/
   Set<String> resolve(String sourceType, String sourceValue) throws QException;
}
