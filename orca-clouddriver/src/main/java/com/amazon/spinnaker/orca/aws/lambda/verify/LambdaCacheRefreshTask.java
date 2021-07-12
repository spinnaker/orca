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

package com.amazon.spinnaker.orca.aws.lambda.verify;

import com.amazon.spinnaker.orca.aws.lambda.CloudDriverProperties;
import com.amazon.spinnaker.orca.aws.lambda.LambdaCloudOperationOutput;
import com.amazon.spinnaker.orca.aws.lambda.LambdaStageBaseTask;
import com.amazon.spinnaker.orca.aws.lambda.utils.LambdaCloudDriverResponse;
import com.amazon.spinnaker.orca.aws.lambda.utils.LambdaCloudDriverUtils;
import com.amazon.spinnaker.orca.aws.lambda.verify.model.LambdaCacheRefreshInput;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LambdaCacheRefreshTask implements LambdaStageBaseTask {
  private static Logger logger = LoggerFactory.getLogger(LambdaCacheRefreshTask.class);
  private static String CLOUDDRIVER_REFRESH_CACHE_PATH = "/cache/aws/function";

  @Autowired CloudDriverProperties props;
  private String cloudDriverUrl;

  @Autowired private LambdaCloudDriverUtils utils;

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    logger.debug("Executing LambdaCacheRefreshTask...");
    cloudDriverUrl = props.getCloudDriverBaseUrl();
    prepareTask(stage);
    LambdaCloudOperationOutput output = forceCacheRefresh(stage);
    logger.debug("Going to wait for some seconds after requesting cache refresh...");
    utils.await();
    return taskComplete(stage);
  }

  private LambdaCloudOperationOutput forceCacheRefresh(StageExecution stage) {
    LambdaCacheRefreshInput inp = utils.getInput(stage, LambdaCacheRefreshInput.class);
    inp.setAppName(stage.getExecution().getApplication());
    inp.setCredentials(inp.getAccount());
    String endPoint = cloudDriverUrl + CLOUDDRIVER_REFRESH_CACHE_PATH;
    String rawString = utils.asString(inp);
    LambdaCloudDriverResponse respObj = utils.postToCloudDriver(endPoint, rawString);
    String url = cloudDriverUrl + respObj.getResourceUri();
    logger.debug("Posted to cloudDriver for cache refresh: " + url);
    LambdaCloudOperationOutput operationOutput =
        LambdaCloudOperationOutput.builder().resourceId(respObj.getId()).url(url).build();
    return operationOutput;
  }
}
