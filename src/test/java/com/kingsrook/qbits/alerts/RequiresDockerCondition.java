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


import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;


/*******************************************************************************
 ** Runs a PostgreSQL (Testcontainers) test when Docker is available; skips it
 ** on a workstation without Docker, and fails when CI is set but Docker is not.
 *******************************************************************************/
public class RequiresDockerCondition implements ExecutionCondition
{

   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context)
   {
      boolean available;
      try
      {
         available = DockerClientFactory.instance().isDockerAvailable();
      }
      catch(Exception | LinkageError e)
      {
         available = false;
      }
      if(available)
      {
         return (ConditionEvaluationResult.enabled("Docker is available"));
      }
      if(System.getenv("CI") != null)
      {
         throw new IllegalStateException("Docker is required when CI is set");
      }
      return (ConditionEvaluationResult.disabled("Docker is unavailable; the PostgreSQL schema tests need it"));
   }
}
