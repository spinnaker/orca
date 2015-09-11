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

package com.netflix.spinnaker.orca.kato.tasks.gce.securitygroup

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.MortService
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class UpsertGoogleSecurityGroupTask extends AbstractCloudProviderAwareTask implements Task {

  @Autowired
  KatoService kato

  @Autowired
  MortService mortService

  @Autowired
  ObjectMapper mapper

  @Override
  TaskResult execute(Stage stage) {
    String cloudProvider = getCloudProvider(stage)
    String account = getCredentials(stage)

    def taskId = kato.requestOperations(cloudProvider, [[upsertSecurityGroup: stage.context]])
                     .toBlocking()
                     .first()

    Map outputs = [
      "notification.type"   : "upsertsecuritygroup",
      "kato.last.task.id"   : taskId,
      "targets"             : [
        [
          credentials: account,
          region     : stage.context.region,
          name       : stage.context.name
        ]
      ],
      // TODO(duftler): Do something with this.
      "securityGroupIngress": stage.context.securityGroupIngress ?: []
    ]

    println "** outputs=$outputs"

    new DefaultTaskResult(ExecutionStatus.SUCCEEDED, outputs)
  }
}
