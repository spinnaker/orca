/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.q.handler

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.ExecutionStatus.*
import com.netflix.spinnaker.orca.ext.shouldContinueOnFailure
import com.netflix.spinnaker.orca.ext.shouldFailPipeline
import com.netflix.spinnaker.orca.ext.syntheticStages
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.Task

fun Stage.determineStatus(): ExecutionStatus {
  val syntheticStatuses = syntheticStages().map(Stage::getStatus)
  val taskStatuses = tasks.map(Task::getStatus)
  val allStatuses = syntheticStatuses + taskStatuses
  return when {
    allStatuses.isEmpty() -> NOT_STARTED
    allStatuses.contains(TERMINAL) -> TERMINAL
    allStatuses.contains(STOPPED) -> STOPPED
    allStatuses.contains(CANCELED) -> CANCELED
    allStatuses.contains(FAILED_CONTINUE) -> FAILED_CONTINUE
    allStatuses.all { it == SUCCEEDED } -> SUCCEEDED
    else -> TERMINAL
  }
}

fun Stage.failureStatus(default: ExecutionStatus = TERMINAL) =
  when {
    shouldContinueOnFailure() -> FAILED_CONTINUE
    shouldFailPipeline() -> default
    else -> STOPPED
  }
