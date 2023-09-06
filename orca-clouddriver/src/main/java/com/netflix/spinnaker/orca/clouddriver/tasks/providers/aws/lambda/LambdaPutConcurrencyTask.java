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
import org.pf4j.util.StringUtils;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LambdaPutConcurrencyTask implements LambdaStageBaseTask {

  private final KatoService katoService;
  private final ObjectMapper objectMapper;

  public LambdaPutConcurrencyTask(KatoService katoService, ObjectMapper objectMapper) {
    this.katoService = katoService;
    this.objectMapper = objectMapper;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    log.debug("Executing LambdaPutConcurrencyTask...");
    prepareTask(stage);

    LambdaConcurrencyInput inp = stage.mapTo(LambdaConcurrencyInput.class);
    inp.setAppName(stage.getExecution().getApplication());

    if ((inp.getReservedConcurrentExecutions() == null
            && Optional.ofNullable(inp.getProvisionedConcurrentExecutions()).orElse(0) == 0)
        || LambdaDeploymentStrategyEnum.$WEIGHTED
            .toString()
            .equals(stage.getContext().get("deploymentStrategy"))) {
      addToOutput(stage, "LambdaPutConcurrencyTask", "Lambda concurrency : nothing to update");
      return taskComplete(stage);
    }

    LambdaCloudOperationOutput output = putConcurrency(inp);
    addCloudOperationToContext(stage, output, LambdaStageConstants.putConcurrencyUrlKey);
    return taskComplete(stage);
  }

  private LambdaCloudOperationOutput putConcurrency(LambdaConcurrencyInput inp) {
    inp.setCredentials(inp.getAccount());
    if (inp.getProvisionedConcurrentExecutions() != null
        && inp.getProvisionedConcurrentExecutions() != 0
        && StringUtils.isNotNullOrEmpty(inp.getAliasName())) {
      return putProvisionedConcurrency(inp);
    }
    if (inp.getReservedConcurrentExecutions() != null) {
      return putReservedConcurrency(inp);
    }
    return LambdaCloudOperationOutput.builder().build();
  }

  private LambdaCloudOperationOutput putReservedConcurrency(LambdaConcurrencyInput inp) {
    OperationContext context = objectMapper.convertValue(inp, new TypeReference<>() {});
    context.setOperationType("putLambdaReservedConcurrency");
    SubmitOperationResult result = katoService.submitOperation(getCloudProvider(), context);

    return LambdaCloudOperationOutput.builder()
        .resourceId(result.getId())
        .url(result.getResourceUri())
        .build();
  }

  private LambdaCloudOperationOutput putProvisionedConcurrency(LambdaConcurrencyInput inp) {
    OperationContext context = objectMapper.convertValue(inp, new TypeReference<>() {});
    context.setOperationType("putLambdaProvisionedConcurrency");
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
