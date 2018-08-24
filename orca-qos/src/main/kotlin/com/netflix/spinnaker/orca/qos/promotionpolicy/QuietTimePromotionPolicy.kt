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
package com.netflix.spinnaker.orca.qos.promotionpolicy

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.qos.PromotionPolicy
import com.netflix.spinnaker.orca.qos.PromotionResult
import com.netflix.spinnaker.orca.qos.bufferpolicy.ApplicationConfigurationAwareSupport
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.*

/**
 * This policy enforces Quiet Time period promotions of executions.
 * Promotes all candidates if Quiet Time is over
 * Promotes application level Quiet Time
 * The resulting quiet time is global || application.
 */
@Component
class QuietTimePromotionPolicy(
  clock: Clock,
  front50Service: Front50Service,
  private val configService: DynamicConfigService
) : PromotionPolicy, ApplicationConfigurationAwareSupport(clock, front50Service) {
  override fun apply(candidates: List<Execution>): PromotionResult {
    if (configService.isEnabled("qos.bufferPolicy.quietTime", false)) {
      candidates.filter {
        getApplication(it.application).hasExitedQuietTimeWindow()
      }.let {
        return if (!it.isEmpty()) PromotionResult(
          candidates = it,
          finalized = false,
          reason = "Quiet Time window is enabled. Only promoting executions not affected by Quiet Time."
        ) else PromotionResult(
          candidates = Collections.emptyList(),
          finalized = false,
          reason = "Quiet Time window is enabled."
        )
      }
    }

    return PromotionResult(
      candidates = candidates,
      finalized = false,
      reason = "Quiet Time window is over."
    )
  }
}
