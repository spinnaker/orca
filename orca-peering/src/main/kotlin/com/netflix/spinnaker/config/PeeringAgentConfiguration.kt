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

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.peering.PeeringAgent
import com.netflix.spinnaker.orca.peering.MySqlRawAccess
import com.netflix.spinnaker.orca.peering.SqlRawAccess
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.Executors

@Configuration
/**
 * TODO(mvulfson): this needs to support multiple (arbitrary number of) beans / peers defined in config
 * TODO(mvulfson): prob also worth creating ConfigurationProperties class for the props
 */
class PeeringAgentConfiguration {
  @Bean
  @ConditionalOnExpression("\${pollers.peering.enabled:false}")
  fun peeringAgent(
    jooq: DSLContext,
    clusterLock: NotificationClusterLock,
    dynamicConfigService: DynamicConfigService,
    registry: Registry,
    @Value("\${pollers.peering.pool-name}") peeredPoolName: String,
    @Value("\${pollers.peering.id}") peeredId: String,
    @Value("\${pollers.peering.interval-ms:5000}") pollIntervalMs: Long,
    @Value("\${pollers.peering.thread-count:30}") threadCount: Int,
    @Value("\${pollers.peering.chunk-size:100}") chunkSize: Int,
    @Value("\${pollers.peering.clock-drift-ms:5000}") clockDriftMs: Long
  ): PeeringAgent {
    val executor = Executors.newCachedThreadPool(
      ThreadFactoryBuilder()
        .setNameFormat(PeeringAgent::javaClass.name + "-%d")
        .build())

    val srcDB: SqlRawAccess
    val destDB: SqlRawAccess

    when (jooq.dialect()) {
      SQLDialect.MYSQL -> {
        srcDB = MySqlRawAccess(jooq, peeredPoolName, chunkSize)
        destDB = MySqlRawAccess(jooq, "default", chunkSize)
      }
      else -> throw UnsupportedOperationException("Peering only supported on MySQL right now")
    }

    return PeeringAgent(peeredId, pollIntervalMs, executor, threadCount, chunkSize, clockDriftMs, srcDB, destDB, dynamicConfigService, registry, clusterLock)
  }
}
