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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.CharStreams;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.model.Task;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.*;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaDeploymentInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaGetInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.output.LambdaVerificationStatusOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.pf4j.util.StringUtils;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

@Component
@Slf4j
public class LambdaUtils {

  private final KatoService katoService;
  private final OortService oortService;
  private final ObjectMapper objectMapper;
  private final RetrySupport retrySupport = new RetrySupport();

  public LambdaUtils(KatoService katoService, OortService oortService) {
    this.katoService = katoService;
    this.oortService = oortService;

    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    objectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
    this.objectMapper = objectMapper;
  }

  public LambdaCloudDriverInvokeOperationResults getLambdaInvokeResults(String uri) {
    Task task = katoService.lookupTaskWithUri(uri);
    LambdaCloudDriverInvokeOperationResults respObject = null;
    try {
      if (task != null && task.getResultObjects() != null && task.getResultObjects().size() > 0) {
        respObject =
            objectMapper.convertValue(
                task.getResultObjects().get(0), LambdaCloudDriverInvokeOperationResults.class);
        JsonNode respStringNode = objectMapper.readTree(respObject.getResponseString());
        if (respStringNode.has("statusCode")) {
          int statusCode = respStringNode.get("statusCode").intValue();
          respObject.setStatusCode(statusCode);
        }
        if (respStringNode.has("body")) {
          String body = respStringNode.get("body").textValue();
          respObject.setBody(body);
        } else if (respStringNode.has("payload")) {
          String body = respStringNode.get("payload").textValue();
          respObject.setBody(body);
        }
        if (respStringNode.has("errorMessage")) {
          String errorMessage = respStringNode.get("errorMessage").textValue();
          respObject.setErrorMessage(errorMessage);
          respObject.setHasErrors(true);
        } else {
          respObject.setHasErrors(false);
        }
      }
      return respObject;
    } catch (Exception e) {
      log.error("Failed getLambdaInvokeResults task at {}", uri, e);
      return respObject;
    }
  }

  public String getPublishedVersion(String uri) {
    try {
      Task task = katoService.lookupTaskWithUri(uri);
      List<Map> resultObjects = task.getResultObjects();
      if (resultObjects != null && resultObjects.size() > 0) {
        Map result = resultObjects.get(0);
        if (result.containsKey("version")) {
          return result.get("version").toString();
        }
      }
    } catch (Exception e) {
      log.error("Failed getPublishedVersion task at {}", uri, e);
    }
    return "$LATEST";
  }

  public LambdaCloudDriverTaskResults verifyStatus(String uri) {
    Task task = katoService.lookupTaskWithUri(uri);
    try {
      Task.Status status = task.getStatus();
      LambdaVerificationStatusOutput st =
          objectMapper.convertValue(status, LambdaVerificationStatusOutput.class);

      List<Map> resultObjects = task.getResultObjects();
      LambdaCloudDriverResultObject ro = null;
      LambdaCloudDriverErrorObject err = null;
      if (resultObjects != null && resultObjects.size() > 0) {
        ro = objectMapper.convertValue(resultObjects.get(0), LambdaCloudDriverResultObject.class);
        err = objectMapper.convertValue(resultObjects.get(0), LambdaCloudDriverErrorObject.class);
      }

      return LambdaCloudDriverTaskResults.builder().results(ro).status(st).errors(err).build();
    } catch (Exception e) {
      log.error("Failed verifying task at {}", uri, e);
      throw new RuntimeException(e);
    }
  }

  public LambdaDefinition retrieveLambdaFromCache(LambdaGetInput input, String appName) {
    String appPrefix = String.format("%s-", appName);
    String functionName = input.getFunctionName();

    // todo(mattg) why do this
    if (!functionName.startsWith(appPrefix)) {
      functionName = String.format("%s-%s", appName, functionName);
    }

    String finalFunctionName = functionName;
    List<LambdaDefinition> definitions =
        retrySupport.retry(
            () -> oortService.getFunction(input.getAccount(), input.getRegion(), finalFunctionName),
            5,
            Duration.ofMillis(500),
            true);

    return definitions.size() > 0 ? definitions.get(0) : null;
  }

  public LambdaDefinition retrieveLambdaFromCache(StageExecution stage) {
    LambdaGetInput lgi = stage.mapTo(LambdaGetInput.class);
    return retrieveLambdaFromCache(lgi, stage.getExecution().getApplication());
  }

  public String getCanonicalVersion(
      LambdaDefinition lf, String inputVersion, String versionNumber, int retentionNumber) {
    List<String> revisions = getSortedRevisions(lf);
    if (revisions.size() != 0) {
      if (inputVersion.startsWith("$PROVIDED")) { // actual version
        return versionNumber;
      }

      if (inputVersion.startsWith("$LATEST")) { // latest version number
        return revisions.get(0);
      }

      if (inputVersion.startsWith("$OLDEST")) { // oldest version number
        return revisions.get(revisions.size() - 1);
      }

      if (inputVersion.startsWith("$PREVIOUS")) { // latest - 1 version number
        if (revisions.size() >= 2) return revisions.get(1);
        else return null;
      }

      if (inputVersion.startsWith("$MOVING")) { // list of versions
        if (revisions.size() > retentionNumber) {
          List<String> toRemoveList = revisions.subList(retentionNumber, revisions.size());
          return String.join(",", toRemoveList);
        }
      }
      // Couldnt find it.
      log.error(String.format("Found invalid version string %s", inputVersion));
      return null;
    }
    log.error("No published versions exist for function.");
    return null;
  }

  public List<String> getSortedRevisions(LambdaDefinition lf) {
    List<String> revisions = new ArrayList<>(lf.getRevisions().values());
    List<Integer> revInt =
        revisions.stream()
            .filter(NumberUtils::isCreatable)
            .map(Integer::valueOf)
            .collect(Collectors.toList());
    revInt = revInt.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList());
    return revInt.stream().map(x -> Integer.toString(x)).collect(Collectors.toList());
  }

  public boolean validateUpsertLambdaInput(
      LambdaDeploymentInput inputLambda, List<String> errorMessages) {
    if (validateBasicLambdaDeploymentInput(inputLambda, errorMessages)) {
      if (ObjectUtils.defaultIfNull(inputLambda.getEnableLambdaAtEdge(), Boolean.FALSE)) {
        return validateLambdaEdgeInput(inputLambda, errorMessages);
      }
      return true;
    }
    return false;
  }

  public boolean validateBasicLambdaDeploymentInput(
      LambdaDeploymentInput inputLambda, List<String> errorMessages) {
    int numErrors = errorMessages.size();

    if (StringUtils.isNullOrEmpty(inputLambda.getAccount())) {
      errorMessages.add("Account is required");
    }
    if (StringUtils.isNullOrEmpty(inputLambda.getRegion())) {
      errorMessages.add("Region is required");
    }
    if (StringUtils.isNullOrEmpty(inputLambda.getFunctionName())) {
      errorMessages.add("Function Name is required");
    }
    if (StringUtils.isNullOrEmpty(inputLambda.getRuntime())) {
      errorMessages.add("Runtime is required");
    }
    if (StringUtils.isNullOrEmpty(inputLambda.getS3bucket())) {
      errorMessages.add("S3 Bucket is required");
    }
    if (StringUtils.isNullOrEmpty(inputLambda.getS3key())) {
      errorMessages.add("S3 Key is required");
    }
    if (StringUtils.isNullOrEmpty(inputLambda.getHandler())) {
      errorMessages.add("Handler is required");
    }
    if (StringUtils.isNullOrEmpty(inputLambda.getRole())) {
      errorMessages.add("Role ARN is required");
    }
    return errorMessages.size() == numErrors;
  }

  public boolean validateLambdaEdgeInput(
      LambdaDeploymentInput inputLambda, List<String> errorMessages) {
    int numErrors = errorMessages.size();

    if (inputLambda.getEnvVariables() == null || inputLambda.getEnvVariables().size() > 0) {
      errorMessages.add("Edge enabled lambdas cannot have env variables");
    }
    if (inputLambda.getTimeout() > 5) {
      errorMessages.add("Edge enabled lambdas cannot have timeout > 5");
    }
    if (inputLambda.getMemorySize() > 128) {
      errorMessages.add("Edge enabled lambdas cannot have memory > 128");
    }
    if (!inputLambda.getRegion().equals("us-east-1")) {
      errorMessages.add("Edge enabled lambdas need to be deployed in us-east-1 region");
    }
    if (StringUtils.isNotNullOrEmpty(inputLambda.getVpcId())) {
      errorMessages.add("Edge enabled lambdas cannot have vpc associations");
    }
    if (inputLambda.getSubnetIds() == null || inputLambda.getSubnetIds().size() > 0) {
      errorMessages.add("Edge enabled lambdas cannot have subnets");
    }
    if (inputLambda.getSecurityGroupIds() == null || inputLambda.getSecurityGroupIds().size() > 0) {
      errorMessages.add("Edge enabled lambdas cannot have security groups");
    }
    return errorMessages.size() == numErrors;
  }

  public void await() {
    await(20000);
  }

  public void await(long duration) {
    try {
      log.debug("Going to sleep during lambda");
      Thread.sleep(duration);
    } catch (Throwable e) {
      log.error("Error during await of lambda", e);
    }
  }

  private Artifact resolvePipelineArtifact(LambdaPipelineArtifact artifact) {
    return Artifact.builder()
        .uuid(artifact.getId())
        .artifactAccount(artifact.getArtifactAccount())
        .type(artifact.getType())
        .reference(artifact.getReference())
        .version(artifact.getVersion())
        .name(artifact.getName())
        .build();
  }

  public String getPipelinesArtifactContent(LambdaPipelineArtifact pipelineArtifact) {
    return retrySupport.retry(
        () -> {
          Response response = oortService.fetchArtifact(resolvePipelineArtifact(pipelineArtifact));
          InputStream artifactInputStream;
          try {
            artifactInputStream = response.getBody().in();
          } catch (IOException e) {
            throw new IllegalStateException(e); // forces a retry
          }
          try (InputStreamReader rd = new InputStreamReader(artifactInputStream)) {
            return CharStreams.toString(rd);
          } catch (IOException e) {
            throw new IllegalStateException(e); // forces a retry
          }
        },
        10,
        Duration.ofMillis(200),
        true);
  }
}
