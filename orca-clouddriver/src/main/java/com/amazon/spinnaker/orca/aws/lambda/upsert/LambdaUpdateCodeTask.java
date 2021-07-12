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

package com.amazon.spinnaker.orca.aws.lambda.upsert;

import com.amazon.spinnaker.orca.aws.lambda.CloudDriverProperties;
import com.amazon.spinnaker.orca.aws.lambda.LambdaCloudOperationOutput;
import com.amazon.spinnaker.orca.aws.lambda.LambdaStageBaseTask;
import com.amazon.spinnaker.orca.aws.lambda.upsert.model.LambdaUpdateCodeInput;
import com.amazon.spinnaker.orca.aws.lambda.utils.LambdaCloudDriverResponse;
import com.amazon.spinnaker.orca.aws.lambda.utils.LambdaCloudDriverUtils;
import com.amazon.spinnaker.orca.aws.lambda.utils.LambdaStageConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LambdaUpdateCodeTask implements LambdaStageBaseTask {
  private static Logger logger = LoggerFactory.getLogger(LambdaUpdateCodeTask.class);
  private static final ObjectMapper objMapper = new ObjectMapper();
  private static String CLOUDDRIVER_CREATE_PATH = "/aws/ops/createLambdaFunction";
  private static String CLOUDDRIVER_UPDATE_CODE_PATH = "/aws/ops/updateLambdaFunctionCode";
  private static String CLOUDDRIVER_UPDATE_CONFIG_PATH =
      "/aws/ops/updateLambdaFunctionConfiguration";

  @Autowired CloudDriverProperties props;
  private String cloudDriverUrl;

  @Autowired private LambdaCloudDriverUtils utils;

  @NotNull
  @Override
  public TaskResult execute(@NotNull StageExecution stage) {
    logger.debug("Executing LambdaUpdateCodeTask...");
    cloudDriverUrl = props.getCloudDriverBaseUrl();
    prepareTask(stage);
    Boolean justCreated =
        (Boolean)
            stage.getContext().getOrDefault(LambdaStageConstants.lambaCreatedKey, Boolean.FALSE);
    if (justCreated) {
      return taskComplete(stage);
    }
    LambdaCloudOperationOutput output = updateLambdaCode(stage);
    addCloudOperationToContext(stage, output, LambdaStageConstants.updateCodeUrlKey);
    addToTaskContext(stage, LambdaStageConstants.lambaCodeUpdatedKey, Boolean.TRUE);
    return taskComplete(stage);
  }

  private LambdaCloudOperationOutput updateLambdaCode(StageExecution stage) {
    LambdaUpdateCodeInput inp = utils.getInput(stage, LambdaUpdateCodeInput.class);
    inp.setAppName(stage.getExecution().getApplication());
    inp.setCredentials(inp.getAccount());
    String endPoint = cloudDriverUrl + CLOUDDRIVER_UPDATE_CODE_PATH;
    String rawString = utils.asString(inp);
    LambdaCloudDriverResponse respObj = utils.postToCloudDriver(endPoint, rawString);
    String url = cloudDriverUrl + respObj.getResourceUri();
    logger.debug("Posted to cloudDriver for updateLambdaCode: " + url);
    LambdaCloudOperationOutput operationOutput =
        LambdaCloudOperationOutput.builder().resourceId(respObj.getId()).url(url).build();
    return operationOutput;
  }

  @Nullable
  @Override
  public TaskResult onTimeout(@NotNull StageExecution stage) {
    return TaskResult.builder(ExecutionStatus.SKIPPED).build();
  }

  @Override
  public void onCancel(@NotNull StageExecution stage) {}
}
