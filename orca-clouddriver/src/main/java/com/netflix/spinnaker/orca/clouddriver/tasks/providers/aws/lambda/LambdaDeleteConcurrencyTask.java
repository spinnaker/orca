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
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.lambda.LambdaDeploymentStrategyEnum;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaConcurrencyInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.output.LambdaCloudOperationOutput;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LambdaDeleteConcurrencyTask implements LambdaStageBaseTask {

  private final KatoService katoService;
  private final ObjectMapper objectMapper;

  public LambdaDeleteConcurrencyTask(KatoService katoService, ObjectMapper objectMapper) {
    this.katoService = katoService;
    this.objectMapper = objectMapper;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    log.debug("Executing LambdaDeleteConcurrencyTask...");
    prepareTask(stage);

    LambdaConcurrencyInput inp = stage.mapTo(LambdaConcurrencyInput.class);
    inp.setAppName(stage.getExecution().getApplication());

    LambdaCloudOperationOutput output;
    if (stage.getType().equals("Aws.LambdaDeploymentStage")
        && inp.getReservedConcurrentExecutions() == null) {
      output = deleteReservedConcurrency(inp);
    } else if (stage.getType().equals("Aws.LambdaTrafficRoutingStage")
        && Optional.ofNullable(inp.getProvisionedConcurrentExecutions()).orElse(0) == 0
        && !LambdaDeploymentStrategyEnum.$WEIGHTED
            .toString()
            .equals(stage.getContext().get("deploymentStrategy"))) {
      output = deleteProvisionedConcurrency(inp);
    } else {
      addToOutput(
          stage, "LambdaDeleteConcurrencyTask", "Lambda delete concurrency : nothing to delete");
      return taskComplete(stage);
    }
    addCloudOperationToContext(stage, output, LambdaStageConstants.deleteConcurrencyUrlKey);
    return taskComplete(stage);
  }

  private LambdaCloudOperationOutput deleteProvisionedConcurrency(LambdaConcurrencyInput inp) {
    inp.setQualifier(inp.getAliasName());

    OperationContext context = objectMapper.convertValue(inp, new TypeReference<>() {});
    context.setOperationType("deleteLambdaProvisionedConcurrency");
    SubmitOperationResult result = katoService.submitOperation(getCloudProvider(), context);

    return LambdaCloudOperationOutput.builder()
        .resourceId(result.getId())
        .url(result.getResourceUri())
        .build();
  }

  private LambdaCloudOperationOutput deleteReservedConcurrency(LambdaConcurrencyInput inp) {
    OperationContext context = objectMapper.convertValue(inp, new TypeReference<>() {});
    context.setOperationType("deleteLambdaReservedConcurrency");
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
