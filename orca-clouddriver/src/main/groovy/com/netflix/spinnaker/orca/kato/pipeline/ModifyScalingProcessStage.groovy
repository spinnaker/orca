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

import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.ModifyAwsScalingProcessStage
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReferenceLinearStageSupport
import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCacheForceRefreshTask
import com.netflix.spinnaker.orca.kato.tasks.scalingprocess.ResumeScalingProcessTask
import com.netflix.spinnaker.orca.kato.tasks.scalingprocess.SuspendScalingProcessTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import org.springframework.stereotype.Component

@Component
@CompileStatic
@Deprecated
class ModifyScalingProcessStage extends TargetReferenceLinearStageSupport {

  @Override
  def <T extends Execution> List<StageDefinitionBuilder.TaskDefinition> taskGraph(Stage<T> parentStage) {
    def data = parentStage.mapTo(StageData)
    switch (data.action) {
      case StageAction.suspend:
        return [
          new StageDefinitionBuilder.TaskDefinition("suspend", SuspendScalingProcessTask),
          new StageDefinitionBuilder.TaskDefinition("monitor", MonitorKatoTask),
          new StageDefinitionBuilder.TaskDefinition("forceCacheRefresh", ServerGroupCacheForceRefreshTask),
          new StageDefinitionBuilder.TaskDefinition("waitForScalingProcesses", ModifyAwsScalingProcessStage.WaitForScalingProcess)
        ]
      case StageAction.resume:
        return [
          new StageDefinitionBuilder.TaskDefinition("resume", ResumeScalingProcessTask),
          new StageDefinitionBuilder.TaskDefinition("monitor", MonitorKatoTask),
          new StageDefinitionBuilder.TaskDefinition("forceCacheRefresh", ServerGroupCacheForceRefreshTask),
          new StageDefinitionBuilder.TaskDefinition("waitForScalingProcesses", ModifyAwsScalingProcessStage.WaitForScalingProcess)
        ]
    }
    throw new RuntimeException("No action specified!")
  }

  enum StageAction {
    suspend, resume
  }

  static class StageData {
    StageAction action
  }
}
