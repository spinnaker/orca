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

import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class HelloTaskSpec extends Specification {

  @Subject
    task = new HelloTask("cameron")

  void "should say hello"() {
    setup:
    def yourName = "cameron"
    def stage = stage {
      refId = "1"
      type = "hello"
      context["yourName"] = yourName
    }

    when:
    def result = task.execute(stage)

    then:
    result.status == SUCCEEDED
    result.context.yourName != null
    result.context.yourName == yourName
    stage.context.putAll(result.context)
  }

}
