/*
 * Copyright 2022 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.echo.notification

import com.google.common.base.Preconditions
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.pipeline.CompoundExecutionOperator
import com.netflix.spinnaker.security.AuthenticatedRequest
import org.apache.commons.lang3.StringUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.RequestEntity
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.orca.echo.EchoService.Notification.*

@Component
class ManualJudgementCallbackHandler {
  @Autowired
  CompoundExecutionOperator executionOperator

  ResponseEntity<String> processCallback(RequestEntity<InteractiveActionCallback> request) {
    InteractiveActionCallback interactiveActionCallback = request.getBody()
    String judgement = interactiveActionCallback.getActionPerformed().getValue()
    String messageId = interactiveActionCallback.getMessageId()
    String user = interactiveActionCallback.getUser()
    String[] ids = StringUtils.split(messageId, "-")
    Preconditions.checkArgument(ids.length == 3, "Unexpected message id")
    ExecutionType executionType = getExecutionType(ids[0])
    String executionId = ids[1]
    String stageId = ids[2]
    PipelineExecution pipelineExecution = executionOperator.updateStage(executionType, executionId, stageId,
        { stage ->
          stage.context.putAll(Map.of("judgmentStatus", judgement.toLowerCase()))
          stage.lastModified = new StageExecution.LastModifiedDetails(
              user: user,
              allowedAccounts: AuthenticatedRequest.getSpinnakerAccounts().orElse(null)?.split(",") ?: [],
              lastModifiedTime: System.currentTimeMillis()
          )
          stage.context["lastModifiedBy"] = stage.lastModified.user
        })
    String pipelineName = pipelineExecution.getName()
    return ResponseEntity.ok("pipeline $pipelineName updated successfully") as ResponseEntity<String>
  }

  private static ExecutionType getExecutionType(String name) {
    for (ExecutionType executionType : ExecutionType.values()) {
      if (executionType.toString().equalsIgnoreCase(name)) {
        return executionType;
      }
    }
    throw new IllegalArgumentException();
  }
}
