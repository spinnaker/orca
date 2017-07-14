/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.q.trafficshaping.capacity

import com.netflix.spinnaker.orca.events.*
import com.netflix.spinnaker.orca.q.*

/**
 * Simple prioritization strategy that ensures transitional messages
 * are never throttled.
 */
class TransitionalPrioritizationStrategy() : PrioritizationStrategy {

  private val TRANSITIONAL_EVENTS: List<Class<out ExecutionEvent>> = listOf(
    ExecutionComplete::class.java,
    ExecutionStarted::class.java,
    StageComplete::class.java,
    StageStarted::class.java,
    TaskComplete::class.java,
    TaskStarted::class.java
  )

  private val TRANSITIONAL_MESSAGES: List<Class<out Message>> = listOf(
    StartTask::class.java,
    CompleteTask::class.java,
    StartStage::class.java,
    CompleteStage::class.java,
    StartExecution::class.java,
    CompleteExecution::class.java,
    ConfigurationError::class.java
  )

  override fun getPriority(execution: ExecutionEvent): Priority {
    if (execution::class.java in TRANSITIONAL_EVENTS) {
      return Priority.CRITICAL
    }
    return Priority.MEDIUM
  }

  override fun getPriority(message: Message): Priority {
    if (message::class.java in TRANSITIONAL_MESSAGES) {
      return Priority.CRITICAL
    }
    return Priority.MEDIUM
  }
}
