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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.netflix.spinnaker.orca.api.operations.OperationsContext
import com.netflix.spinnaker.orca.api.operations.OperationsInput
import com.netflix.spinnaker.orca.api.operations.OperationsRunner
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware

import com.netflix.spinnaker.orca.clouddriver.utils.HealthHelper
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
class CreateServerGroupTask implements CloudProviderAware, RetryableTask {

  @Autowired
  OperationsRunner operationsRunner

  @Autowired
  List<ServerGroupCreator> serverGroupCreators

  long backoffPeriod = 2000
  long timeout = 60000

  @Override
  TaskResult execute(StageExecution stage) {
    String credentials = getCredentials(stage)
    String cloudProvider = getCloudProvider(stage)
    def creator = serverGroupCreators.find { it.cloudProvider == cloudProvider }
    if (!creator) {
      throw new IllegalStateException("ServerGroupCreator not found for cloudProvider $cloudProvider")
    }

    def ops = creator.getOperations(stage)
    OperationsInput operationsInput = OperationsInput.of(cloudProvider, ops, stage)
    OperationsContext operationsContext = operationsRunner.run(operationsInput)

    Map outputs = [
        "notification.type"             : "createdeploy",
        "kato.result.expected"          : creator.katoResultExpected,
        (operationsContext.contextKey()): operationsContext.contextValue(),
        "deploy.account.name"           : credentials
    ]

    if (stage.context.suspendedProcesses?.contains("AddToLoadBalancer")) {
      outputs.interestingHealthProviderNames = HealthHelper.getInterestingHealthProviderNames(stage, ["Amazon"])
    }

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build()
  }
}
