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

package com.netflix.spinnaker.orca.kato.tasks

import com.netflix.spinnaker.orca.DebugSupport
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.kato.api.KatoService
import com.netflix.spinnaker.orca.kato.pipeline.ResizeAsgStage
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeSupport
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReferenceSupport
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@Slf4j
class ResizeServerGroupTask implements Task{

  @Autowired
  KatoService kato

  @Autowired
  TargetReferenceSupport targetReferenceSupport

  @Autowired
  ResizeSupport resizeSupport

  @Override
  TaskResult execute(Stage stage) {
    def operation = convert(stage)
    def taskId = kato.requestOperations([[resizeServerGroup: operation]])
                     .toBlocking()
                     .first()
    new DefaultTaskResult(ExecutionStatus.SUCCEEDED, [
      "notification.type"   : "resizeasg", // TODO(someone on NFLX side): Rename to 'resizeservergroup'?
      "deploy.account.name" : operation.credentials,
      "kato.last.task.id"   : taskId,
      "asgName"             : operation.asgName,
      "capacity"            : operation.capacity,
      "deploy.server.groups": operation.regions.collectEntries {
        [(it): [operation.asgName]]
      }
    ])
  }

  Map convert(Stage stage) {
    Map operation = new HashMap(stage.context)
    if (targetReferenceSupport.isDynamicallyBound(stage)) {
      def targetReference = targetReferenceSupport.getDynamicallyBoundTargetAsgReference(stage)
      def descriptors = resizeSupport.createResizeStageDescriptors(stage, [targetReference])
      if (descriptors) {
        operation = descriptors[0]
      }
    }
    if (operation.containsKey(ResizeAsgStage.PIPELINE_CONFIG_TYPE)) {
      operation = (Map) operation[ResizeAsgStage.PIPELINE_CONFIG_TYPE]
    }
    operation
  }
}
