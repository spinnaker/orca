/*
 * Copyright 2014 Netflix, Inc.
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

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.Task
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.kato.pipeline.ResizeAsgStage
import com.netflix.spinnaker.orca.kato.pipeline.support.ResizeSupport
import com.netflix.spinnaker.orca.kato.pipeline.support.TargetReferenceSupport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.Nonnull

@Component
@Deprecated
class ResizeAsgTask implements Task {

  @Autowired
  KatoService kato

  @Autowired
  TargetReferenceSupport targetReferenceSupport

  @Autowired
  ResizeSupport resizeSupport

  @Nonnull
  @Override
  TaskResult execute(@Nonnull StageExecution stage) {
    def operation = convert(stage)
    def taskId = kato.requestOperations([[resizeAsgDescription: operation]])
                     .toBlocking()
                     .first()
    TaskResult.builder(ExecutionStatus.SUCCEEDED).context([
        "notification.type"   : "resizeasg",
        "deploy.account.name" : operation.credentials,
        "kato.last.task.id"   : taskId,
        "asgName"             : operation.asgName,
        "capacity"            : operation.capacity,
        "deploy.server.groups": operation.regions.collectEntries {
          [(it): [operation.asgName]]
        }

    ]).build()
  }

  Map convert(StageExecution stage) {
    Map context = stage.context
    if (targetReferenceSupport.isDynamicallyBound(stage)) {
      def targetReference = targetReferenceSupport.getDynamicallyBoundTargetAsgReference(stage)
      def descriptors = resizeSupport.createResizeStageDescriptors(stage, [targetReference])
      if (!descriptors.isEmpty()) {
        context = descriptors[0]
      }
    }
    if (context.containsKey(ResizeAsgStage.PIPELINE_CONFIG_TYPE)) {
      context = (Map) context[ResizeAsgStage.PIPELINE_CONFIG_TYPE]
    }
    context
  }
}
