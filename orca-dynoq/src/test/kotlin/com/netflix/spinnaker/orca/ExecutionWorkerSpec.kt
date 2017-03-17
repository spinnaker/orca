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

package com.netflix.spinnaker.orca

import com.netflix.appinfo.InstanceInfo.InstanceStatus.OUT_OF_SERVICE
import com.netflix.appinfo.InstanceInfo.InstanceStatus.UP
import com.netflix.discovery.StatusChangeEvent
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.nhaarman.mockito_kotlin.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith

@RunWith(JUnitPlatform::class)
class ExecutionWorkerSpec : Spek({
  describe("execution workers") {

    val commandQ: CommandQueue = mock()
    val eventQ: EventQueue = mock()
    val repository: ExecutionRepository = mock()

    val executionWorker = ExecutionWorker(commandQ, eventQ)

    describe("when disabled in discovery") {
      beforeGroup {
        executionWorker.onApplicationEvent(RemoteStatusChangedEvent(StatusChangeEvent(UP, OUT_OF_SERVICE)))
        executionWorker.pollOnce()
      }

      afterGroup {
        reset(commandQ, eventQ, repository)
      }

      it("does not poll the queue") {
        verify(eventQ, never()).poll()
      }
    }

    describe("when enabled in discovery") {
      val instanceUpEvent = RemoteStatusChangedEvent(StatusChangeEvent(OUT_OF_SERVICE, UP))

      beforeGroup {
        executionWorker.onApplicationEvent(instanceUpEvent)
      }

      describe("no events on the queue") {
        beforeGroup {
          whenever(eventQ.poll())
            .thenReturn(null)

          executionWorker.pollOnce()
        }

        afterGroup {
          reset(commandQ, eventQ, repository)
        }

        it("does nothing") {
          verifyZeroInteractions(commandQ, repository)
        }
      }

      describe("starting an execution") {
        beforeGroup {

        }
      }
    }
  }
})
