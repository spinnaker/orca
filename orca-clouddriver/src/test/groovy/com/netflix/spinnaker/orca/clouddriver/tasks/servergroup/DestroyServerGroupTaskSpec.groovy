/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver
import com.netflix.spinnaker.orca.clouddriver.utils.TrafficGuard
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import spock.lang.Specification
import spock.lang.Subject

class DestroyServerGroupTaskSpec extends Specification {
  @Subject
    task = new DestroyServerGroupTask(
      trafficGuard: Stub(TrafficGuard),
      retrySupport: Spy(RetrySupport) {
        _ * sleep(_) >> { /* do nothing */ }
      }
    )
  def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "whatever")
  def taskId = new TaskId(UUID.randomUUID().toString())

  def destroyASGConfig = [
    asgName      : "test-asg",
    cloudProvider: "aws",
    credentials  : "fzlem",
    regions      : ["us-west-1"],
  ]

  def setup() {
    stage.context = destroyASGConfig
  }

  def "creates a destroy ASG task based on job parameters"() {
    given:
      def operations
      task.kato = Mock(KatoService) {
        1 * requestOperations('aws', _) >> {
          operations = it[1]
          taskId
        }
      }

    when:
    task.execute(stage)

    then:
      operations.size() == 1
      with(operations[0].destroyServerGroup) {
        it instanceof Map
        asgName == this.destroyASGConfig.asgName
        regions == this.destroyASGConfig.regions
        credentials == this.destroyASGConfig.credentials
      }
  }

  def "returns a success status with the kato task id"() {
    given:
      task.kato = Stub(KatoService) {
        requestOperations('aws', _) >> taskId
      }

    when:
    def result = task.execute(stage)

    then:
      result.status == ExecutionStatus.SUCCEEDED
    result.context."kato.last.task.id" == taskId
    result.context."deploy.account.name" == destroyASGConfig.credentials
  }

  void "should get target dynamically when configured"() {
    setup:
      stage.context.target = TargetServerGroup.Params.Target.ancestor_asg_dynamic
      task.kato = Stub(KatoService) {
        requestOperations('aws', _) >> taskId
      }
      GroovyMock(TargetServerGroupResolver, global: true)
      TargetServerGroupResolver.fromPreviousStage(_) >> new TargetServerGroup(region: "us-west-1", name: "foo-v001")
      GroovyMock(TargetServerGroup, global: true)
      TargetServerGroup.isDynamicallyBound(_) >> true

    when:
    def result = task.execute(stage)

    then:
    result.context.asgName == "foo-v001"
    result.context."deploy.server.groups" == ["us-west-1": ["foo-v001"]]
  }

  def "task uses serverGroupName if present"() {
    given:
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "whatever2")
      stage.context = [
        cloudProvider  : "aws",
        serverGroupName: "test-server-group",
        regions        : ["us-west-1"],
        credentials    : "test"
      ]
      task.kato = Stub(KatoService) {
        requestOperations('aws', _) >> taskId
      }

    when:
    def result = task.execute(stage)

    then:
      result.status == ExecutionStatus.SUCCEEDED
    result.context."kato.last.task.id" == taskId
    result.context."deploy.account.name" == 'test'
    result.context."asgName" == 'test-server-group'
    result.context."serverGroupName" == 'test-server-group'
  }
}
