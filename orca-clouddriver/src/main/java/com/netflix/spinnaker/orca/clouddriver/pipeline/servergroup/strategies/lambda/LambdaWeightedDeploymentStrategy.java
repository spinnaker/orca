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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.LambdaUtils;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaTrafficUpdateInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaWeightedStrategyInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.output.LambdaCloudOperationOutput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.output.LambdaDeploymentStrategyOutput;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class LambdaWeightedDeploymentStrategy
    extends BaseLambdaDeploymentStrategy<LambdaWeightedStrategyInput> {

  public LambdaWeightedDeploymentStrategy(
      LambdaUtils lambdaUtils, KatoService katoService, ObjectMapper objectMapper) {
    super(lambdaUtils, katoService, objectMapper);
  }

  @Override
  public LambdaDeploymentStrategyOutput deploy(LambdaWeightedStrategyInput inp) {
    Map<String, Object> outputMap = new HashMap<>();
    outputMap.put("deployment:majorVersionDeployed", inp.getMajorFunctionVersion());
    outputMap.put("deployment:minorVersionDeployed", inp.getMinorFunctionVersion());
    outputMap.put("deployment:aliasDeployed", inp.getAliasName());
    outputMap.put("deployment:strategyUsed", "WeightedDeploymentStrategy");

    // TODO: Form a new inputObject such as SimpleStrategyInput and just have the
    LambdaCloudOperationOutput out = updateAlias(inp);
    out.setOutputMap(outputMap);

    return LambdaDeploymentStrategyOutput.builder().succeeded(true).output(out).build();
  }

  @Override
  public LambdaWeightedStrategyInput setupInput(StageExecution stage) {
    LambdaTrafficUpdateInput aliasInp = stage.mapTo(LambdaTrafficUpdateInput.class);
    LambdaWeightedStrategyInput weightedInput = stage.mapTo(LambdaWeightedStrategyInput.class);
    weightedInput.setAppName(stage.getExecution().getApplication());
    weightedInput.setAccount(aliasInp.getAccount());
    weightedInput.setCredentials(aliasInp.getAccount());

    weightedInput.setWeightToMinorFunctionVersion(
        (double) (100 - aliasInp.getTrafficPercentA()) / 100);
    weightedInput.setMajorFunctionVersion(
        getVersion(stage, aliasInp.getVersionNameA(), aliasInp.getVersionNumberA()));
    weightedInput.setMinorFunctionVersion(
        getVersion(stage, aliasInp.getVersionNameB(), aliasInp.getVersionNumberB()));
    return weightedInput;
  }
}
