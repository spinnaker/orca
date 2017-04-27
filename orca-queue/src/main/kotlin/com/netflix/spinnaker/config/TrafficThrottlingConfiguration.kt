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
package com.netflix.spinnaker.config

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.q.NoopTrafficShapingInterceptor
import com.netflix.spinnaker.orca.q.TrafficShapingInterceptor
import com.netflix.spinnaker.orca.q.interceptors.ApplicationThrottlingQueueInterceptor
import com.netflix.spinnaker.orca.q.ratelimit.NoopRateLimitBackend
import com.netflix.spinnaker.orca.q.ratelimit.RateLimitBackend
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty("queue.trafficShaping.enabled")
open class TrafficThrottlingConfiguration {

  @Bean @ConditionalOnMissingBean(TrafficShapingInterceptor::class)
  open fun noopTrafficShapingInterceptor() = NoopTrafficShapingInterceptor()

  @Bean
  @ConditionalOnMissingBean(RateLimitBackend::class)
  @ConditionalOnProperty("queue.trafficShaping.applicationThrottling.enabled")
  open fun noopRateLimitBackend(): RateLimitBackend = NoopRateLimitBackend()

  @Bean @ConditionalOnProperty("queue.trafficShaping.applicationThrottling.enabled")
  open fun applicationThrottlingQueueInterceptor(rateLimitBackend: RateLimitBackend, registry: Registry): TrafficShapingInterceptor {
    return ApplicationThrottlingQueueInterceptor(rateLimitBackend, registry)
  }
}
