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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.OperationContext;
import com.netflix.spinnaker.orca.clouddriver.model.SubmitOperationResult;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.LambdaUtils;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaDefinition;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaDeleteStageInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.output.LambdaCloudOperationOutput;
import java.util.*;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LambdaDeleteTask implements LambdaStageBaseTask {

  private final LambdaUtils utils;
  private final KatoService katoService;
  private final ObjectMapper objectMapper;

  public LambdaDeleteTask(
      LambdaUtils lambdaUtils, KatoService katoService, ObjectMapper objectMapper) {
    this.utils = lambdaUtils;
    this.katoService = katoService;
    this.objectMapper = objectMapper;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    log.debug("Executing LambdaDeletionTask...");
    prepareTask(stage);

    LambdaDeleteStageInput ldi = stage.mapTo(LambdaDeleteStageInput.class);
    ldi.setAppName(stage.getExecution().getApplication());

    if (ldi.getVersion().equals("$ALL")) {
      addToTaskContext(stage, "deleteTask:deleteVersion", ldi.getVersion());
      return formTaskResult(stage, deleteLambda(stage), stage.getOutputs());
    }

    String versionToDelete = getVersion(stage, ldi);
    if (versionToDelete == null) {
      addErrorMessage(
          stage, "No version found for Lambda function. Unable to perform delete operation.");
      return formSuccessTaskResult(
          stage, "LambdaDeleteTask", "Found no version of function to delete");
    }

    addToTaskContext(stage, "deleteTask:deleteVersion", versionToDelete);

    if (!versionToDelete.contains(",")) {
      ldi.setQualifier(versionToDelete);
      return formTaskResult(stage, deleteLambda(stage), stage.getOutputs());
    }

    String[] allVersionsList = versionToDelete.split(",");
    List<String> urlList = new ArrayList<>();

    for (String currVersion : allVersionsList) {
      ldi.setQualifier(currVersion);
      LambdaCloudOperationOutput ldso = deleteLambda(stage);
      urlList.add(ldso.getUrl());
    }
    addToTaskContext(stage, "urlList", urlList);
    return taskComplete(stage);
  }

  private String getVersion(StageExecution stage, LambdaDeleteStageInput ldi) {
    if (ldi.getVersion() == null) {
      return null;
    }
    if (!ldi.getVersion().startsWith("$")) { // actual version number
      return ldi.getVersion();
    }

    if (ldi.getVersion().startsWith("$PROVIDED")) { // actual version number
      return ldi.getVersionNumber();
    }

    LambdaDefinition lf = utils.retrieveLambdaFromCache(stage);
    if (lf != null) {
      return utils.getCanonicalVersion(
          lf, ldi.getVersion(), ldi.getVersionNumber(), ldi.getRetentionNumber());
    }
    return null;
  }

  private LambdaCloudOperationOutput deleteLambda(StageExecution stage) {
    LambdaDeleteStageInput inp = stage.mapTo(LambdaDeleteStageInput.class);
    inp.setCredentials(inp.getAccount());

    OperationContext context = objectMapper.convertValue(inp, new TypeReference<>() {});
    context.setOperationType("deleteLambdaFunction");
    SubmitOperationResult result = katoService.submitOperation("aws", context);

    return LambdaCloudOperationOutput.builder().url(result.getResourceUri()).build();
  }
}
