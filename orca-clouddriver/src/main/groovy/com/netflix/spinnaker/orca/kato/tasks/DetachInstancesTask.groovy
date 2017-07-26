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


package com.netflix.spinnaker.orca.kato.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class DetachInstancesTask implements RetryableTask, CloudProviderAware {

  final long backoffPeriod = 5000

  final long timeout = 30000

  @Autowired
  KatoService kato

  @Override
  TaskResult execute(Stage stage) {
    def taskId = kato.requestOperations(getCloudProvider(stage), [[detachInstances: stage.context]])
      .toBlocking()
      .first()

    new TaskResult(ExecutionStatus.SUCCEEDED, [
      "notification.type"     : "detachinstances",
      "kato.last.task.id"     : taskId,
      "terminate.instance.ids": stage.context.instanceIds,
      "terminate.account.name": getCredentials(stage),
      "terminate.region"      : stage.context.region
    ])
  }
}
