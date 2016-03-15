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

package com.netflix.spinnaker.orca.igor.pipeline

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.batch.RestartableStage
import com.netflix.spinnaker.orca.igor.tasks.MonitorJenkinsJobTask
import com.netflix.spinnaker.orca.igor.tasks.MonitorQueuedJenkinsJobTask
import com.netflix.spinnaker.orca.igor.tasks.StartScriptTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.Task
import groovy.transform.CompileStatic
import org.springframework.stereotype.Component

@Component
@CompileStatic
class ScriptStage implements StageDefinitionBuilder, RestartableStage {

  @Override
  <T extends Execution> List<StageDefinitionBuilder.TaskDefinition> taskGraph(Stage<T> parentStage) {
    return [
        new StageDefinitionBuilder.TaskDefinition("startScript", StartScriptTask),
        new StageDefinitionBuilder.TaskDefinition("waitForScriptStart", MonitorQueuedJenkinsJobTask),
        new StageDefinitionBuilder.TaskDefinition("monitorScript", MonitorJenkinsJobTask)
    ]
  }

  @Override
  Stage prepareStageForRestart(Stage stage) {
    stage = StageDefinitionBuilder.StageDefinitionBuilderSupport.prepareStageForRestart(stage)
    stage.startTime = null
    stage.endTime = null

    if (stage.context.buildInfo) {
      stage.context.restartDetails["previousBuildInfo"] = stage.context.buildInfo
    }
    stage.context.remove("buildInfo")
    stage.context.remove("buildNumber")

    stage.tasks.each { Task task ->
      task.startTime = null
      task.endTime = null
      task.status = ExecutionStatus.NOT_STARTED
    }

    return stage
  }
}
