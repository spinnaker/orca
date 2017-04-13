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
package com.netflix.spinnaker.orca.q.ratelimit

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.RateLimitConfiguration
import com.netflix.spinnaker.orca.q.Message
import com.netflix.spinnaker.orca.q.Queue
import com.netflix.spinnaker.orca.q.push
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.time.Duration
import java.util.concurrent.TimeUnit

class RateLimitedQueue(
  val queue: Queue,
  val backend: RateLimitBackend,
  val rateLimitConfiguration: RateLimitConfiguration,
  val registry: Registry
) : Queue, Closeable {

  override val ackTimeout = queue.ackTimeout

  private val log: Logger = LoggerFactory.getLogger(javaClass)

  private val throttledMessagesId = registry.createId("orca.nu.ratelimit.throttledMessages")

  override fun poll(): Message? {
    val message = queue.poll()
    when (message) {
      null -> return null
      !is Message.ApplicationAware -> return message
      else -> {
        val rate = backend.getRate(message.application)
        if (rate.limiting) {
          queue.push(message, rateLimitConfiguration.delayMs, TimeUnit.MILLISECONDS)
          queue.ack(message)

          registry.counter(throttledMessagesId).increment()
          log.info("throttling message (application: ${message.application}, capacity: ${rate.capacity}, windowMs: ${rate.windowMs}, delayMs: ${rateLimitConfiguration.delayMs}, message: ${message.id})")

          return null
        }
        return message
      }
    }
  }

  override fun push(message: Message) {
    queue.push(message)
  }

  override fun push(message: Message, delay: Duration) {
    queue.push(message, delay)
  }

  override fun ack(message: Message) {
    queue.ack(message)
  }

  override fun close() {
    if (queue is Closeable) {
      queue.close()
    }
  }
}
