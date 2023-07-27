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
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.OperationContext;
import com.netflix.spinnaker.orca.clouddriver.model.SubmitOperationResult;
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.lambda.LambdaStageConstants;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.LambdaUtils;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaDeploymentInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.output.LambdaCloudOperationOutput;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LambdaUpdateConfigurationTask implements LambdaStageBaseTask {

  private final LambdaUtils utils;
  private final KatoService katoService;
  private final ObjectMapper objectMapper;

  public LambdaUpdateConfigurationTask(
      LambdaUtils utils, KatoService katoService, ObjectMapper objectMapper) {
    this.utils = utils;
    this.katoService = katoService;
    this.objectMapper = objectMapper;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    log.debug("Executing LambdaUpdateConfigurationTask...");
    prepareTask(stage);

    Boolean justCreated =
        (Boolean)
            stage.getContext().getOrDefault(LambdaStageConstants.lambaCreatedKey, Boolean.FALSE);
    if (justCreated) {
      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(stage.getContext()).build();
    }

    List<String> errors = new ArrayList<>();
    LambdaDeploymentInput ldi = stage.mapTo(LambdaDeploymentInput.class);
    if (!utils.validateUpsertLambdaInput(ldi, errors)) {
      return this.formErrorListTaskResult(stage, errors);
    }

    LambdaCloudOperationOutput output = this.updateLambdaConfig(stage, ldi);
    addCloudOperationToContext(stage, output, LambdaStageConstants.updateConfigUrlKey);
    addToTaskContext(stage, LambdaStageConstants.lambaConfigurationUpdatedKey, Boolean.TRUE);
    return taskComplete(stage);
  }

  private LambdaCloudOperationOutput updateLambdaConfig(
      StageExecution stage, LambdaDeploymentInput ldi) {
    ldi.setAppName(stage.getExecution().getApplication());
    ldi.setCredentials(ldi.getAccount());

    OperationContext context = objectMapper.convertValue(ldi, new TypeReference<>() {});
    context.setOperationType("updateLambdaFunctionConfiguration");
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
