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

package com.netflix.spinnaker.orca.pipeline.tasks

import com.netflix.spinnaker.orca.time.MutableClock
import spock.lang.Specification
import spock.lang.Subject

import java.time.Duration

import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class WaitUntilTaskSpec extends Specification {
  def clock = new MutableClock()
  @Subject
    task = new WaitUntilTask(clock)

  void "should wait for a configured period"() {
    setup:
    def stage = stage {
      refId = "1"
      type = "epochMillis"
      context["epochMillis"] = 5
    }

    when:
    def result = task.execute(stage)

    then:
    result.status == RUNNING
    result.context.startTime != null
    stage.context.putAll(result.context)

    when:
    clock.incrementBy(Duration.ofSeconds(10))

    and:
    result = task.execute(stage)

    then:
    result.status == SUCCEEDED
    result.context.startTime == null
  }

  void "should return backoff based on epochMillis"() {
    def waitTimeSeconds = 300
    def epochMillis = clock.instant().plusSeconds(waitTimeSeconds)
    def stage = stage {
      refId = "1"
      type = "epochMillis"
      context["epochMillis"] = epochMillis
    }

    when:
    def result = task.execute(stage)

    and:
    stage.context.putAll(result.context)
    def backOff = task.getDynamicBackoffPeriod(stage, null)

    then:
    result.status == RUNNING
    backOff == waitTimeSeconds * 1000
  }

}
