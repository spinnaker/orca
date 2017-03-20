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

  interface ExecutionLevel {
    val executionType: Class<out Execution<*>>
    val executionId: String
  }

  interface StageLevel : ExecutionLevel {
    val stageId: String
  }

  interface TaskLevel : StageLevel {
    val taskId: String
  }

  sealed class TaskResult : Event() {
    /**
     * Task ran successfully.
     */
    data class TaskSucceeded(
      override val executionType: Class<out Execution<*>>,
      override val executionId: String,
      override val stageId: String,
      override val taskId: String
    ) : TaskResult(), TaskLevel

    /**
     * Task ran and failed.
     */
    data class TaskFailed(
      override val executionType: Class<out Execution<*>>,
      override val executionId: String,
      override val stageId: String,
      override val taskId: String
    ) : TaskResult(), TaskLevel
  }

  sealed class ConfigurationError : Event() {
    /**
     * Execution id was not found in {@link ExecutionRepository}.
     */
    data class InvalidExecutionId(
      override val executionType: Class<out Execution<*>>,
      override val executionId: String
    ) : ConfigurationError(), ExecutionLevel

    /**
     * Stage id was not found in the execution.
     */
    data class InvalidStageId(
      override val executionType: Class<out Execution<*>>,
      override val executionId: String,
      override val stageId: String
    ) : ConfigurationError(), StageLevel

    /**
     * No such task class.
     */
    data class InvalidTaskType(
      override val executionType: Class<out Execution<*>>,
      override val executionId: String,
      override val stageId: String,
      val className: String
    ) : ConfigurationError(), StageLevel
  }

  data class StageStarting(
    override val executionType: Class<out Execution<*>>,
    override val executionId: String,
    override val stageId: String
  ) : Event(), StageLevel

  data class StageComplete(
    override val executionType: Class<out Execution<*>>,
    override val executionId: String,
    override val stageId: String
  ) : Event(), StageLevel

  data class ExecutionComplete(
    override val executionType: Class<out Execution<*>>,
    override val executionId: String
  ) : Event(), ExecutionLevel
}
