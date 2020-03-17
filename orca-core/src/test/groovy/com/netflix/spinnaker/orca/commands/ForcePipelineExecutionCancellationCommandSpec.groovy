/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.orca.commands

import com.netflix.spinnaker.orca.pipeline.model.TaskExecutionImpl
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import spock.lang.Specification

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.*
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class ForcePipelineExecutionCancellationCommandSpec extends Specification {

  ExecutionRepository executionRepository = Mock()
  Clock clock = Clock.fixed(Instant.EPOCH, ZoneId.systemDefault())

  def subject = new ForceExecutionCancellationCommand(executionRepository, clock)

  def "cancels a pipeline and all children stages"() {
    given:
    def execution = pipeline {
      status = RUNNING
      stage {
        id = "s1"
        status = SUCCEEDED
      }
      stage {
        id = "s2"
        status = RUNNING
        tasks.add(new TaskExecutionImpl(id: "t1", status: RUNNING))
      }
      stage {
        id = "s3"
        status = NOT_STARTED
      }
    }

    when:
    subject.forceCancel(PIPELINE, execution.id, "rz")

    then:
    noExceptionThrown()
    1 * executionRepository.retrieve(PIPELINE, _) >> execution
    1 * executionRepository.store(execution)
    0 * _
    execution.status == CANCELED
    execution.canceledBy == "rz"
    execution.cancellationReason == "Force canceled by admin"
    execution.endTime == clock.instant().toEpochMilli()
    execution.stageById("s1").status == SUCCEEDED
    execution.stageById("s2").status == CANCELED
    execution.stageById("s2").endTime == clock.instant().toEpochMilli()
    execution.stageById("s2").tasks*.status == [CANCELED]
    execution.stageById("s3").status == NOT_STARTED
  }

  def "does nothing with a canceled pipeline"() {
    given:
    def execution = pipeline {
      status = SUCCEEDED
      stage {
        id = "s1"
        status = SUCCEEDED
      }
    }

    when:
    subject.forceCancel(PIPELINE, execution.id, "rz")

    then:
    noExceptionThrown()
    1 * executionRepository.retrieve(PIPELINE, _) >> execution
    0 * _

  }
}
