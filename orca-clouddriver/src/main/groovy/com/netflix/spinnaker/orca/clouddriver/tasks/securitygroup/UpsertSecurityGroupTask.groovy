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

package com.netflix.spinnaker.orca.clouddriver.tasks.securitygroup

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class UpsertSecurityGroupTask extends AbstractCloudProviderAwareTask {

  @Autowired
  KatoService kato

  @Autowired
  List<SecurityGroupUpserter> securityGroupUpserters

  @Override
  TaskResult execute(Stage stage) {
    String cloudProvider = getCloudProvider(stage)
    def upserter = securityGroupUpserters.find { it.cloudProvider == cloudProvider }
    if (!upserter) {
      throw new IllegalStateException("SecurityGroupUpserter not found for cloudProvider $cloudProvider")
    }

    SecurityGroupUpserter.OperationContext result = upserter.getOperationContext(stage)
    def taskId = kato.requestOperations(cloudProvider, result.operations).toBlocking().first()

    Map outputs = [
        "notification.type"   : "upsertsecuritygroup",
        "kato.last.task.id"   : taskId,
    ] + result.extraOutput

    new TaskResult(ExecutionStatus.SUCCEEDED, outputs)
  }
}
