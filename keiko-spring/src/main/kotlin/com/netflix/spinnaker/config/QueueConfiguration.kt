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

package com.netflix.spinnaker.config

import com.netflix.spinnaker.q.MessageHandler
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.q.QueueExecutor
import com.netflix.spinnaker.q.QueueProcessor
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.time.Clock

@Configuration
@EnableConfigurationProperties(QueueProperties::class)
@ComponentScan(basePackages = arrayOf("com.netflix.spinnaker.q"))
@EnableScheduling
open class QueueConfiguration {

  @Bean
  @ConditionalOnMissingBean(Clock::class)
  open fun systemClock(): Clock = Clock.systemDefaultZone()

  @Bean
  open fun messageHandlerPool(queueProperties: QueueProperties) =
    ThreadPoolTaskExecutor().apply {
      corePoolSize = queueProperties.handlerCorePoolSize
      maxPoolSize = queueProperties.handlerMaxPoolSize
      setQueueCapacity(0)
    }

  @Bean
  open fun queueProcessor(
    queue: Queue,
    queueExecutor: QueueExecutor,
    handlers: Collection<MessageHandler<*>>
  ) = QueueProcessor(queue, queueExecutor, handlers)
}
