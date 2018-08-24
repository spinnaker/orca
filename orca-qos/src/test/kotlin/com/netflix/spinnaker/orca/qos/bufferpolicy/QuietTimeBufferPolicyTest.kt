/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.qos.bufferpolicy

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.model.Application
import com.netflix.spinnaker.orca.qos.BufferAction
import com.netflix.spinnaker.orca.qos.BufferResult
import com.netflix.spinnaker.time.fixedClock
import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.jetbrains.spek.api.lifecycle.CachingMode
import org.jetbrains.spek.subject.SubjectSpek
import java.time.LocalDateTime

class QuietTimeBufferPolicyTest : SubjectSpek<QuietTimeBufferPolicy>({
  val clock = fixedClock()
  val front50Service: Front50Service = mock()
  val configService: DynamicConfigService = mock()

  subject(CachingMode.GROUP) {
    QuietTimeBufferPolicy(clock, configService, front50Service)
  }

  fun resetMocks() = reset(front50Service, configService)

  describe("a global Quiet Time Buffer Policy") {
    given("an execution submitted during Quiet Time") {
      val app = Application().apply {
        name = "testApp"
      }

      val execution = pipeline {
        application = app.name
      }

      beforeGroup {
        whenever(front50Service.get(execution.application)) doReturn app
        whenever(configService.isEnabled("qos.bufferPolicy.quietTime", false)) doReturn true
      }

      afterGroup(::resetMocks)

      on("Quiet Time enforced on execution because app is opted in") {
        app.set("quietTime", mapOf("enabled" to true))
        val result: BufferResult = subject.apply(execution)
        it("buffers execution") {
          verify(configService).isEnabled(any(), any())
          assertThat(result.action).isEqualTo(BufferAction.BUFFER)
        }
      }

      on("Quiet Time not enforced on execution because app is opted out") {
        app.set("quietTime", mapOf("enabled" to false))
        val result: BufferResult = subject.apply(execution)
        it("buffers execution") {
          assertThat(result.action).isEqualTo(BufferAction.ENQUEUE)
        }
      }
    }
  }

  describe("an application level Quiet Time Buffer Policy") {
    given("an execution submitted during app level Quiet Time") {
      val app = Application().apply {
        name = "testApp"
        set("quietTime", mapOf(
          "enabled" to true,
          "dates" to listOf(
            mapOf(
              "startDateTime" to LocalDateTime.now().minusSeconds(10).toString(),
              "endDateTime" to LocalDateTime.now().plusSeconds(10).toString()))
          )
        )
      }

      val execution = pipeline {
        application = app.name
      }

      beforeGroup {
        whenever(front50Service.get(execution.application)) doReturn app
      }

      afterGroup(::resetMocks)

      on("Quiet Time enforced on execution because app is opted in") {
        val result: BufferResult = subject.apply(execution)
        it("buffers execution") {
          verify(configService).isEnabled(any(), any())
          assertThat(result.action).isEqualTo(BufferAction.BUFFER)
        }
      }
    }
  }
})
