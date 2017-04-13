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

import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.RateLimitConfiguration
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.tasks.WaitTask
import com.netflix.spinnaker.orca.q.Message
import com.netflix.spinnaker.orca.q.Queue
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith
import java.time.Duration

@RunWith(JUnitPlatform::class)
class RateLimitedQueueSpec : Spek({
  describe("rate limited queue") {
    val backingQueue: Queue = mock()
    val backend: RateLimitBackend = mock()
    val registry: Registry = mock {
      on { createId(any<String>()) }.doReturn(mock<Id>())
      on { counter(any<Id>()) }.doReturn(mock<Counter>())
    }

    var subject: RateLimitedQueue? = null

    fun resetMocks() = reset(backingQueue, backend)

    describe("when rate limiter disabled") {
      beforeGroup {
        whenever(backend.getRate(any<String>())).thenReturn(Rate(false, 0, 0))
        whenever(backingQueue.poll()).thenReturn(Message.RunTask(Pipeline::class.java, "id0", "app", "id1", "id2", WaitTask::class.java))

        subject = RateLimitedQueue(backingQueue, backend, RateLimitConfiguration(capacity = 1), registry)
      }

      afterGroup(::resetMocks)

      action("poll is called repeatedly") {
        (0..10).forEach { subject!!.poll() }
      }

      it("does not throttle") {
        verify(backingQueue, times(0)).push(any<Message>(), any<Duration>())
        verify(backingQueue, times(0)).ack(any<Message>())
      }
    }

    describe("when rate limiter enabled") {
      beforeGroup {
        whenever(backend.getRate(any<String>())).thenReturn(Rate(true, 0, 0))
        whenever(backingQueue.poll()).thenReturn(Message.RunTask(Pipeline::class.java, "id0", "app", "id1", "id2", WaitTask::class.java))

        subject = RateLimitedQueue(backingQueue, backend, RateLimitConfiguration(capacity = 1), registry)
      }

      afterGroup(::resetMocks)

      action("poll is called") {
        subject!!.poll()
      }

      it("does throttle messages") {
        verify(backingQueue, times(1)).push(any<Message>(), any<Duration>())
        verify(backingQueue, times(1)).ack(any<Message>())
      }
    }
  }
})
