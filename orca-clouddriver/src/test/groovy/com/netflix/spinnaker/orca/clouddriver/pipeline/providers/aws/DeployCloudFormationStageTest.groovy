/*
 * Copyright (c) 2019 Schibsted Media Group.
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
package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws

import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject

class DeployCloudFormationStageTest extends Specification {

  def builder = new TaskNode.Builder()

  @Subject
  def cloudFormationStage = new DeployCloudFormationStage()

  def "should return CloudFormation execution ID"() {
    given:
    def stage = new Stage(new Execution(Execution.ExecutionType.PIPELINE, "testApp"), "cf", [:])

    when:
    if (isChangeSet) {
      stage.context.put("isChangeSet", true)
    }
    cloudFormationStage.taskGraph(stage, builder)

    then:
    builder.graph.size == graphSize

    where:
    isChangeSet || graphSize
    false       || 4
    true        || 6
  }

}
