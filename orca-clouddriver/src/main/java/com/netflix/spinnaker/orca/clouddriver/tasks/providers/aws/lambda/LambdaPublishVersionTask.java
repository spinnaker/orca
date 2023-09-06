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
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.OperationContext;
import com.netflix.spinnaker.orca.clouddriver.model.SubmitOperationResult;
import com.netflix.spinnaker.orca.clouddriver.pipeline.providers.aws.lambda.LambdaStageConstants;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.LambdaUtils;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.*;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaPublisVersionInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.output.LambdaCloudOperationOutput;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LambdaPublishVersionTask implements LambdaStageBaseTask, CloudProviderAware {

  private final LambdaUtils utils;
  private final KatoService katoService;
  private final ObjectMapper objectMapper;

  public LambdaPublishVersionTask(
      LambdaUtils utils, KatoService katoService, ObjectMapper objectMapper) {
    this.utils = utils;
    this.katoService = katoService;
    this.objectMapper = objectMapper;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    log.debug("Executing LambdaPublishVersionTask...");
    prepareTask(stage);

    if (!requiresVersionPublish(stage)) {
      addToOutput(stage, LambdaStageConstants.lambaVersionPublishedKey, Boolean.FALSE);
      return taskComplete(stage);
    }

    LambdaCloudOperationOutput output = this.publishVersion(stage);
    addCloudOperationToContext(stage, output, LambdaStageConstants.publishVersionUrlKey);

    addToTaskContext(stage, LambdaStageConstants.lambaVersionPublishedKey, Boolean.TRUE);
    return taskComplete(stage);
  }

  private boolean requiresVersionPublish(StageExecution stage) {
    Boolean justCreated =
        (Boolean)
            stage.getContext().getOrDefault(LambdaStageConstants.lambaCreatedKey, Boolean.FALSE);
    if (justCreated) {
      return false;
    }

    Boolean requiresPublishFlag =
        (Boolean) stage.getContext().getOrDefault("publish", Boolean.FALSE);
    if (!requiresPublishFlag) {
      return false;
    }

    LambdaDefinition lf = utils.retrieveLambdaFromCache(stage);
    String newRevisionId = lf.getRevisionId();
    String origRevisionId =
        (String) stage.getContext().get(LambdaStageConstants.originalRevisionIdKey);
    stage.getContext().put(LambdaStageConstants.newRevisionIdKey, newRevisionId);
    return !newRevisionId.equals(origRevisionId);
  }

  private LambdaCloudOperationOutput publishVersion(StageExecution stage) {
    LambdaPublisVersionInput inp = stage.mapTo(LambdaPublisVersionInput.class);
    inp.setAppName(stage.getExecution().getApplication());
    inp.setCredentials(inp.getAccount());

    String revisionId = (String) stage.getContext().get(LambdaStageConstants.newRevisionIdKey);
    inp.setRevisionId(revisionId);

    OperationContext context = objectMapper.convertValue(inp, new TypeReference<>() {});
    context.setOperationType("publishLambdaFunctionVersion");
    SubmitOperationResult result = katoService.submitOperation(getCloudProvider(), context);

    return LambdaCloudOperationOutput.builder()
        .resourceId(result.getId())
        .url(result.getResourceUri())
        .build();
  }

  @Nullable
  @Override
  public TaskResult onTimeout(@Nonnull StageExecution stage) {
    return TaskResult.builder(ExecutionStatus.SKIPPED).build();
  }
}
