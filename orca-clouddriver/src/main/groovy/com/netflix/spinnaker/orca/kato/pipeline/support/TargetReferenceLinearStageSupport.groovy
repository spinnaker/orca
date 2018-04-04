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

package com.netflix.spinnaker.orca.kato.pipeline.support

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.kato.pipeline.DetermineTargetReferenceStage
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilder
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

import javax.annotation.Nonnull

@Slf4j
@Deprecated
abstract class TargetReferenceLinearStageSupport implements StageDefinitionBuilder {

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  TargetReferenceSupport targetReferenceSupport

  @Autowired
  DetermineTargetReferenceStage determineTargetReferenceStage

  @Override
  void beforeStages(@Nonnull Stage parent, @Nonnull StageGraphBuilder graph) {
    log.warn("TargetReferenceLinearStageSupport is deprecated (execution: ${parent.execution.id})")

    parent.resolveStrategyParams()

    if (targetReferenceSupport.isDynamicallyBound(parent) && !parent.parentStageId) {
      Map injectedContext = new HashMap(parent.context)
      injectedContext.regions = new ArrayList(parent.context.regions)
      graph.add {
        it.type = determineTargetReferenceStage.type
        it.name = "determineTargetReferences"
        it.context = injectedContext
      }
    }
  }

  @Override
  void afterStages(@Nonnull Stage parent, @Nonnull StageGraphBuilder graph) {
    parent.resolveStrategyParams()
    composeTargets(parent, graph)
  }

  void composeTargets(Stage stage, StageGraphBuilder graph) {
    if (targetReferenceSupport.isDynamicallyBound(stage)) {
      composeDynamicTargets(stage, graph)
      return
    }

    composeStaticTargets(stage, graph)
  }

  private void composeStaticTargets(Stage stage, StageGraphBuilder graph) {
    def descriptionList = buildStaticTargetDescriptions(stage)
    if (descriptionList.empty) {
      throw new TargetReferenceNotFoundException("Could not find any server groups for specified target")
    }
    def first = descriptionList.remove(0)
    stage.context.putAll(first)

    List<Stage> stages = descriptionList.collect { description ->
      graph.append {
        it.type = type
        it.name = type
        it.context = description
      }
    }
  }

  private List<Map<String, Object>> buildStaticTargetDescriptions(Stage stage) {
    List<TargetReference> targets = targetReferenceSupport.getTargetAsgReferences(stage)

    return targets.collect { TargetReference target ->
      def region = target.region
      def asg = target.asg

      def description = new HashMap(stage.context)

      description.asgName = asg.name
      description.serverGroupName = asg.name
      description.region = region

      return description
    }
  }

  private void composeDynamicTargets(Stage stage, StageGraphBuilder graph) {
    List<Stage> stages = []

    // We only want to determine the target ASGs once per stage, so only inject if this is the root stage, i.e.
    // the one the user configured
    // This may become a bad assumption, or a limiting one, in that we cannot inject a dynamic stage ourselves
    // as part of some other stage that is not itself injecting a determineTargetReferences stage
    if (!stage.parentStageId) {
      def configuredRegions = stage.context.regions
      if (configuredRegions.size() > 1) {
        stage.context.region = configuredRegions.remove(0)
        for (region in configuredRegions) {
          def description = new HashMap(stage.context)
          description.region = region
          stages << graph.append {
            it.name = type
            it.type = type
            it.context = description
          }
        }
      }
    }
  }
}
