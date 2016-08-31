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


package com.netflix.spinnaker.orca.applications.pipelines

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
class DeleteProjectStage implements StageDefinitionBuilder {
  @Override
  <T extends Execution> List<StageDefinitionBuilder.TaskDefinition> taskGraph(Stage<T> parentStage) {
    return Collections.singletonList(
      new StageDefinitionBuilder.TaskDefinition("deleteProject", DeleteProjectTask)
    );
  }

  @Component
  static class DeleteProjectTask implements Task {
    @Autowired
    Front50Service front50Service

    @Override
    TaskResult execute(Stage stage) {
      def projectId = stage.mapTo("/project", Front50Service.Project)
      front50Service.deleteProject(projectId.id)

      def outputs = [
        "notification.type": "deleteproject"
      ]

      return new DefaultTaskResult(ExecutionStatus.SUCCEEDED, outputs)
    }
  }
}
