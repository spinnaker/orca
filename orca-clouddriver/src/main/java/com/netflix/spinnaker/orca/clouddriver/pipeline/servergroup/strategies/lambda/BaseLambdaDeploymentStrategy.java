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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.strategies.lambda;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.OperationContext;
import com.netflix.spinnaker.orca.clouddriver.model.SubmitOperationResult;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.LambdaUtils;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.*;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaBaseStrategyInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.output.LambdaCloudOperationOutput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.output.LambdaDeploymentStrategyOutput;

public class BaseLambdaDeploymentStrategy<T extends LambdaBaseStrategyInput> {
  private static final String CLOUD_PROVIDER = "aws";

  protected final LambdaUtils lambdaUtils;
  protected final KatoService katoService;
  protected final ObjectMapper objectMapper;

  public BaseLambdaDeploymentStrategy(
      LambdaUtils lambdaUtils, KatoService katoService, ObjectMapper objectMapper) {
    this.lambdaUtils = lambdaUtils;
    this.katoService = katoService;
    this.objectMapper = objectMapper;
  }

  public LambdaDeploymentStrategyOutput deploy(T inp) {
    throw new RuntimeException("Not Implemented");
  }

  public LambdaCloudOperationOutput updateAlias(LambdaBaseStrategyInput inp) {
    OperationContext context = objectMapper.convertValue(inp, new TypeReference<>() {});
    context.setOperationType("upsertLambdaFunctionAlias");
    SubmitOperationResult result = katoService.submitOperation(CLOUD_PROVIDER, context);

    return LambdaCloudOperationOutput.builder()
        .resourceId(result.getId())
        .url(result.getResourceUri())
        .build();
  }

  public LambdaUtils getUtils() {
    return lambdaUtils;
  }

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

    LambdaDefinition lf = lambdaUtils.retrieveLambdaFromCache(stage);
    return lambdaUtils.getCanonicalVersion(lf, version, versionNumberProvided, 0);
  }
}
