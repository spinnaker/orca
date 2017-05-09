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

import com.netflix.spinnaker.orca.ExecutionStatus.TERMINAL
import com.netflix.spinnaker.orca.q.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component open class DeadMessageHandler {
  private val log = LoggerFactory.getLogger(javaClass)

  fun handle(queue: Queue, message: Message) {
    log.error("Dead message: $message")
    when (message) {
      is TaskLevel -> queue.push(CompleteTask(message, TERMINAL))
      is StageLevel -> queue.push(CompleteStage(message, TERMINAL))
      is ExecutionLevel -> queue.push(CompleteExecution(message))
      else -> log.error("Unhandled message type ${message.javaClass}")
    }
  }
}
