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
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaBlueGreenStrategyInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaTrafficUpdateInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.output.LambdaCloudOperationOutput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.output.LambdaDeploymentStrategyOutput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.output.LambdaInvokeFunctionOutput;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LambdaBlueGreenDeploymentStrategy
    extends BaseLambdaDeploymentStrategy<LambdaBlueGreenStrategyInput> {

  public LambdaBlueGreenDeploymentStrategy(
      LambdaUtils utils, KatoService katoService, ObjectMapper objectMapper) {
    super(utils, katoService, objectMapper);
  }

  @Override
  public LambdaDeploymentStrategyOutput deploy(LambdaBlueGreenStrategyInput inp) {
    LambdaInvokeFunctionOutput invokeOutput = invokeLambdaFunction(inp);
    Pair<Boolean, String> results = verifyResults(inp, invokeOutput);
    if (results.getLeft()) {
      return updateLambdaToLatest(inp);
    } else {
      LambdaCloudOperationOutput cloudOperationOutput =
          LambdaCloudOperationOutput.builder().build();
      LambdaDeploymentStrategyOutput deploymentStrategyOutput =
          LambdaDeploymentStrategyOutput.builder()
              .succeeded(false)
              .errorMessage(results.getRight())
              .output(cloudOperationOutput)
              .build();
      log.error("BlueGreen Deployment failed: " + results.getRight());
      return deploymentStrategyOutput;
    }
  }

  private Pair<Boolean, String> verifyResults(
      LambdaBlueGreenStrategyInput inp, LambdaInvokeFunctionOutput output) {
    int timeout = inp.getTimeout() * 1000;
    int sleepTime = 10000;

    String taskUri = output.getUrl();

    LambdaCloudDriverTaskResults taskResult = null;
    boolean done = false;
    while (timeout > 0) {
      taskResult = lambdaUtils.verifyStatus(taskUri);
      if (taskResult.getStatus().isCompleted()) {
        done = true;
        break;
      }
      try {
        lambdaUtils.await();
        timeout -= sleepTime;
      } catch (Throwable e) {
        log.error("Error waiting for blue green test to complete");
      }
    }

    if (!done) return Pair.of(Boolean.FALSE, "Lambda Invocation did not finish on time");

    if (taskResult.getStatus().isFailed()) {
      return Pair.of(Boolean.FALSE, "Lambda Invocation returned failure");
    }

    LambdaCloudDriverInvokeOperationResults invokeResponse =
        lambdaUtils.getLambdaInvokeResults(taskUri);
    String expected =
        lambdaUtils
            .getPipelinesArtifactContent(inp.getOutputArtifact())
            .replaceAll("[\\n\\t ]", "");
    String actual = null;
    if (invokeResponse != null) {
      if (invokeResponse.getBody() != null) {
        actual = invokeResponse.getBody().replaceAll("[\\n\\t ]", "");
      } else if (invokeResponse.getResponseString() != null) {
        actual = invokeResponse.getResponseString().replaceAll("[\\n\\t ]", "");
      }
    }
    boolean comparison = ObjectUtils.defaultIfNull(expected, "").equals(actual);
    if (!comparison) {
      String err =
          String.format(
              "BlueGreenDeployment failed: Comparison failed. expected : [%s], actual : [%s]",
              expected, actual);
      log.error("Response string: {}", invokeResponse.getResponseString());
      String errMsg = String.format("%s \n %s", err, invokeResponse.getErrorMessage());
      log.error("Log results: {}", invokeResponse.getInvokeResult().getLogResult());
      log.error(err);
      return Pair.of(Boolean.FALSE, errMsg);
    }
    return Pair.of(Boolean.TRUE, "");
  }

  private LambdaDeploymentStrategyOutput updateLambdaToLatest(LambdaBlueGreenStrategyInput inp) {
    inp.setWeightToMinorFunctionVersion(0.0);
    inp.setMajorFunctionVersion(inp.getLatestVersionQualifier());
    inp.setMinorFunctionVersion(null);

    Map<String, Object> outputMap = new HashMap<>();
    outputMap.put("deployment:majorVersionDeployed", inp.getMajorFunctionVersion());
    outputMap.put("deployment:aliasDeployed", inp.getAliasName());
    outputMap.put("deployment:strategyUsed", "BlueGreenDeploymentStrategy");

    LambdaCloudOperationOutput out = updateAlias(inp);
    out.setOutputMap(outputMap);

    return LambdaDeploymentStrategyOutput.builder().succeeded(true).output(out).build();
  }

  @Override
  public LambdaBlueGreenStrategyInput setupInput(StageExecution stage) {
    LambdaTrafficUpdateInput aliasInp = stage.mapTo(LambdaTrafficUpdateInput.class);
    LambdaBlueGreenStrategyInput blueGreenInput = stage.mapTo(LambdaBlueGreenStrategyInput.class);
    aliasInp.setAppName(stage.getExecution().getApplication());

    blueGreenInput.setCredentials(aliasInp.getAccount());
    blueGreenInput.setAppName(stage.getExecution().getApplication());

    blueGreenInput.setPayloadArtifact(aliasInp.getPayloadArtifact().getArtifact());
    blueGreenInput.setOutputArtifact(aliasInp.getOutputArtifact().getArtifact());

    LambdaDefinition lf = lambdaUtils.retrieveLambdaFromCache(stage);

    String qual = lambdaUtils.getCanonicalVersion(lf, "$LATEST", "", 1);
    blueGreenInput.setQualifier(qual);
    String latestVersion = this.getVersion(stage, "$LATEST", "");
    blueGreenInput.setLatestVersionQualifier(latestVersion);

    return blueGreenInput;
  }

  private LambdaInvokeFunctionOutput invokeLambdaFunction(LambdaBlueGreenStrategyInput ldi) {
    OperationContext context = objectMapper.convertValue(ldi, new TypeReference<>() {});
    context.setOperationType("invokeLambdaFunction");
    SubmitOperationResult result = katoService.submitOperation("aws", context);

    return LambdaInvokeFunctionOutput.builder().url(result.getResourceUri()).build();
  }
}
