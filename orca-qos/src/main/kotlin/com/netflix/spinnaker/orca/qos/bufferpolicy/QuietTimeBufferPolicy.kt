/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.orca.qos.bufferpolicy

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.qos.BufferAction.BUFFER
import com.netflix.spinnaker.orca.qos.BufferAction.ENQUEUE
import com.netflix.spinnaker.orca.qos.BufferPolicy
import com.netflix.spinnaker.orca.qos.BufferResult
import org.springframework.stereotype.Component
import java.time.Clock

/**
 * There are two types of Quiet times, Global & Application level.
 * Global Quiet Time is when `qos.bufferPolicy.quietTime=true` and the current application is opted in.
 * Application level quiet time can also provide a list of dates ranges on which this policy is enforced.
 * The resulting quiet time is global || application.
 */
@Component
class QuietTimeBufferPolicy(
  clock: Clock,
  private val configService: DynamicConfigService,
  front50Service: Front50Service
) : BufferPolicy, ApplicationConfigurationAwareSupport(clock, front50Service) {
  override fun apply(execution: Execution): BufferResult {
    val globalLevelQuietTime = configService.isEnabled("qos.bufferPolicy.quietTime", false)
    val application = getApplication(execution.application)
    if (globalLevelQuietTime && application.quietTimeEnabled()) {
      // we are in Quiet Time window.
      return BufferResult(
        action = BUFFER,
        force = true,
        reason = "Buffering Execution during Quiet Time window."
      )
    }

    if (application.hasEnteredQuietTimeWindow()) {
      return BufferResult(
        action = BUFFER,
        force = true,
        reason = "${application.name}:${execution.name} is set to be buffered during Quiet Time."
      )
    }

    return BufferResult(
      action = ENQUEUE,
      force = false,
      reason = "Execution is not affected by Quiet Time."
    )
  }
}

