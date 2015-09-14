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

package com.netflix.spinnaker.orca.kato.pipeline

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReferenceSupport
import com.netflix.spinnaker.orca.kato.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.kato.tasks.ResizeServerGroupTask
import com.netflix.spinnaker.orca.kato.tasks.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.kato.tasks.WaitForCapacityMatchTask
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@Slf4j
class ResizeServerGroupStage extends LinearStage {

  public static final String TYPE = "resizeServerGroup"

  ResizeServerGroupStage() {
    super(TYPE)
  }

  @Override
  List<Step> buildSteps(Stage stage) {
    [
      buildStep(stage, "resizeServerGroup", ResizeServerGroupTask),
      buildStep(stage, "monitorServerGroup", MonitorKatoTask),
      buildStep(stage, "forceCacheRefresh", ServerGroupCacheForceRefreshTask),
      buildStep(stage, "waitForCapacityMatch", WaitForCapacityMatchTask),
    ]
  }

  /**
   * This version in only used for Pipeline executions, due to the dynamic nature of the target server group. It injects
   * a synthetic stage to resolve the target server group, then adds the Orchestration execution version of Resize
   * afterwards.
   */
  @Component
  @Slf4j
  class Pipeline extends LinearStage {

    public static final String TYPE = "resizeServerGroup_pipeline"

    @Autowired
    DetermineTargetReferenceStage determineTargetReferenceStage

    @Autowired
    TargetReferenceSupport targetReferenceSupport

    @Autowired
    ModifyScalingProcessStage modifyScalingProcessStage


    ResizeServerGroupStage resizeServerGroupStage

    /**
     * Because this is a nested, non-static class, the JVM passes the parent class instance as part of the default
     * constructor (usually implicitly, but by making it explicit, we can now use the @Autowired annotation to enable
     * Spring to manage it like any other @Component).
     */
    @Autowired
    Pipeline(ResizeServerGroupStage parent) {
      super(TYPE)
      newCloudProviderStyle = true
      resizeServerGroupStage = parent
    }

    @Override
    List<Step> buildSteps(Stage stage) {
      injectBefore(stage, "determineTargetReferences", determineTargetReferenceStage, stage.context)

      for (targetReference in targetReferenceSupport.getTargetAsgReferences(stage)) {
        def context = [
          credentials : stage.context.credentials,
          regions     : [targetReference.region]
        ]

        if (targetReferenceSupport.isDynamicallyBound(stage)) {
          def resizeContext = new HashMap(stage.context)
          resizeContext.regions = [targetReference.region]
          context.remove("asgName")
          context.target = stage.context.target
          injectAfter(stage, ResizeServerGroupStage.TYPE, resizeServerGroupStage, resizeContext)
        } else {
          context.asgName = targetReference.asg.name
        }

        if (stage.context.cloudProvider == 'aws') {
          injectBefore(stage, "resumeScalingProcesses", modifyScalingProcessStage, context + [
            action   : "resume",
            processes: ["Launch", "Terminate"]
          ])
          injectAfter(stage, "suspendScalingProcesses", modifyScalingProcessStage, context + [
            action: "suspend"
          ])
        }
      }

      // mark as SUCCEEDED otherwise a stage w/o child tasks will remain in NOT_STARTED
      stage.status = ExecutionStatus.SUCCEEDED
      return []
    }
  }
}
