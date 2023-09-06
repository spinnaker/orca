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
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaInvokeStageInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaTrafficUpdateInput;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.util.StringUtils;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LambdaInvokeTask implements LambdaStageBaseTask {

  private final LambdaUtils utils;
  private final KatoService katoService;
  private final ObjectMapper objectMapper;

  public LambdaInvokeTask(LambdaUtils utils, KatoService katoService, ObjectMapper objectMapper) {
    this.utils = utils;
    this.katoService = katoService;
    this.objectMapper = objectMapper;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    log.debug("Executing LambdaInvokeTask...");
    prepareTask(stage);

    LambdaDefinition lf = utils.retrieveLambdaFromCache(stage);
    if (lf == null) {
      log.error("Could not find lambda");
      return this.formErrorTaskResult(stage, "No such lambda found.");
    }

    LambdaInvokeStageInput ldi = stage.mapTo(LambdaInvokeStageInput.class);
    LambdaTrafficUpdateInput tui = stage.mapTo(LambdaTrafficUpdateInput.class);
    ldi.setPayloadArtifact(tui.getPayloadArtifact().getArtifact());
    ldi.setQualifier(
        StringUtils.isNullOrEmpty(ldi.getAliasName()) ? "$LATEST" : ldi.getAliasName());
    ldi.setAppName(stage.getExecution().getApplication());
    ldi.setCredentials(ldi.getAccount());
    List<String> urlList = new ArrayList<>();
    for (int i = 0; i < ldi.getExecutionCount(); i++) {
      String url = invokeLambdaFunction(ldi);
      urlList.add(url);
    }
    addToTaskContext(stage, "urlList", urlList);
    return taskComplete(stage);
  }

  private String invokeLambdaFunction(LambdaInvokeStageInput ldi) {
    OperationContext context = objectMapper.convertValue(ldi, new TypeReference<>() {});
    context.setOperationType("invokeLambdaFunction");

    SubmitOperationResult result = katoService.submitOperation(getCloudProvider(), context);
    return result.getResourceUri();
  }

  @Override
  public Collection<String> aliases() {
    return List.of("lambdaInvokeTask");
  }
}
