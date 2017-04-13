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

package com.netflix.spinnaker.orca.q.event

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.q.*
import org.springframework.context.ApplicationEvent
import java.time.Instant

/**
 * Events that external clients can listen for to receive updates on progress of
 * an execution. These are not used internally by the queueing system to
 * organize work but are published for external monitoring.
 */
sealed class ExecutionEvent(source: Any) : ApplicationEvent(source) {

  val timestamp: Instant
    get() = Instant.ofEpochMilli(super.getTimestamp())

  /**
   * Converts an event to the execution log entry format
   */
  abstract fun toLogEntry(): ExecutionLogEntry

  class ExecutionStarted(
    source: Any,
    val executionType: Class<out Execution<*>>,
    val executionId: String
  ) : ExecutionEvent(source) {
    constructor(source: Any, message: StartExecution) :
      this(source, message.executionType, message.executionId)

    override fun toLogEntry() = ExecutionLogEntry(
      executionId,
      timestamp,
      javaClass.simpleName,
      emptyMap()
    )
  }

  /**
   * An execution completed (either completed successfully or stopped due to
   * failure/cancellation/whatever).
   */
  class ExecutionComplete(
    source: Any,
    val executionType: Class<out Execution<*>>,
    val executionId: String,
    val status: ExecutionStatus
  ) : ExecutionEvent(source) {
    /**
     * Copy constructor to create a pub-sub event from a queue message.
     */
    constructor(source: Any, message: CompleteExecution) :
      this(source, message.executionType, message.executionId, message.status)

    override fun toLogEntry() = ExecutionLogEntry(
      executionId,
      timestamp,
      javaClass.simpleName,
      hashMapOf("status" to status.name)
    )
  }

  class StageStarted(
    source: Any,
    val executionType: Class<out Execution<*>>,
    val executionId: String,
    val stageId: String
  ) : ExecutionEvent(source) {
    constructor(source: Any, message: StartStage) :
      this(source, message.executionType, message.executionId, message.stageId)

    override fun toLogEntry() = ExecutionLogEntry(
      executionId,
      timestamp,
      javaClass.simpleName,
      emptyMap()
    )
  }

  class StageComplete(
    source: Any,
    val executionType: Class<out Execution<*>>,
    val executionId: String,
    val stageId: String,
    val status: ExecutionStatus
  ) : ExecutionEvent(source) {
    constructor(source: Any, message: CompleteStage) :
      this(source, message.executionType, message.executionId, message.stageId, message.status)

    override fun toLogEntry() = ExecutionLogEntry(
      executionId,
      timestamp,
      javaClass.simpleName,
      hashMapOf("status" to status.name)
    )
  }

  class TaskStarted(
    source: Any,
    val executionType: Class<out Execution<*>>,
    val executionId: String,
    val stageId: String,
    val taskId: String
  ) : ExecutionEvent(source) {
    constructor(source: Any, message: StartTask) :
      this(source, message.executionType, message.executionId, message.stageId, message.taskId)

    override fun toLogEntry() = ExecutionLogEntry(
      executionId,
      timestamp,
      javaClass.simpleName,
      emptyMap()
    )
  }

  class TaskComplete(
    source: Any,
    val executionType: Class<out Execution<*>>,
    val executionId: String,
    val stageId: String,
    val taskId: String,
    val status: ExecutionStatus
  ) : ExecutionEvent(source) {
    constructor(source: Any, message: CompleteTask) :
      this(source, message.executionType, message.executionId, message.stageId, message.taskId, message.status)

    override fun toLogEntry() = ExecutionLogEntry(
      executionId,
      timestamp,
      javaClass.simpleName,
      hashMapOf("status" to status.name)
    )
  }
}
