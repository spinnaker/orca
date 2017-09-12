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

package com.netflix.spinnaker.q.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.netflix.spinnaker.kork.jedis.EmbeddedRedis
import com.netflix.spinnaker.q.DeadMessageCallback
import com.netflix.spinnaker.q.Message
import com.netflix.spinnaker.q.QueueTest
import com.netflix.spinnaker.q.TestMessage
import com.netflix.spinnaker.q.metrics.MonitorableQueueTest
import com.netflix.spinnaker.spek.shouldEqual
import org.funktionale.partials.invoke
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import java.time.Clock

object RedisQueueTest : QueueTest<RedisQueue>(createQueue(p3 = null), ::shutdownCallback)

object RedisMonitorableQueueTest : MonitorableQueueTest<RedisQueue>(
  createQueue,
  RedisQueue::retry,
  ::shutdownCallback
)

private var redis: EmbeddedRedis? = null

private val createQueue = { clock: Clock,
                            deadLetterCallback: DeadMessageCallback,
                            publisher: ApplicationEventPublisher? ->
  redis = EmbeddedRedis.embed()
  RedisQueue(
    queueName = "test",
    pool = redis!!.pool,
    clock = clock,
    deadMessageHandler = deadLetterCallback,
    publisher = publisher ?: (object : ApplicationEventPublisher {
      override fun publishEvent(event: ApplicationEvent?) {}
      override fun publishEvent(event: Any?) {}
    })
  )
}

private fun shutdownCallback() {
  redis?.destroy()
}

class ConvertToMessageSpec : Spek({
  describe("should support deserializing a nested message") {
    val objectMapper = ObjectMapper().apply {
      registerModule(KotlinModule())
    }

    val message = TestMessage("a")

    it("is not nested") {
      message shouldEqual RedisQueue.convertToMessage(objectMapper.writeValueAsString(message), objectMapper)
    }

    it("is nested") {
      message shouldEqual RedisQueue.convertToMessage(objectMapper.writeValueAsString(Envelope(message)), objectMapper)
    }
  }
})

private data class Envelope(val payload: Message)
