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

package com.netflix.spinnaker.orca.q

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import java.util.*

sealed class Event : Message {

  override val id: UUID = UUID.randomUUID()

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

  data class TaskStarting(
    override val executionType: Class<out Execution<*>>,
    override val executionId: String,
    override val stageId: String,
    override val taskId: String
  ) : Event(), TaskLevel {
    constructor(source: ExecutionLevel, stageId: String, taskId: String) :
      this(source.executionType, source.executionId, stageId, taskId)

    constructor(source: StageLevel, taskId: String) :
      this(source, source.stageId, taskId)
  }

  data class TaskComplete(
    override val executionType: Class<out Execution<*>>,
    override val executionId: String,
    override val stageId: String,
    override val taskId: String,
    val status: ExecutionStatus
  ) : Event(), TaskLevel {
    constructor(source: TaskLevel, status: ExecutionStatus) :
      this(source.executionType, source.executionId, source.stageId, source.taskId, status)
  }

  data class StageStarting(
    override val executionType: Class<out Execution<*>>,
    override val executionId: String,
    override val stageId: String
  ) : Event(), StageLevel {
    constructor(source: ExecutionLevel, stageId: String) :
      this(source.executionType, source.executionId, stageId)
  }

  data class StageComplete(
    override val executionType: Class<out Execution<*>>,
    override val executionId: String,
    override val stageId: String,
    val status: ExecutionStatus
  ) : Event(), StageLevel {
    constructor(source: ExecutionLevel, stageId: String, status: ExecutionStatus) :
      this(source.executionType, source.executionId, stageId, status)

    constructor(source: StageLevel, status: ExecutionStatus) :
      this(source, source.stageId, status)
  }

  data class ExecutionStarting(
    override val executionType: Class<out Execution<*>>,
    override val executionId: String
  ) : Event(), ExecutionLevel

  data class ExecutionComplete(
    override val executionType: Class<out Execution<*>>,
    override val executionId: String,
    val status: ExecutionStatus
  ) : Event(), ExecutionLevel {
    constructor(source: ExecutionLevel, status: ExecutionStatus) :
      this(source.executionType, source.executionId, status)
  }

  /**
   * Fatal errors in processing the execution configuration.
   */
  sealed class ConfigurationError : Event(), ExecutionLevel {
    /**
     * Execution id was not found in the [ExecutionRepository].
     */
    data class InvalidExecutionId(
      override val executionType: Class<out Execution<*>>,
      override val executionId: String
    ) : ConfigurationError() {
      constructor(source: ExecutionLevel)
        : this(source.executionType, source.executionId)
    }

    /**
     * Stage id was not found in the execution.
     */
    data class InvalidStageId(
      override val executionType: Class<out Execution<*>>,
      override val executionId: String,
      override val stageId: String
    ) : ConfigurationError(), StageLevel {
      constructor(source: StageLevel)
        : this(source.executionType, source.executionId, source.stageId)
    }

    /**
     * No such [Task] class.
     */
    data class InvalidTaskType(
      override val executionType: Class<out Execution<*>>,
      override val executionId: String,
      override val stageId: String,
      val className: String
    ) : ConfigurationError(), StageLevel {
      constructor(source: StageLevel, className: String)
        : this(source.executionType, source.executionId, source.stageId, className)
    }
  }

}
