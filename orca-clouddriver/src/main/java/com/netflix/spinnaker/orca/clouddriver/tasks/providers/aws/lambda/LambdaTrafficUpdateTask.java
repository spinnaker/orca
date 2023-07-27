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

import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.lambda.BaseLambdaDeploymentStrategy;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.lambda.LambdaDeploymentStrategyEnum;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.lambda.LambdaTrafficUpdateStrategyInjector;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaBaseStrategyInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.output.LambdaDeploymentStrategyOutput;
import java.util.*;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.util.StringUtils;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LambdaTrafficUpdateTask implements LambdaStageBaseTask {

  private final LambdaTrafficUpdateStrategyInjector injector;

  public LambdaTrafficUpdateTask(LambdaTrafficUpdateStrategyInjector injector) {
    this.injector = injector;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    log.debug("Executing LambdaTrafficUpdateTask...");
    prepareTask(stage);

    LambdaDeploymentStrategyOutput result;
    BaseLambdaDeploymentStrategy deploymentStrategy = getDeploymentStrategy(stage);

    List<String> validationErrors = new ArrayList<>();
    if (!validateInput(stage, validationErrors)) {
      log.error("Validation failed for traffic update task");
      return formErrorListTaskResult(stage, validationErrors);
    }

    LambdaBaseStrategyInput input = deploymentStrategy.setupInput(stage);
    result = deploymentStrategy.deploy(input);
    if (!result.isSucceeded()) {
      return formErrorTaskResult(stage, result.getErrorMessage());
    }

    final StageExecution tmpStage = stage;
    result.getOutput().getOutputMap().forEach((x, y) -> addToTaskContext(tmpStage, x, y));

    addCloudOperationToContext(stage, result.getOutput(), "url");
    return taskComplete(stage);
  }

  @Override
  public boolean validateInput(StageExecution stage, List<String> errors) {
    boolean exists = stage.getContext().containsKey("aliasName");
    if (!exists) {
      errors.add("Traffic Update requires aliasName field");
      return false;
    }
    String xx = (String) stage.getContext().get("aliasName");
    if (StringUtils.isNullOrEmpty(xx)) {
      errors.add("Traffic Update requires aliasName field");
      return false;
    }
    return true;
  }

  private BaseLambdaDeploymentStrategy getDeploymentStrategy(StageExecution stage) {
    return injector.getStrategy(
        LambdaDeploymentStrategyEnum.valueOf(
            (String) stage.getContext().get("deploymentStrategy")));
  }

  @Nullable
  @Override
  public TaskResult onTimeout(@Nonnull StageExecution stage) {
    return null;
  }
}
