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
package com.netflix.spinnaker.config

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.pipeline.ExecutionLauncher
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.qos.DefaultExecutionPromoter
import com.netflix.spinnaker.orca.qos.PromotionPolicy
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@Configuration
@ComponentScan("com.netflix.spinnaker.orca.qos")
@EnableConfigurationProperties(QosConfigurationProperties::class)
class QosConfiguration {
  @Bean
  @ConditionalOnExpression("\${pollers.qos.enabled:false}")
  fun executionPromoterAgent(
    executionLauncher: ExecutionLauncher,
    executionRepository: ExecutionRepository,
    policies: List<PromotionPolicy>,
    registry: Registry,
    qosConfigurationProperties: QosConfigurationProperties,
    clusterLock: NotificationClusterLock
  ): DefaultExecutionPromoter {
    return DefaultExecutionPromoter(executionLauncher, executionRepository, policies, registry, qosConfigurationProperties.promoteIntervalMs, clusterLock)
  }
}
