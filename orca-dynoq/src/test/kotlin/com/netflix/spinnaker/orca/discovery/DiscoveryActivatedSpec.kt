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

package com.netflix.spinnaker.orca.discovery

import com.netflix.appinfo.InstanceInfo.InstanceStatus
import com.netflix.appinfo.InstanceInfo.InstanceStatus.OUT_OF_SERVICE
import com.netflix.appinfo.InstanceInfo.InstanceStatus.UP
import com.netflix.discovery.StatusChangeEvent
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.verify
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith

@RunWith(JUnitPlatform::class)
class DiscoveryActivatedSpec : Spek({

  describe("a discovery-activated poller") {

    val target: Function0<Unit> = mock()
    val subject = object : DiscoveryActivated() {
      fun invoke() = ifEnabled(target::invoke)
    }

    describe("when disabled") {
      beforeGroup {
        subject.invoke()
      }

      afterGroup {
        reset(target)
      }

      it("does nothing") {
        verify(target, never()).invoke()
      }
    }

    describe("when enabled") {
      beforeGroup {
        subject.triggerEvent(OUT_OF_SERVICE, UP)
      }

      describe("when invoked") {
        beforeGroup {
          subject.invoke()
        }

        afterGroup {
          reset(target)
        }

        it("does something") {
          verify(target).invoke()
        }
      }

      describe("when instance goes out of service") {
        beforeGroup {
          subject.invoke()
          subject.triggerEvent(UP, OUT_OF_SERVICE)
          subject.invoke()
        }

        afterGroup {
          reset(target)
        }

        it("stops doing anything") {
          verify(target).invoke()
        }
      }
    }
  }
})

private fun DiscoveryActivated.triggerEvent(from: InstanceStatus, to: InstanceStatus) =
  onApplicationEvent(event(from, to))

private fun event(from: InstanceStatus, to: InstanceStatus) =
  RemoteStatusChangedEvent(StatusChangeEvent(from, to))
