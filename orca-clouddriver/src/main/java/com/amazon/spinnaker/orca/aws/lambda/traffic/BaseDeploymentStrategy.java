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

import com.amazon.spinnaker.orca.aws.lambda.LambdaCloudOperationOutput;
import com.amazon.spinnaker.orca.aws.lambda.model.LambdaDefinition;
import com.amazon.spinnaker.orca.aws.lambda.traffic.model.LambdaBaseStrategyInput;
import com.amazon.spinnaker.orca.aws.lambda.traffic.model.LambdaDeploymentStrategyOutput;
import com.amazon.spinnaker.orca.aws.lambda.utils.LambdaCloudDriverResponse;
import com.amazon.spinnaker.orca.aws.lambda.utils.LambdaCloudDriverUtils;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class BaseDeploymentStrategy<T extends LambdaBaseStrategyInput> {
  private static final Logger logger = LoggerFactory.getLogger(BaseDeploymentStrategy.class);
  static String CLOUDDRIVER_UPSERT_ALIAS_PATH = "/aws/ops/upsertLambdaFunctionAlias";

  @Autowired protected LambdaCloudDriverUtils utils;

  public LambdaDeploymentStrategyOutput deploy(T inp) {
    throw new RuntimeException("Not Implemented");
  }

  public LambdaCloudOperationOutput postToCloudDriver(
      LambdaBaseStrategyInput inp, String cloudDriverUrl, LambdaCloudDriverUtils utils) {
    String endPoint = cloudDriverUrl + CLOUDDRIVER_UPSERT_ALIAS_PATH;
    String rawString = utils.asString(inp);
    LambdaCloudDriverResponse respObj = utils.postToCloudDriver(endPoint, rawString);
    String url = cloudDriverUrl + respObj.getResourceUri();
    logger.debug("Posted to cloudDriver for deployment: " + url);
    LambdaCloudOperationOutput out =
        LambdaCloudOperationOutput.builder().resourceId(respObj.getId()).url(url).build();
    return out;
  }

  public LambdaCloudDriverUtils getUtils() {
    return null;
  }
  ;

  public T setupInput(StageExecution stage) {
    throw new RuntimeException("Should be overridden. This class needs to be extract");
  }

  public String getVersion(StageExecution stage, String version, String versionNumberProvided) {
    if (version == null) {
      return null;
    }

    if (version.startsWith("$PROVIDED")) { // actual version number
      return versionNumberProvided;
    }

    LambdaDefinition lf = utils.findLambda(stage);
    return getUtils().getCanonicalVersion(lf, version, versionNumberProvided, 0);
  }
}
