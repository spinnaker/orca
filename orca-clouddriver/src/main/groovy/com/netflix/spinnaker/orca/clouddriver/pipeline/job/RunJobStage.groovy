/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.job

import com.netflix.spinnaker.orca.clouddriver.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.clouddriver.tasks.job.JobForceCacheRefreshTask
import com.netflix.spinnaker.orca.clouddriver.tasks.job.RunJobTask
import com.netflix.spinnaker.orca.clouddriver.tasks.job.WaitOnJobCompletion
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import org.springframework.stereotype.Component

@Component
class RunJobStage implements StageDefinitionBuilder {
  @Override
  List<StageDefinitionBuilder.TaskDefinition> taskGraph() {
    return [
      new StageDefinitionBuilder.TaskDefinition("runJob", RunJobTask),
      new StageDefinitionBuilder.TaskDefinition("monitorDeploy", MonitorKatoTask),
      new StageDefinitionBuilder.TaskDefinition("forceCacheRefresh", JobForceCacheRefreshTask),
      new StageDefinitionBuilder.TaskDefinition("waitOnJobCompletion", WaitOnJobCompletion)
    ]
  }
}
