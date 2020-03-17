/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.netflix.spinnaker.orca.api.pipeline.SyntheticStageOwner
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class ApplySourceServerGroupSnapshotTaskSpec extends Specification {
  def oortHelper = Mock(OortHelper)
  def executionRepository = Mock(ExecutionRepository)

  @Subject
  def task = new ApplySourceServerGroupCapacityTask(
    oortHelper: oortHelper,
    executionRepository: executionRepository,
    objectMapper: new ObjectMapper()
  )

  void "should support ancestor deploy stages w/ a custom strategy"() {
    given:
    def parentPipeline = pipeline {
      stage {
        id = "stage-1"
        context = [
          strategy                         : "custom",
          sourceServerGroupCapacitySnapshot: [
            min    : 0,
            desired: 5,
            max    : 10
          ]
        ]
      }
      stage {
        type = "pipeline"
        parentStageId = "stage-1"
        context = [executionId: "execution-id"]
      }
    }

    def childPipeline = pipeline {
      stage {
        context = [type: "doSomething"]
      }
      stage {
        context = [type: "createServerGroup"]
      }
      stage {
        context = [
          type                  : "createServerGroup",
          "deploy.server.groups": [
            "us-west-1": ["asg-v001"]
          ]
        ]
      }
    }

    when:
    def ancestorDeployStage = ApplySourceServerGroupCapacityTask.getAncestorDeployStage(
      executionRepository, parentPipeline.stages[-1]
    )

    then:
    1 * executionRepository.retrieve(PIPELINE, "execution-id") >> childPipeline

    // should match the first childPipeline of type 'createServerGroup' w/ 'deploy.server.groups'
    ancestorDeployStage == childPipeline.stages[2]
  }

  @Unroll
  void "should support ancestor deploy stages w/ a #strategy strategy"() {
    given:
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", [
      strategy                         : strategy,
      sourceServerGroupCapacitySnapshot: [
        min    : 0,
        desired: 5,
        max    : 10
      ]
    ])

    expect:
    ApplySourceServerGroupCapacityTask.getAncestorDeployStage(null, stage) == stage

    where:
    strategy   || _
    "redblack" || _
    null       || _
  }

  @Unroll
  void "should construct resizeServerGroup context with source `min` + target `desired` and `max` capacity"() {
    given:
    def sourceServerGroupCapacitySnapshot = [
      min    : originalMinCapacity,
      desired: 10,
      max    : 20
    ]

    def context = [
      credentials    : "test",
      region         : "us-east-1",
      asgName        : "application-stack-v001",
      serverGroupName: "application-stack-v001",
      cloudProvider  : cloudProvider
    ]

    task = new ApplySourceServerGroupCapacityTask() {
      @Override
      ApplySourceServerGroupCapacityTask.TargetServerGroupContext getTargetServerGroupContext(StageExecution stage) {
        return new ApplySourceServerGroupCapacityTask.TargetServerGroupContext(
          context: context,
          sourceServerGroupCapacitySnapshot: sourceServerGroupCapacitySnapshot
        )
      }
    }
    task.oortHelper = oortHelper

    and:
    def targetServerGroup = new TargetServerGroup(
      name: "application-stack-v001",
      capacity: [
        min    : 5,
        desired: 5,
        max    : 10
      ]
    )

    when:
    def result = task.convert(null)

    then:
    1 * oortHelper.getTargetServerGroup(
      "test",
      "application-stack-v001",
      "us-east-1",
      cloudProvider
    ) >> Optional.of(targetServerGroup)

    result.cloudProvider == cloudProvider
    result.credentials == "test"
    result.asgName == "application-stack-v001"
    result.serverGroupName == "application-stack-v001"
    result.region == "us-east-1"
    result.capacity == (cloudProvider == "aws"
      ? [ min: expectedMinCapacity ]
      : [ min: expectedMinCapacity, desired: 5, max : 10 ])

    where:
    cloudProvider || originalMinCapacity || expectedMinCapacity
    "aws"         || 0                   || 0            // min(currentMin, snapshotMin) == 0
    "aws"         || 10                  || 5            // min(currentMin, snapshotMin) == 5
    "notaws"      || 0                   || 0            // min(currentMin, snapshotMin) == 0
    "notaws"      || 10                  || 5            // min(currentMin, snapshotMin) == 5
  }

  void "should get TargetServerGroupContext with explicitly provided coordinates"() {
    given:
    StageExecutionImpl currentStage
    StageExecutionImpl siblingStage
    def pipeline = pipeline {
      stage {
        id = "parentStageId"
      }
      currentStage = stage {
        parentStageId = "parentStageId"
        context = [
          target: [
            region         : "us-west-2",
            serverGroupName: "asg-v001",
            account        : "test",
            cloudProvider  : "aws"
          ]
        ]
      }
      siblingStage = stage {
        parentStageId = "parentStageId"
        syntheticStageOwner = SyntheticStageOwner.STAGE_AFTER
        context = [
          sourceServerGroupCapacitySnapshot: [
            min    : 10,
            max    : 20,
            desired: 15
          ]
        ]
      }
    }

    when:
    def targetServerGroupContext = task.getTargetServerGroupContext(currentStage)

    then:
    targetServerGroupContext.sourceServerGroupCapacitySnapshot == siblingStage.context.sourceServerGroupCapacitySnapshot
    targetServerGroupContext.context == [
      region         : "us-west-2",
      asgName        : "asg-v001",
      serverGroupName: "asg-v001",
      credentials    : "test",
      cloudProvider  : "aws"
    ]
  }

  void "should get TargetServerGroupContext from coordinates from upstream deploy stage"() {
    given:
    def pipeline = PipelineExecutionImpl.newPipeline("orca")
    def deployStage = new StageExecutionImpl(pipeline, "", [
      refId                            : "1",
      "deploy.server.groups"           : ["us-west-2a": ["asg-v001"]],
      region                           : "us-west-2",
      sourceServerGroupCapacitySnapshot: [
        min    : 10,
        max    : 20,
        desired: 15
      ],
    ])


    def childStage = new StageExecutionImpl(pipeline, "", [
      requisiteRefIds: ["1"],
      credentials    : "test"
    ])

    pipeline.stages << deployStage
    pipeline.stages << childStage

    when:
    def targetServerGroupContext = task.getTargetServerGroupContext(childStage)

    then:
    targetServerGroupContext.sourceServerGroupCapacitySnapshot == deployStage.context.sourceServerGroupCapacitySnapshot
    targetServerGroupContext.context == [
      region         : "us-west-2",
      asgName        : "asg-v001",
      serverGroupName: "asg-v001",
      credentials    : "test",
      cloudProvider  : "aws"
    ]
  }
}
