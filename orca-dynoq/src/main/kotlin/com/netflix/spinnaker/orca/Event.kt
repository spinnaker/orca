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

package com.netflix.spinnaker.orca

import com.netflix.spinnaker.orca.pipeline.model.Execution

sealed class Event {
  sealed class TaskResult : Event() {
    /**
     * Task ran successfully.
     */
    data class TaskSucceeded(val executionId: String, val stageId: String, val taskId: String)
      : TaskResult()

    /**
     * Task ran and failed.
     */
    data class TaskFailed(val executionId: String, val stageId: String, val taskId: String)
      : TaskResult()
  }

  sealed class ConfigurationError : Event() {
    /**
     * Execution id was not found in {@link ExecutionRepository}.
     */
    data class InvalidExecutionId(val executionId: String)
      : ConfigurationError()

    /**
     * Stage id was not found in the execution.
     */
    data class InvalidStageId(val executionId: String, val stageId: String)
      : ConfigurationError()

    /**
     * No such task class.
     */
    data class InvalidTaskType(val executionId: String, val stageId: String, val className: String)
      : ConfigurationError()
  }

  data class StageStarting(val executionType: Class<out Execution<*>>, val executionId: String, val stageId: String) : Event()
}
