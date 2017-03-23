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

import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.q.Event.StageLevel
import com.netflix.spinnaker.orca.q.Event.TaskLevel
import java.util.*

sealed class Command : Message {
  data class RunTask(
    override val executionType: Class<out Execution<*>>,
    override val executionId: String,
    override val stageId: String,
    override val taskId: String,
    val taskType: Class<out Task>
  ) : Command(), TaskLevel {
    override val id: UUID = UUID.randomUUID()

    constructor(event: StageLevel, taskId: String, taskType: Class<out Task>) :
      this(event.executionType, event.executionId, event.stageId, taskId, taskType)
  }
}
