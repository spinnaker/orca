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

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id.MINIMAL_CLASS
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import java.util.*
import java.util.UUID.randomUUID

/**
 * Messages used internally by the queueing system.
 */
@JsonTypeInfo(use = MINIMAL_CLASS, include = PROPERTY, property = "@class")
sealed class Message {

  abstract val id: UUID

  interface ApplicationAware {
    val application: String
  }

  interface ExecutionLevel : ApplicationAware {
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
    override val id: UUID,
    override val executionType: Class<out Execution<*>>,
    override val executionId: String,
    override val application: String,
    override val stageId: String,
    override val taskId: String
  ) : Message(), TaskLevel {
    constructor(executionType: Class<out Execution<*>>, executionId: String, application: String, stageId: String, taskId: String) :
      this(randomUUID(), executionType, executionId, application, stageId, taskId)

    constructor(source: ExecutionLevel, stageId: String, taskId: String) :
      this(source.executionType, source.executionId, source.application, stageId, taskId)

    constructor(source: StageLevel, taskId: String) :
      this(source, source.stageId, taskId)
  }

  data class TaskComplete(
    override val id: UUID,
    override val executionType: Class<out Execution<*>>,
    override val executionId: String,
    override val application: String,
    override val stageId: String,
    override val taskId: String,
    val status: ExecutionStatus
  ) : Message(), TaskLevel {
    constructor(executionType: Class<out Execution<*>>, executionId: String, application: String, stageId: String, taskId: String, status: ExecutionStatus) :
      this(randomUUID(), executionType, executionId, application, stageId, taskId, status)

    constructor(source: TaskLevel, status: ExecutionStatus) :
      this(source.executionType, source.executionId, source.application, source.stageId, source.taskId, status)
  }

  data class RunTask(
    override val id: UUID,
    override val executionType: Class<out Execution<*>>,
    override val executionId: String,
    override val application: String,
    override val stageId: String,
    override val taskId: String,
    val taskType: Class<out Task>
  ) : Message(), TaskLevel {
    constructor(executionType: Class<out Execution<*>>, executionId: String, application: String, stageId: String, taskId: String, taskType: Class<out Task>) :
      this(randomUUID(), executionType, executionId, application, stageId, taskId, taskType)

    constructor(message: StageLevel, taskId: String, taskType: Class<out Task>) :
      this(message.executionType, message.executionId, message.application, message.stageId, taskId, taskType)

    constructor(source: RunTask) :
      this(source.executionType, source.executionId, source.application, source.stageId, source.taskId, source.taskType)
  }

  data class StageStarting(
    override val id: UUID,
    override val executionType: Class<out Execution<*>>,
    override val executionId: String,
    override val application: String,
    override val stageId: String
  ) : Message(), StageLevel {
    constructor(executionType: Class<out Execution<*>>, executionId: String, application: String, stageId: String) :
      this(randomUUID(), executionType, executionId, application, stageId)

    constructor(source: ExecutionLevel, stageId: String) :
      this(source.executionType, source.executionId, source.application, stageId)

    constructor(source: StageLevel) :
      this(source, source.stageId)
  }

  data class StageComplete(
    override val id: UUID,
    override val executionType: Class<out Execution<*>>,
    override val executionId: String,
    override val application: String,
    override val stageId: String,
    val status: ExecutionStatus
  ) : Message(), StageLevel {
    constructor(executionType: Class<out Execution<*>>, executionId: String, application: String, stageId: String, status: ExecutionStatus) :
      this(randomUUID(), executionType, executionId, application, stageId, status)

    constructor(source: ExecutionLevel, stageId: String, status: ExecutionStatus) :
      this(source.executionType, source.executionId, source.application, stageId, status)

    constructor(source: StageLevel, status: ExecutionStatus) :
      this(source, source.stageId, status)
  }

  data class StageRestarting(
    override val id: UUID,
    override val executionType: Class<out Execution<*>>,
    override val executionId: String,
    override val application: String,
    override val stageId: String
  ) : Message(), StageLevel {
    constructor(executionType: Class<out Execution<*>>, executionId: String, application: String, stageId: String) :
      this(randomUUID(), executionType, executionId, application, stageId)
  }

  data class ExecutionStarting
  constructor(
    override val id: UUID,
    override val executionType: Class<out Execution<*>>,
    override val executionId: String,
    override val application: String
  ) : Message(), ExecutionLevel {
    constructor(executionType: Class<out Execution<*>>, executionId: String, application: String) :
      this(randomUUID(), executionType, executionId, application)
  }

  data class ExecutionComplete(
    override val id: UUID,
    override val executionType: Class<out Execution<*>>,
    override val executionId: String,
    override val application: String,
    val status: ExecutionStatus
  ) : Message(), ExecutionLevel {
    constructor(executionType: Class<out Execution<*>>, executionId: String, application: String, status: ExecutionStatus) :
      this(randomUUID(), executionType, executionId, application, status)

    constructor(source: ExecutionLevel, status: ExecutionStatus) :
      this(source.executionType, source.executionId, source.application, status)
  }

  /**
   * Fatal errors in processing the execution configuration.
   */
  sealed class ConfigurationError : Message(), ExecutionLevel {
    /**
     * Execution id was not found in the [ExecutionRepository].
     */
    data class InvalidExecutionId(
      override val id: UUID,
      override val executionType: Class<out Execution<*>>,
      override val executionId: String,
      override val application: String
    ) : ConfigurationError() {
      constructor(executionType: Class<out Execution<*>>, executionId: String, application: String) :
        this(randomUUID(), executionType, executionId, application)

      constructor(source: ExecutionLevel) :
        this(source.executionType, source.executionId, source.application)
    }

    /**
     * Stage id was not found in the execution.
     */
    data class InvalidStageId(
      override val id: UUID,
      override val executionType: Class<out Execution<*>>,
      override val executionId: String,
      override val application: String,
      override val stageId: String
    ) : ConfigurationError(), StageLevel {
      constructor(executionType: Class<out Execution<*>>, executionId: String, application: String, stageId: String) :
        this(randomUUID(), executionType, executionId, application, stageId)

      constructor(source: StageLevel) :
        this(source.executionType, source.executionId, source.application, source.stageId)
    }

    /**
     * No such [Task] class.
     */
    data class InvalidTaskType(
      override val id: UUID,
      override val executionType: Class<out Execution<*>>,
      override val executionId: String,
      override val application: String,
      override val stageId: String,
      val className: String
    ) : ConfigurationError(), StageLevel {
      constructor(executionType: Class<out Execution<*>>, executionId: String, application: String, stageId: String, className: String) :
        this(randomUUID(), executionType, executionId, application, stageId, className)

      constructor(source: StageLevel, className: String) :
        this(source.executionType, source.executionId, source.application, source.stageId, className)
    }

    data class NoDownstreamTasks(
      override val id: UUID,
      override val executionType: Class<out Execution<*>>,
      override val executionId: String,
      override val application: String,
      override val stageId: String,
      override val taskId: String
    ) : ConfigurationError(), TaskLevel {
      constructor(executionType: Class<out Execution<*>>, executionId: String, application: String, stageId: String, taskId: String) :
        this(randomUUID(), executionType, executionId, application, stageId, taskId)

      constructor(source: TaskLevel) :
        this(source.executionType, source.executionId, source.application, source.stageId, source.taskId)
    }
  }
}
