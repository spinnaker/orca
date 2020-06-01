/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.scalingpolicy

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution

import java.util.concurrent.TimeUnit
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import groovy.util.logging.Slf4j

@Component
@Slf4j
class UpsertScalingPolicyTask extends AbstractCloudProviderAwareTask implements RetryableTask {

  @Autowired
  KatoService kato

  long backoffPeriod = TimeUnit.SECONDS.toMillis(5)
  long timeout = TimeUnit.SECONDS.toMillis(100)

  @Override
  TaskResult execute(StageExecution stage) {
    try {
      def taskId = kato.requestOperations(getCloudProvider(stage), [[upsertScalingPolicy: stage.context]])

      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context([
        "deploy.account.name" : stage.context.credentials,
        "kato.last.task.id"   : taskId,
        "deploy.server.groups": [(stage.context.region): [stage.context.serverGroupName]]
      ]).build()
    }
    catch (Exception e) {
      log.error("Failed upsertScalingPolicy task (stageId: ${stage.id}, executionId: ${stage.execution.id})", e)
      return TaskResult.ofStatus(ExecutionStatus.RUNNING)
    }
  }
}
