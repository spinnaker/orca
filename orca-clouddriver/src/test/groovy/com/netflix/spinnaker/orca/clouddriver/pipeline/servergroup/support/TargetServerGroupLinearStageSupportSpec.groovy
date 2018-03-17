/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support

import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.ResizeServerGroupStage
import spock.lang.Specification
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupLinearStageSupport.Injectable
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class TargetServerGroupLinearStageSupportSpec extends Specification {

  def resolver = Stub(TargetServerGroupResolver)
  def supportStage = new testSupport(determineTargetServerGroupStage: new DetermineTargetServerGroupStage())

  void setup() {
    supportStage.resolver = resolver
  }

  @Unroll
  void "should inject determineTargetReferences stage when target is dynamic and parentStageId is #parentStageId"() {
    given:
    def stage = stage {
      type = "test"
      context["regions"] = ["us-east-1"]
      context["target"] = "current_asg_dynamic"
    }
    stage.parentStageId = parentStageId

    when:
    def syntheticStages = supportStage.composeTargets(stage).groupBy { it.syntheticStageOwner }

    then:
    syntheticStages.getOrDefault(STAGE_BEFORE, [])*.name == stageNamesBefore
    syntheticStages.getOrDefault(STAGE_AFTER, []).isEmpty()

    where:
    parentStageId | stageNamesBefore
    null          | ["determineTargetServerGroup"]
    "a"           | []
  }

  @Unroll
  void "should inject a stage for each extra region when the target is dynamically bound"() {
    given:
    def stage = stage {
      type = "test"
      context[(locationType + 's')] = ["us-east-1", "us-west-1", "us-west-2", "eu-west-2"]
      context["target"] = "current_asg_dynamic"
      context["cloudProvider"] = cloudProvider
    }

    when:
    def syntheticStages = supportStage.composeTargets(stage).groupBy { it.syntheticStageOwner }

    then:
    syntheticStages[STAGE_BEFORE].size() == 1
    syntheticStages[STAGE_AFTER].size() == 3
    syntheticStages[STAGE_AFTER]*.name == ["testSupport", "testSupport", "testSupport"]
    stage.context[locationType] == "us-east-1"
    stage.context[oppositeLocationType] == null
    syntheticStages[STAGE_AFTER]*.context[locationType].flatten() == ["us-west-1", "us-west-2", "eu-west-2"]

    where:
    locationType | oppositeLocationType | cloudProvider
    "region"     | "zone"               | null
    "zone"       | "region"             | 'gce'
  }

  void "should inject a stage after for each extra target when target is not dynamically bound"() {
    given:
    def stage = stage {
      type = "test"
      context["region"] = "should be overridden"
    }

    and:
    resolver.resolveByParams(_) >> [
      new TargetServerGroup(name: "asg-v001", region: "us-east-1"),
      new TargetServerGroup(name: "asg-v001", region: "us-west-1"),
      new TargetServerGroup(name: "asg-v002", region: "us-west-2"),
      new TargetServerGroup(name: "asg-v003", region: "eu-west-2"),
    ]

    when:
    def syntheticStages = supportStage.composeTargets(stage).groupBy { it.syntheticStageOwner }

    then:
    syntheticStages[STAGE_BEFORE] == null
    syntheticStages[STAGE_AFTER]*.name == ["testSupport", "testSupport", "testSupport"]
    syntheticStages[STAGE_AFTER]*.context.region.flatten() == ["us-west-1", "us-west-2", "eu-west-2"]
    stage.context.region == "us-east-1"
  }

  @Unroll
  def "#target should inject stages correctly before and after each location stage"() {
    given:
    def stage = stage {
      type = "test"
      context["target"] = target
      context["regions"] = ["us-east-1", "us-west-1"]
    }
    def arbitraryStageBuilder = new ResizeServerGroupStage()
    supportStage.preInjectables = [new Injectable(
      name: "testPreInjectable",
      stage: arbitraryStageBuilder,
      context: ["abc": 123]
    )]
    supportStage.postInjectables = [new Injectable(
      name: "testPostInjectable",
      stage: arbitraryStageBuilder,
      context: ["abc": 123]
    )]

    and:
    resolver.resolveByParams(_) >> [
      new TargetServerGroup(name: "asg-v001", region: "us-east-1"),
      new TargetServerGroup(name: "asg-v002", region: "us-west-1"),
    ]

    when:
    def syntheticStages = supportStage.composeTargets(stage).groupBy { it.syntheticStageOwner }

    then:
    syntheticStages[STAGE_BEFORE]*.name == beforeNames
    syntheticStages[STAGE_AFTER]*.name == ["testPostInjectable", "testPreInjectable", "testSupport", "testPostInjectable"]

    where:
    target                | beforeNames
    "current_asg"         | ["testPreInjectable"]
    "current_asg_dynamic" | ["determineTargetServerGroup", "testPreInjectable"]
  }

  class testSupport extends TargetServerGroupLinearStageSupport {

    List<Injectable> preInjectables
    List<Injectable> postInjectables

    @Override
    List<Injectable> preStatic(Map descriptor) {
      preInjectables
    }

    @Override
    List<Injectable> postStatic(Map descriptor) {
      postInjectables
    }

    @Override
    List<Injectable> preDynamic(Map context) {
      preInjectables
    }

    @Override
    List<Injectable> postDynamic(Map context) {
      postInjectables
    }
  }
}
