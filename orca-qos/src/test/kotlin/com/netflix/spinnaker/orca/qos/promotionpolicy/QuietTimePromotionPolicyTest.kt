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

package com.netflix.spinnaker.orca.qos.promotionpolicy

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.model.Application
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

class QuietTimePromotionPolicyTest : SubjectSpek<QuietTimePromotionPolicy>({
  val clock = fixedClock()
  val front50Service: Front50Service = mock()
  val configService: DynamicConfigService = mock()

  subject(CachingMode.GROUP) {
    QuietTimePromotionPolicy(clock, front50Service, configService)
  }

  fun resetMocks() = reset(front50Service, configService)

  describe("a Quiet Time Promotion Policy", {
    given("Executions submitted during Quiet Time") {
      val executions = listOf(
        pipeline {
          application = "testApp"
        },
        pipeline {
          application = "testApp"
        }
      )

      beforeGroup {
        whenever(configService.isEnabled("qos.bufferPolicy.quietTime", false)) doReturn true
      }

      afterGroup(::resetMocks)

      on("Quiet Time in effect") {
        it("zero execution gets promoted") {
          subject.apply(executions).let { result ->
            assertThat(result.candidates.size).isEqualTo(0)
          }
        }
      }
    }

    given("Executions submitted outside of Quiet Time window") {
      val app = Application().apply {
        name = "testApp"
        set("quietTime", mapOf(
          "enabled" to true,
          "dates" to listOf(
            mapOf(
              "startDateTime" to LocalDateTime.now().minusMinutes(10).toString(),
              "endDateTime" to LocalDateTime.now().plusMinutes(5).toString())))
        )
      }

      val app2 = Application().apply { name = "testApp2" }

      val executions = listOf(
        pipeline {
          application = app.name
        },
        pipeline {
          application = app2.name
        }
      )

      beforeGroup {
        whenever(configService.isEnabled("qos.bufferPolicy.quietTime", false)) doReturn false
        whenever(front50Service.get(app.name)) doReturn app
        whenever(front50Service.get(app2.name)) doReturn app2
      }

      afterGroup(::resetMocks)

      on("Quiet Time window is over") {
        it("promotes executions of applications not currently in Quiet Time window") {
          subject.apply(executions).let { result ->
            assertThat(result.candidates.size).isEqualTo(1)
          }
        }
      }
    }
  })
})

