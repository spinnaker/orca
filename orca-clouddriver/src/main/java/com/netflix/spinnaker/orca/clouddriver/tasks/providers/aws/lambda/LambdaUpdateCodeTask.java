/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.TaskExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.OperationContext;
import com.netflix.spinnaker.orca.clouddriver.model.SubmitOperationResult;
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.lambda.LambdaStageConstants;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaUpdateCodeInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.output.LambdaCloudOperationOutput;
import com.netflix.spinnaker.orca.pipeline.model.StageContext;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LambdaUpdateCodeTask implements LambdaStageBaseTask {

  private final KatoService katoService;
  private final ObjectMapper objectMapper;

  public LambdaUpdateCodeTask(KatoService katoService, ObjectMapper objectMapper) {
    this.katoService = katoService;
    this.objectMapper = objectMapper;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    log.debug("Executing LambdaUpdateCodeTask...");
    prepareTask(stage);

    StageContext stageContext = (StageContext) stage.getContext();
    boolean justCreated =
        stageContext.containsKey(LambdaStageConstants.lambaCreatedKey)
            && (Boolean)
                stageContext.getOrDefault(LambdaStageConstants.lambaCreatedKey, Boolean.FALSE);
    log.debug("justCreated GetCurrent:" + justCreated);
    if (justCreated) {
      return taskComplete(stage);
    }

    LambdaCloudOperationOutput output = updateLambdaCode(stage);
    addCloudOperationToContext(stage, output, LambdaStageConstants.updateCodeUrlKey);
    addToTaskContext(stage, LambdaStageConstants.lambaCodeUpdatedKey, Boolean.TRUE);
    return taskComplete(stage);
  }

  private LambdaCloudOperationOutput updateLambdaCode(StageExecution stage) {
    LambdaUpdateCodeInput inp = stage.mapTo(LambdaUpdateCodeInput.class);
    List<TaskExecution> taskExecutions = stage.getTasks();
    boolean deferPublish =
        taskExecutions.stream()
            .anyMatch(
                t ->
                    t.getName().equals("lambdaPublishVersionTask")
                        && t.getStatus().equals(ExecutionStatus.NOT_STARTED));

    inp.setPublish(inp.isPublish() && !deferPublish);
    log.debug("Publish flag for UpdateCodeTask is set to " + (inp.isPublish() && !deferPublish));

    inp.setAppName(stage.getExecution().getApplication());
    inp.setCredentials(inp.getAccount());

    OperationContext context = objectMapper.convertValue(inp, new TypeReference<>() {});
    context.setOperationType("updateLambdaFunctionCode");
    SubmitOperationResult result = katoService.submitOperation(getCloudProvider(), context);

    return LambdaCloudOperationOutput.builder()
        .resourceId(result.getId())
        .url(result.getResourceUri())
        .build();
  }

  @Nullable
  @Override
  public TaskResult onTimeout(@Nonnull StageExecution stage) {
    return TaskResult.builder(ExecutionStatus.SKIPPED).build();
  }
}
