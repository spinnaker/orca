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
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaUpdateAliasesInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.output.LambdaCloudOperationOutput;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.util.StringUtils;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LambdaUpdateAliasesTask implements LambdaStageBaseTask {
  private static final String DEFAULT_ALIAS_DESCRIPTION = "Created via Spinnaker";
  private static final String LATEST_VERSION_STRING = "$LATEST";

  private final KatoService katoService;
  private final ObjectMapper objectMapper;

  public LambdaUpdateAliasesTask(KatoService katoService, ObjectMapper objectMapper) {
    this.katoService = katoService;
    this.objectMapper = objectMapper;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    log.debug("Executing LambdaUpdateAliasesTask...");
    prepareTask(stage);

    if (!shouldAddAliases(stage)) {
      addToOutput(stage, LambdaStageConstants.lambaAliasesUpdatedKey, Boolean.FALSE);
      return taskComplete(stage);
    }

    List<LambdaCloudOperationOutput> output = updateLambdaAliases(stage);
    buildContextOutput(stage, output);
    addToTaskContext(stage, LambdaStageConstants.lambaAliasesUpdatedKey, Boolean.TRUE);
    addToOutput(stage, LambdaStageConstants.lambaAliasesUpdatedKey, Boolean.TRUE);
    return taskComplete(stage);
  }

  /** Fill up with values required for next task */
  private void buildContextOutput(StageExecution stage, List<LambdaCloudOperationOutput> ldso) {
    List<String> urlList = new ArrayList<>();
    ldso.forEach(x -> urlList.add(x.getUrl()));
    addToTaskContext(stage, LambdaStageConstants.eventTaskKey, urlList);
  }

  private boolean shouldAddAliases(StageExecution stage) {
    return stage.getContext().containsKey("aliases");
  }

  private LambdaCloudOperationOutput updateSingleAlias(LambdaUpdateAliasesInput inp, String alias) {
    inp.setAliasDescription(DEFAULT_ALIAS_DESCRIPTION);
    inp.setAliasName(alias);
    inp.setMajorFunctionVersion(LATEST_VERSION_STRING);

    OperationContext context = objectMapper.convertValue(inp, new TypeReference<>() {});
    context.setOperationType("upsertLambdaFunctionAlias");
    SubmitOperationResult result = katoService.submitOperation(getCloudProvider(), context);

    return LambdaCloudOperationOutput.builder()
        .resourceId(result.getId())
        .url(result.getResourceUri())
        .build();
  }

  private List<LambdaCloudOperationOutput> updateLambdaAliases(StageExecution stage) {
    List<LambdaCloudOperationOutput> result = new ArrayList<>();

    LambdaUpdateAliasesInput inp = stage.mapTo(LambdaUpdateAliasesInput.class);
    inp.setAppName(stage.getExecution().getApplication());
    inp.setCredentials(inp.getAccount());

    List<String> aliases = (List<String>) stage.getContext().get("aliases");
    for (String alias : aliases) {
      if (StringUtils.isNullOrEmpty(alias)) {
        continue;
      }

      String formattedAlias = alias.trim();
      if (StringUtils.isNullOrEmpty(formattedAlias)) {
        continue;
      }

      LambdaCloudOperationOutput operationOutput = updateSingleAlias(inp, formattedAlias);
      result.add(operationOutput);
    }
    return result;
  }

  @Nullable
  @Override
  public TaskResult onTimeout(@Nonnull StageExecution stage) {
    return TaskResult.builder(ExecutionStatus.SKIPPED).build();
  }
}
