/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.support

import com.netflix.spinnaker.orca.batch.StageBuilder
import com.netflix.spinnaker.orca.clouddriver.pipeline.DetermineTargetServerGroupStage
import com.netflix.spinnaker.orca.kato.pipeline.Nameable
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired

@Slf4j
abstract class TargetServerGroupLinearStageSupport extends LinearStage implements Nameable {

  @Autowired
  TargetServerGroupResolver resolver

  @Autowired
  DetermineTargetServerGroupStage determineTargetServerGroupStage

  String name = this.type

  TargetServerGroupLinearStageSupport(String name) {
    super(name)
  }

  void composeTargets(Stage stage) {
    if(stage.execution instanceof Pipeline && stage.execution.trigger.parameters?.strategy == true){
      Map trigger = ((Pipeline) stage.execution).trigger
      stage.context.regions = [trigger.parameters.region]
      stage.context.cluster = trigger.parameters.cluster
      stage.context.credentials = trigger.parameters.credentials
    }
    def params = TargetServerGroup.Params.fromStage(stage)
    if (TargetServerGroup.isDynamicallyBound(stage)) {
      composeDynamicTargets(stage, params)
    } else {
      composeStaticTargets(stage, params)
    }
  }

  private void composeStaticTargets(Stage stage, TargetServerGroup.Params params) {
    if (stage.parentStageId) {
      // Only process this stage as-is when the user specifies. Otherwise, the targets should already be defined in the
      // context.
      return
    }

    def targets = resolver.resolveByParams(params)
    def descriptionList = buildStaticTargetDescriptions(stage, targets)
    def first = descriptionList.remove(0)
    stage.context.putAll(first)

    preStatic(first).each {
      injectBefore(stage, it.name, it.stage, it.context)
    }
    postStatic(first).each {
      injectAfter(stage, it.name, it.stage, it.context)
    }

    for (description in descriptionList) {
      preStatic(description).each {
        // Operations done after the first iteration must all be added with injectAfter.
        injectAfter(stage, it.name, it.stage, it.context)
      }
      injectAfter(stage, name, this, description)

      postStatic(description).each {
        injectAfter(stage, it.name, it.stage, it.context)
      }
    }
  }

  protected List<Map<String, Object>> buildStaticTargetDescriptions(Stage stage, List<TargetServerGroup> targets) {
    List<Map<String, Object>> descriptions = []
    for (target in targets) {
      def description = new HashMap(stage.context)
      description.asgName = target.name
      description.serverGroupName = target.name

      def location = target.getLocation()
      if (location.type == Location.Type.ZONE) {
        description.zone = location.value
      } else if (location.type == Location.Type.REGION) {
        // Clouddriver operations work with multiple values here, but we're choosing to only use 1 per operation.
        description.regions = [location.value]
      }
      description.deployServerGroupsRegion = target.region
      description.targetLocation = [type: location.type.name(), value: location.value]

      descriptions << description
    }
    descriptions
  }

  private void composeDynamicTargets(Stage stage, TargetServerGroup.Params params) {
    if (stage.parentStageId) {
      // We only want to determine the target server groups once per stage, so only inject if this is the root stage,
      // i.e. the one the user configured.
      // This may become a bad assumption, or a limiting one, in that we cannot inject a dynamic stage ourselves
      // as part of some other stage that is not itself injecting a determineTargetReferences stage.
      return
    }

    // Scrub the context of any preset location.
    stage.context.with {
      remove("zone")
      remove("zones")
      remove("region")
      remove("regions")
    }

    def locationType = params.locations[0].pluralType()

    Map dtsgContext = new HashMap(stage.context)
    dtsgContext[locationType] = params.locations.collect { it.value }

    // The original stage.context object is reused here because concrete subclasses must actually perform the requested
    // operation. All future copies of the subclass (operating on different regions/zones) use a copy of the context.
    def initialLocation = params.locations.head()
    def remainingLocations = params.locations.tail()
    stage.context[locationType] = [initialLocation.value]
    stage.context.targetLocation = [type: initialLocation.type.name(), value: initialLocation.value]

    preDynamic(stage.context).each {
      injectBefore(stage, it.name, it.stage, it.context)
    }
    postDynamic(stage.context).each {
      injectAfter(stage, it.name, it.stage, it.context)
    }

    for (location in remainingLocations) {
      def ctx = new HashMap(stage.context)
      ctx[locationType] = [location.value]
      ctx.targetLocation = [type: location.type.name(), value: location.value]
      preDynamic(ctx).each {
        // Operations done after the first pre-postDynamic injection must all be added with injectAfter.
        injectAfter(stage, it.name, it.stage, it.context)
      }
      injectAfter(stage, name, this, ctx)
      postDynamic(ctx).each {
        injectAfter(stage, it.name, it.stage, it.context)
      }
    }

    // For silly reasons, this must be added after the pre/post-DynamicInject to get the execution order right.
    injectBefore(stage, DetermineTargetServerGroupStage.PIPELINE_CONFIG_TYPE, determineTargetServerGroupStage, dtsgContext)
  }

  protected List<Injectable> preStatic(Map descriptor) {}

  protected List<Injectable> postStatic(Map descriptor) {}

  protected List<Injectable> preDynamic(Map context) {}

  protected List<Injectable> postDynamic(Map context) {}

  static class Injectable {
    String name
    StageBuilder stage
    Map<String, Object> context
  }
}
