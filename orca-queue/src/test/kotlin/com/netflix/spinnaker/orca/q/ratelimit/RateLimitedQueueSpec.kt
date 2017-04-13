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
import com.netflix.spinnaker.orca.q.Message.RunTask
import com.netflix.spinnaker.orca.q.Queue
import com.netflix.spinnaker.orca.q.QueueCallback
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith

@RunWith(JUnitPlatform::class)
class RateLimitedQueueSpec : Spek({
  describe("rate limited queue") {
    val backingQueue: Queue = mock()
    val backingQueueAck: () -> Unit = mock()
    val backend: RateLimitBackend = mock()
    val registry: Registry = mock {
      on { createId(any<String>()) }.doReturn(mock<Id>())
      on { counter(any<Id>()) }.doReturn(mock<Counter>())
    }

    var subject: RateLimitedQueue? = null

    fun resetMocks() = reset(backingQueue, backingQueueAck, backend)

    describe("when rate limiter disabled") {
      beforeGroup {
        whenever(backend.getRate(any<String>())).thenReturn(Rate(false, 0, 0))
        whenever(backingQueue.poll(any())).then {
          @Suppress("UNCHECKED_CAST")
          val callback = it.arguments.first() as QueueCallback
          callback.invoke(RunTask(Pipeline::class.java, "id0", "app", "id1", "id2", WaitTask::class.java), backingQueueAck)
        }

        subject = RateLimitedQueue(backingQueue, backend, RateLimitConfiguration(capacity = 1), registry)
      }

      afterGroup(::resetMocks)

      action("poll is called repeatedly") {
        repeat(10) {
          subject!!.poll { _, _ -> }
        }
      }

      it("does not throttle") {
        verify(backingQueue, never()).push(any(), any())
      }
    }

    describe("when rate limiter enabled") {
      beforeGroup {
        whenever(backend.getRate(any<String>())).thenReturn(Rate(true, 0, 0))
        whenever(backingQueue.poll(any())).then {
          @Suppress("UNCHECKED_CAST")
          val callback = it.arguments.first() as QueueCallback
          callback.invoke(RunTask(Pipeline::class.java, "id0", "app", "id1", "id2", WaitTask::class.java), backingQueueAck)
        }

        subject = RateLimitedQueue(backingQueue, backend, RateLimitConfiguration(capacity = 1), registry)
      }

      afterGroup(::resetMocks)

      action("poll is called") {
        subject!!.poll { _, ack -> ack.invoke() }
      }

      it("does throttle messages") {
        verify(backingQueue).push(any(), any())
        verify(backingQueueAck).invoke()
      }
    }
  }
})
