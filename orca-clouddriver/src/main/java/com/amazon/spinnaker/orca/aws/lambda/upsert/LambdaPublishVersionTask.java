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
import com.amazon.spinnaker.orca.aws.lambda.model.LambdaDefinition;
import com.amazon.spinnaker.orca.aws.lambda.upsert.model.LambdaPublisVersionInput;
import com.amazon.spinnaker.orca.aws.lambda.utils.LambdaCloudDriverResponse;
import com.amazon.spinnaker.orca.aws.lambda.utils.LambdaCloudDriverUtils;
import com.amazon.spinnaker.orca.aws.lambda.utils.LambdaGetInput;
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
public class LambdaPublishVersionTask implements LambdaStageBaseTask {
  private static Logger logger = LoggerFactory.getLogger(LambdaPublishVersionTask.class);
  private static final ObjectMapper objMapper = new ObjectMapper();
  private static final String CLOUDDRIVER_PUBLISH_VERSION_PATH =
      "/aws/ops/publishLambdaFunctionVersion";

  @Autowired CloudDriverProperties props;
  private String cloudDriverUrl;

  @Autowired private LambdaCloudDriverUtils utils;

  @NotNull
  @Override
  public TaskResult execute(@NotNull StageExecution stage) {
    logger.debug("Executing LambdaPublishVersionTask...");
    cloudDriverUrl = props.getCloudDriverBaseUrl();
    prepareTask(stage);
    if (!requiresVersionPublish(stage)) {
      addToOutput(stage, LambdaStageConstants.lambaVersionPublishedKey, Boolean.FALSE);
      return taskComplete(stage);
    }
    LambdaCloudOperationOutput output = this.publishVersion(stage);
    addCloudOperationToContext(stage, output, LambdaStageConstants.publishVersionUrlKey);
    this.addToTaskContext(stage, LambdaStageConstants.lambaVersionPublishedKey, Boolean.TRUE);
    return taskComplete(stage);
  }

  private boolean requiresVersionPublish(StageExecution stage) {
    Boolean justCreated =
        (Boolean)
            stage.getContext().getOrDefault(LambdaStageConstants.lambaCreatedKey, Boolean.FALSE);
    if (justCreated) return false;
    Boolean requiresPublishFlag =
        (Boolean) stage.getContext().getOrDefault("publish", Boolean.FALSE);
    if (!requiresPublishFlag) return false;
    LambdaGetInput lgi = utils.getInput(stage, LambdaGetInput.class);
    lgi.setAppName(stage.getExecution().getApplication());
    LambdaDefinition lf = utils.retrieveLambda(lgi);
    String newRevisionId = lf.getRevisionId();
    String origRevisionId =
        (String) stage.getContext().get(LambdaStageConstants.originalRevisionIdKey);
    stage.getContext().put(LambdaStageConstants.newRevisionIdKey, newRevisionId);
    return !newRevisionId.equals(origRevisionId);
  }

  private LambdaCloudOperationOutput publishVersion(StageExecution stage) {
    LambdaPublisVersionInput inp = utils.getInput(stage, LambdaPublisVersionInput.class);
    inp.setAppName(stage.getExecution().getApplication());
    inp.setCredentials(inp.getAccount());
    String rawString = utils.asString(inp);
    String endPoint = cloudDriverUrl + CLOUDDRIVER_PUBLISH_VERSION_PATH;
    String revisionId = (String) stage.getContext().get(LambdaStageConstants.newRevisionIdKey);
    inp.setRevisionId(revisionId);
    LambdaCloudDriverResponse respObj = utils.postToCloudDriver(endPoint, rawString);
    String url = cloudDriverUrl + respObj.getResourceUri();
    logger.debug("Posted to cloudDriver for publishVersion: " + url);
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
