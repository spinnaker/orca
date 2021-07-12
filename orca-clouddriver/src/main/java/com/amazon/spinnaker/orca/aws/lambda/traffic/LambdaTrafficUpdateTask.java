/*
 *
 *  * Copyright 2021 Amazon.com, Inc. or its affiliates.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License")
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *   http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

package com.amazon.spinnaker.orca.aws.lambda.traffic;

import com.amazon.spinnaker.orca.aws.lambda.CloudDriverProperties;
import com.amazon.spinnaker.orca.aws.lambda.LambdaStageBaseTask;
import com.amazon.spinnaker.orca.aws.lambda.traffic.model.LambdaBaseStrategyInput;
import com.amazon.spinnaker.orca.aws.lambda.traffic.model.LambdaDeploymentStrategyOutput;
import com.amazon.spinnaker.orca.aws.lambda.utils.LambdaCloudDriverUtils;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pf4j.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LambdaTrafficUpdateTask implements LambdaStageBaseTask {
  private static Logger logger = LoggerFactory.getLogger(LambdaTrafficUpdateTask.class);

  private String cloudDriverUrl;

  @Autowired TrafficUpdateStrategyInjector injector;

  @Autowired CloudDriverProperties props;

  @Autowired private LambdaCloudDriverUtils utils;

  @NotNull
  @Override
  public TaskResult execute(@NotNull StageExecution stage) {
    logger.debug("Executing LambdaTrafficUpdateTask...");
    cloudDriverUrl = props.getCloudDriverBaseUrl();
    prepareTask(stage);
    LambdaDeploymentStrategyOutput result = null;
    BaseDeploymentStrategy deploymentStrategy = getDeploymentStrategy(stage);
    List<String> validationErrors = new ArrayList<>();
    if (!validateInput(stage, validationErrors)) {
      logger.error("Validation failed for traffic update task");
      return this.formErrorListTaskResult(stage, validationErrors);
    }
    LambdaBaseStrategyInput input = deploymentStrategy.setupInput(stage);
    result = deploymentStrategy.deploy(input);
    if (!result.isSucceeded()) {
      return formErrorTaskResult(stage, result.getErrorMessage());
    }
    final StageExecution tmpStage = stage;
    result
        .getOutput()
        .getOutputMap()
        .forEach(
            (x, y) -> {
              addToTaskContext(tmpStage, (String) x, y);
            });

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

  private BaseDeploymentStrategy getDeploymentStrategy(StageExecution stage) {
    return injector.getStrategy(
        DeploymentStrategyEnum.valueOf((String) stage.getContext().get("deploymentStrategy")));
  }

  @Nullable
  @Override
  public TaskResult onTimeout(@NotNull StageExecution stage) {
    return null;
  }

  @Override
  public void onCancel(@NotNull StageExecution stage) {}
}
