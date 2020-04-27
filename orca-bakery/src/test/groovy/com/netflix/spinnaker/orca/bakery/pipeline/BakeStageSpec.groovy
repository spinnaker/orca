/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.bakery.pipeline

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.exceptions.ConstraintViolationException

import java.time.Clock
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilderImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.netflix.spinnaker.orca.pipeline.util.RegionCollector
import spock.lang.Specification
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage
import static java.time.Instant.EPOCH
import static java.time.ZoneOffset.UTC
import static java.time.temporal.ChronoUnit.*

class BakeStageSpec extends Specification {
  def dynamicConfigService = Mock(DynamicConfigService)

  @Unroll
  def "should build contexts corresponding to locally specified bake region and all target deploy regions"() {
    given:
    def pipeline = pipeline {
      deployAvailabilityZones?.each { zones ->
        stage {
          type = "deploy"
          name = "Deploy!"
          context = zones
          refId = "2"
          requisiteStageRefIds = ["1"]
        }
      }
    }

    def bakeStage = new StageExecutionImpl(pipeline, "bake", "Bake!", bakeStageContext + [refId: "1"])
    def builder = new BakeStage(
      clock: Clock.fixed(EPOCH.plus(1, HOURS).plus(15, MINUTES).plus(12, SECONDS), UTC),
      regionCollector: new RegionCollector()
    )

    when:
    def parallelContexts = builder.parallelContexts(bakeStage)

    then:
    parallelContexts == expectedParallelContexts

    where:
    bakeStageContext                                                                                      | deployAvailabilityZones                                                            || expectedParallelContexts
    [cloudProviderType: "aws"]                                                                            | deployAz("aws", "cluster", "us-west-1") + deployAz("aws", "cluster", "us-west-2")  || expectedContexts("aws", "19700101011512", "us-west-1", "us-west-2")
    [cloudProviderType: "aws", region: "us-east-1"]                                                       | deployAz("aws", "cluster", "us-west-1")                                            || expectedContexts("aws", "19700101011512", "us-east-1", "us-west-1")
    [cloudProviderType: "aws"]                                                                            | deployAz("aws", "clusters", "us-west-1")                                           || expectedContexts("aws", "19700101011512", "us-west-1")
    [cloudProviderType: "aws", region: "us-east-1"]                                                       | deployAz("aws", "clusters", "us-west-1")                                           || expectedContexts("aws", "19700101011512", "us-east-1", "us-west-1")
    [cloudProviderType: "aws", region: "us-east-1"]                                                       | []                                                                                 || expectedContexts("aws", "19700101011512", "us-east-1")
    [cloudProviderType: "aws", region: "us-east-1"]                                                       | null                                                                               || expectedContexts("aws", "19700101011512", "us-east-1")
    [cloudProviderType: "aws", region: "us-east-1"]                                                       | deployAz("aws", "clusters", "us-east-1")                                           || expectedContexts("aws", "19700101011512", "us-east-1")
    [cloudProviderType: "aws", region: "us-east-1", amiSuffix: ""]                                        | null                                                                               || expectedContexts("aws", "19700101011512", "us-east-1")
    [cloudProviderType: "aws", region: "us-east-1", amiSuffix: "--"]                                      | null                                                                               || expectedContexts("aws", "--", "us-east-1")
    [cloudProviderType: "aws", region: "global"]                                                          | deployAz("aws", "clusters", "us-west-1")                                           || expectedContexts("aws", "19700101011512", "global")
    [cloudProviderType: "aws", region: "us-east-1", regions: ["us-west-1"]]                               | null                                                                               || expectedContexts("aws", "19700101011512", "us-east-1", "us-west-1")
    [cloudProviderType: "aws", region: "us-east-1", regions: []]                                          | null                                                                               || expectedContexts("aws", "19700101011512", "us-east-1")
    [cloudProviderType: "aws", regions: ["us-east-1", "us-west-1"]]                                       | null                                                                               || expectedContexts("aws", "19700101011512", "us-east-1", "us-west-1")
    [cloudProviderType: "aws", region: "us-east-1", regions: null]                                        | null                                                                               || expectedContexts("aws", "19700101011512", "us-east-1")
    [cloudProviderType: "aws", regions: ["us-east-1", "us-west-1"]]                                       | deployAz("aws", "clusters", "us-west-1")                                           || expectedContexts("aws", "19700101011512", "us-east-1", "us-west-1")
    [cloudProviderType: "aws"]                                                                            | deployAz("aws", "cluster", "us-west-1") + deployAz("gce", "cluster", "us-west1")   || expectedContexts("aws", "19700101011512", "us-west-1")
    [cloudProviderType: "gce", region: "global"]                                                          | deployAz("aws", "cluster", "us-west-1")                                            || expectedContexts("gce", "19700101011512", "global")
    [cloudProviderType: "gce", region: "global"]                                                          | deployAz("gce", "cluster", "us-west1")                                             || expectedContexts("gce", "19700101011512", "global")
    [cloudProviderType: "aws"]                                                                            | deployAz("aws", "clusters", "us-west-1") + deployAz("gce", "clusters", "us-west1") || expectedContexts("aws", "19700101011512", "us-west-1")
    [cloudProviderType: "gce", region: "global"]                                                          | deployAz("aws", "clusters", "us-west-1")                                           || expectedContexts("gce", "19700101011512", "global")
    [cloudProviderType: "gce", region: "global"]                                                          | deployAz("gce", "clusters", "us-west1")                                            || expectedContexts("gce", "19700101011512", "global")
    [cloudProviderType: "gce", region: "global", skipRegionDetection: true]                               | deployAz("gce", "clusters", "us-west1")                                            || expectedContexts("gce", "19700101011512", "global")
    [cloudProviderType: "aws", region: "us-west-2", skipRegionDetection: true]                            | deployAz("aws", "cluster", "us-west-1") + deployAz("aws", "cluster", "us-west-2")  || expectedContexts("aws", "19700101011512", "us-west-2")
    [cloudProviderType: "aws", regions: ["us-east-1", "us-west-1"], skipRegionDetection: true]            | deployAz("aws", "clusters", "eu-central-1")                                        || expectedContexts("aws", "19700101011512", "us-east-1", "us-west-1")
    [cloudProviderType: "aws", regions: ["us-east-1", "us-west-1"], skipRegionDetection: false]           | deployAz("aws", "cluster", "us-west-1") + deployAz("aws", "cluster", "us-west-2")  || expectedContexts("aws", "19700101011512", "us-east-1", "us-west-1", "us-west-2")
    [cloudProviderType: "aws", region: "us-west-2", skipRegionDetection: false]                           | deployAz("aws", "cluster", "us-west-2") + deployAz("aws", "clusters", "us-east-1") || expectedContexts("aws", "19700101011512", "us-west-2", "us-east-1")
    [cloudProviderType: "aws", region: "us-west-2", skipRegionDetection: 0]                               | deployAz("aws", "cluster", "us-west-2") + deployAz("aws", "clusters", "us-east-1") || expectedContexts("aws", "19700101011512", "us-west-2", "us-east-1")
  }

  def "should include per-region stage contexts as global deployment details"() {
    given:
    def pipeline = pipeline {
      stage {
        id = "1"
        type = "bake"
        context = [
          "region": "us-east-1",
          "regions": ["us-east-1", "us-west-2", "eu-east-1"]
        ]
        status = ExecutionStatus.RUNNING
      }
    }

    def bakeStage = pipeline.stageById("1")
    def graph = StageGraphBuilderImpl.beforeStages(bakeStage)
    new BakeStage(regionCollector: new RegionCollector()).beforeStages(bakeStage, graph)
    def parallelStages = graph.build()

    parallelStages.eachWithIndex { it, idx -> it.context.ami = idx + 1 }
    pipeline.stages.addAll(parallelStages)

    when:
    def taskResult = new BakeStage.CompleteParallelBakeTask().execute(pipeline.stageById("1"))

    then:
    with(taskResult.outputs) {
      deploymentDetails[0].ami == 1
      deploymentDetails[1].ami == 2
      deploymentDetails[2].ami == 3
    }
  }

  def "should fail if image names don't match across regions"() {
    given:
    def pipeline = pipeline {
      stage {
        id = "1"
        type = "bake"
        context = [
          "region": "us-east-1",
          "regions": ["us-east-1", "us-west-2", "eu-east-1"],
        ]
        status = ExecutionStatus.RUNNING
      }
    }

    def bakeStage = pipeline.stageById("1")
    def graph = StageGraphBuilderImpl.beforeStages(bakeStage)
    new BakeStage(regionCollector: new RegionCollector()).beforeStages(bakeStage, graph)
    def parallelStages = graph.build()

    parallelStages.eachWithIndex { it, idx ->
      it.context.ami = "${idx}"
      it.context.imageName = "image#${idx}"
    }
    pipeline.stages.addAll(parallelStages)

    dynamicConfigService.isEnabled("stages.bake.failOnImageNameMismatch", false) >> { true }

    when:
    new BakeStage.CompleteParallelBakeTask(dynamicConfigService).execute(bakeStage)

    then:
    thrown(ConstraintViolationException)
  }

  def "should NOT fail if image names from unrelated bake stages don't match"() {
    given:
    def pipeline = pipeline {
      stage {
        id = "1"
        type = "bake"
        context = [
          "region": "us-east-1",
          "regions": ["us-east-1", "us-west-2"]
        ]
        status = ExecutionStatus.RUNNING
      }
      // this is a sibling bake stage whose child bake contexts should not be included in stage 1's outputs, but are
      stage {
        id = "2"
        type = "bake"
        context = [
          "region": "us-east-1",
          "regions": ["us-east-1"]
        ]
        status = ExecutionStatus.RUNNING
      }
    }

    for (stageId in ["1", "2"]) {
      def bakeStage = pipeline.stageById(stageId)
      def graph = StageGraphBuilderImpl.beforeStages(bakeStage)
      new BakeStage(regionCollector: new RegionCollector()).beforeStages(bakeStage, graph)
      def childBakeStages = graph.build()
      childBakeStages.eachWithIndex { it, idx ->
        it.context.ami = "${idx}"
        it.context.imageName = "image-from-bake-stage-${stageId}"
      }
      pipeline.stages.addAll(childBakeStages)
    }

    dynamicConfigService.isEnabled("stages.bake.failOnImageNameMismatch", false) >> { true }

    when:
    new BakeStage.CompleteParallelBakeTask(dynamicConfigService).execute(pipeline.stageById("1"))

    then:
    notThrown(ConstraintViolationException)
  }

  private
  static List<Map> deployAz(String cloudProvider, String prefix, String... regions) {
    if (prefix == "clusters") {
      return [[clusters: regions.collect {
        [cloudProvider: cloudProvider, availabilityZones: [(it): []]]
      }]]
    }

    return regions.collect {
      if (prefix == "cluster") {
        return [cluster: [cloudProvider: cloudProvider, availabilityZones: [(it): []]]]
      }
      return [cloudProvider: cloudProvider, availabilityZones: [(it): []]]
    }
  }

  private
  static List<Map> expectedContexts(String cloudProvider, String amiSuffix, String... regions) {
    return regions.collect {
      [cloudProviderType: cloudProvider, amiSuffix: amiSuffix, type: BakeStage.PIPELINE_CONFIG_TYPE, "region": it, name: "Bake in ${it}"]
    }
  }
}
