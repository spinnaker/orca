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
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.*;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaDeploymentInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.output.LambdaCloudOperationOutput;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LambdaCreateTask implements CloudProviderAware, LambdaStageBaseTask {

  private final LambdaUtils utils;
  private final KatoService katoService;
  private final ObjectMapper objectMapper;

  public LambdaCreateTask(
      LambdaUtils lambdaUtils, KatoService katoService, ObjectMapper objectMapper) {
    this.utils = lambdaUtils;
    this.katoService = katoService;
    this.objectMapper = objectMapper;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    log.debug("Executing LambdaDeploymentTask...");
    prepareTask(stage);
    LambdaDeploymentInput ldi = stage.mapTo(LambdaDeploymentInput.class);

    List<String> errors = new ArrayList<>();
    if (!utils.validateUpsertLambdaInput(ldi, errors)) {
      return formErrorListTaskResult(stage, errors);
    }

    ldi.setAppName(stage.getExecution().getApplication());
    LambdaDefinition lambdaDefinition = utils.retrieveLambdaFromCache(stage);

    if (lambdaDefinition != null) {
      log.debug("noOp. Lambda already exists. only needs updating.");
      addToTaskContext(stage, LambdaStageConstants.lambaCreatedKey, Boolean.FALSE);
      addToTaskContext(stage, LambdaStageConstants.lambdaObjectKey, lambdaDefinition);
      addToTaskContext(
          stage, LambdaStageConstants.originalRevisionIdKey, lambdaDefinition.getRevisionId());
      addToTaskContext(stage, LambdaStageConstants.lambaCreatedKey, Boolean.FALSE);

      addToOutput(stage, LambdaStageConstants.lambaCreatedKey, Boolean.FALSE);
      addToOutput(
          stage, LambdaStageConstants.originalRevisionIdKey, lambdaDefinition.getRevisionId());
      return taskComplete(stage);
    }

    addToOutput(stage, LambdaStageConstants.lambaCreatedKey, Boolean.TRUE);
    addToTaskContext(stage, LambdaStageConstants.lambaCreatedKey, Boolean.TRUE);
    LambdaCloudOperationOutput output = createLambda(stage);
    addCloudOperationToContext(stage, output, LambdaStageConstants.createdUrlKey);
    return taskComplete(stage);
  }

  private LambdaCloudOperationOutput createLambda(StageExecution stage) {
    LambdaDeploymentInput ldi = stage.mapTo(LambdaDeploymentInput.class);
    ldi.setAppName(stage.getExecution().getApplication());
    ldi.setCredentials(ldi.getAccount());

    OperationContext context = objectMapper.convertValue(ldi, new TypeReference<>() {});
    context.setOperationType("createLambdaFunction");
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
