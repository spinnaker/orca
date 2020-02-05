/*
 * Copyright 2020 Netflix, Inc.
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

import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.peering.PeeringAgent
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
/**
 * TODO(mvulfson): this needs to support multiple (arbitrary number of) beans / peers defined in config
 * TODO(mvulfson): prob also worth creating ConfigurationProperties class for the props
 */
class PeeringConfiguration {
  @Bean
  @ConditionalOnExpression("\${pollers.peering.enabled:false}")
  fun peeringAgent(
    jooq: DSLContext,
    clusterLock: NotificationClusterLock,
    @Value("\${pollers.peering.pool-name}") peeredPoolName: String,
    @Value("\${pollers.peering.id}") peeredId: String,
    @Value("\${pollers.peering.interval-ms:3600000}") pollIntervalMs: Long
  ): PeeringAgent {
    return PeeringAgent(jooq, peeredPoolName, peeredId, pollIntervalMs, clusterLock)
  }
}
