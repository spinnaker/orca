/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.interlink

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.interlink.events.*
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION
import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.PIPELINE

class InterlinkSpec extends Specification {
  ObjectMapper objectMapper = new ObjectMapper()

  @Unroll
  def "can parse execution event type #event.getEventType()"() {
    when:
    def payload = objectMapper.writeValueAsString(event)

    then:
    def mappedEvent = objectMapper.readValue(payload, InterlinkEvent.class)
    mappedEvent == event

    where:
    event << [
        new CancelInterlinkEvent(ORCHESTRATION, "execId", "user", "reason"),
        new PauseInterlinkEvent(PIPELINE, "execId", "user"),
        new ResumeInterlinkEvent(PIPELINE, "execId", "user", false).withPartition("partition"),
        new DeleteInterlinkEvent(ORCHESTRATION, "execId").withPartition("partition")
    ]
  }
}
