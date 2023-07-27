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

import com.amazonaws.services.lambda.model.EventSourceMappingConfiguration;
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
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaDeleteEventTaskInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaUpdateEventConfigurationTaskInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.output.LambdaCloudOperationOutput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.output.LambdaUpdateEventConfigurationTaskOutput;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.util.StringUtils;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LambdaUpdateEventConfigurationTask implements LambdaStageBaseTask {

  private static final String DEFAULT_STARTING_POSITION = "LATEST";
  private static final String DYNAMO_EVENT_PREFIX = "arn:aws:dynamodb:";
  private static final String KINESIS_EVENT_PREFIX = "arn:aws:kinesis";

  private final LambdaUtils utils;
  private final KatoService katoService;
  private final ObjectMapper objectMapper;

  public LambdaUpdateEventConfigurationTask(
      LambdaUtils utils, KatoService katoService, ObjectMapper objectMapper) {
    this.utils = utils;
    this.katoService = katoService;
    this.objectMapper = objectMapper;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull StageExecution stage) {
    log.debug("Executing LambdaUpdateEventConfigurationTask");
    LambdaUpdateEventConfigurationTaskInput taskInput =
        stage.mapTo(LambdaUpdateEventConfigurationTaskInput.class);
    taskInput.setAppName(stage.getExecution().getApplication());

    LambdaDefinition lf = utils.retrieveLambdaFromCache(stage);
    if (lf == null) {
      return formErrorTaskResult(stage, "Could not find lambda to update event config for");
    }
    String functionArn = lf.getFunctionArn();
    if (StringUtils.isNotNullOrEmpty(taskInput.getAliasName())) {
      log.debug("LambdaUpdateEventConfigurationTask for alias");
      functionArn = String.format("%s:%s", lf.getFunctionArn(), taskInput.getAliasName());
      taskInput.setQualifier(taskInput.getAliasName());
    }
    return updateEventsForLambdaFunction(taskInput, lf, functionArn);
  }

  private TaskResult updateEventsForLambdaFunction(
      LambdaUpdateEventConfigurationTaskInput taskInput, LambdaDefinition lf, String functionArn) {
    if (taskInput.getTriggerArns() == null || taskInput.getTriggerArns().size() == 0) {
      deleteAllExistingEvents(taskInput, lf, functionArn);
      Map<String, Object> context = new HashMap<>();
      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(context).build();
    }
    deleteRemovedAndChangedEvents(taskInput, lf, functionArn);
    LambdaUpdateEventConfigurationTaskOutput ldso = updateEventConfiguration(taskInput);
    Map<String, Object> context = buildContextOutput(ldso);
    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(context).build();
  }

  /**
   * New configuration has zero events. So find all existing events in the lambda cache and delete
   * them all.
   */
  private void deleteAllExistingEvents(
      LambdaUpdateEventConfigurationTaskInput taskInput, LambdaDefinition lf, String targetArn) {
    List<String> eventArnList = getExistingEvents(lf, targetArn);
    eventArnList.forEach(eventArn -> deleteEvent(eventArn, taskInput, lf, targetArn));
  }

  /**
   * For each eventTriggerArn that already exists at backend: delete from backend if it does not
   * exist in input
   */
  private void deleteRemovedAndChangedEvents(
      LambdaUpdateEventConfigurationTaskInput taskInput, LambdaDefinition lf, String targetArn) {
    List<String> eventArnList = getExistingEvents(lf, targetArn);
    // Does not deal with change in batch size(s)
    eventArnList.stream()
        .filter(x -> !taskInput.getTriggerArns().contains(x))
        .forEach(eventArn -> deleteEvent(eventArn, taskInput, lf, targetArn));
  }

  List<String> getExistingEvents(LambdaDefinition lf, String targetArn) {
    List<EventSourceMappingConfiguration> esmList = lf.getEventSourceMappings();
    if (esmList == null) {
      return new ArrayList<>();
    }
    return esmList.stream()
        .filter(y -> y.getFunctionArn().equals(targetArn))
        .map(EventSourceMappingConfiguration::getEventSourceArn)
        .filter(StringUtils::isNotNullOrEmpty)
        .collect(Collectors.toList());
  }

  private void deleteEvent(
      String eventArn,
      LambdaUpdateEventConfigurationTaskInput ti,
      LambdaDefinition lgo,
      String aliasOrFunctionArn) {
    log.debug("To be deleted: " + eventArn);
    List<EventSourceMappingConfiguration> esmList = lgo.getEventSourceMappings();
    Optional<EventSourceMappingConfiguration> oo =
        esmList.stream()
            .filter(
                x ->
                    x.getEventSourceArn().equals(eventArn)
                        && x.getFunctionArn().equals(aliasOrFunctionArn))
            .findFirst();
    if (oo.isEmpty()) {
      return;
    }
    EventSourceMappingConfiguration toDelete = oo.get();
    LambdaDeleteEventTaskInput inp =
        LambdaDeleteEventTaskInput.builder()
            .account(ti.getAccount())
            .credentials(ti.getCredentials())
            .functionName(ti.getFunctionName())
            .eventSourceArn(toDelete.getEventSourceArn())
            .uuid(toDelete.getUUID())
            .region(ti.getRegion())
            .build();
    if (StringUtils.isNotNullOrEmpty(ti.getAliasName())) {
      ti.setQualifier(ti.getAliasName());
    }
    inp.setUuid(toDelete.getUUID());

    deleteLambdaEventConfig(inp);
  }

  private LambdaUpdateEventConfigurationTaskOutput updateEventConfiguration(
      LambdaUpdateEventConfigurationTaskInput taskInput) {
    LambdaUpdateEventConfigurationTaskOutput ans =
        LambdaUpdateEventConfigurationTaskOutput.builder().build();
    ans.setEventOutputs(List.of());
    taskInput.setCredentials(taskInput.getAccount());

    taskInput
        .getTriggerArns()
        .forEach(
            curr -> {
              LambdaEventConfigurationDescription singleEvent = formEventObject(curr, taskInput);

              OperationContext context =
                  objectMapper.convertValue(singleEvent, new TypeReference<>() {});
              context.setOperationType("upsertLambdaFunctionEventMapping");
              SubmitOperationResult result =
                  katoService.submitOperation(getCloudProvider(), context);

              LambdaCloudOperationOutput z =
                  LambdaCloudOperationOutput.builder().resourceId(result.getResourceUri()).build();
              ans.getEventOutputs().add(z);
            });

    return ans;
  }

  private LambdaEventConfigurationDescription formEventObject(
      String curr, LambdaUpdateEventConfigurationTaskInput taskInput) {
    LambdaEventConfigurationDescription singleEvent =
        LambdaEventConfigurationDescription.builder()
            .eventSourceArn(curr)
            .batchsize(taskInput.getBatchsize())
            .enabled(true)
            .maxBatchingWindowSecs(taskInput.getMaxBatchingWindowSecs())
            .account(taskInput.getAccount())
            .credentials(taskInput.getCredentials())
            .appName(taskInput.getAppName())
            .region(taskInput.getRegion())
            .functionName(taskInput.getFunctionName())
            .qualifier(taskInput.getQualifier())
            .build();
    if (curr.startsWith(DYNAMO_EVENT_PREFIX) || curr.startsWith(KINESIS_EVENT_PREFIX)) {
      if (StringUtils.isNullOrEmpty(taskInput.getStartingPosition())) {
        taskInput.setStartingPosition(DEFAULT_STARTING_POSITION);
      }

      if (taskInput.getBisectBatchOnError() != null) {
        singleEvent.setBisectBatchOnError(taskInput.getBisectBatchOnError());
      }

      singleEvent.setDestinationConfig(formDestinationConfig(taskInput));

      if (taskInput.getMaxRecordAgeSecs() != null) {
        singleEvent.setMaxRecordAgeSecs(taskInput.getMaxRecordAgeSecs());
      }

      if (taskInput.getMaxRetryAttempts() != null) {
        singleEvent.setMaxRetryAttempts(taskInput.getMaxRetryAttempts());
      }

      if (taskInput.getParallelizationFactor() != null) {
        singleEvent.setParallelizationFactor(taskInput.getParallelizationFactor());
      }

      singleEvent.setStartingPosition(taskInput.getStartingPosition());

      if (taskInput.getTumblingWindowSecs() != null && taskInput.getTumblingWindowSecs() != -1) {
        singleEvent.setTumblingWindowSecs(taskInput.getTumblingWindowSecs());
      }
    }
    return singleEvent;
  }

  private Map<String, Object> formDestinationConfig(
      LambdaUpdateEventConfigurationTaskInput taskInput) {
    Map<String, Object> destinationConfig = null;

    if (taskInput.getDestinationConfig() != null) {
      String onFailureArn = taskInput.getDestinationConfig().get("onFailureArn");
      String onSuccessArn = taskInput.getDestinationConfig().get("onSuccessArn");

      if (StringUtils.isNotNullOrEmpty(onFailureArn)
          || StringUtils.isNotNullOrEmpty(onSuccessArn)) {
        destinationConfig = new HashMap<>();

        if (StringUtils.isNotNullOrEmpty(onFailureArn)) {
          Map<String, String> onFailure = new HashMap<>();
          onFailure.put("destination", onFailureArn);
          destinationConfig.put("onFailure", onFailure);
        }

        if (StringUtils.isNotNullOrEmpty(onSuccessArn)) {
          Map<String, String> onSuccess = new HashMap<>();
          onSuccess.put("destination", onSuccessArn);
          destinationConfig.put("onSuccess", onSuccess);
        }
      }
    }

    return destinationConfig;
  }

  private void deleteLambdaEventConfig(LambdaDeleteEventTaskInput inp) {
    inp.setCredentials(inp.getAccount());

    OperationContext context = objectMapper.convertValue(inp, new TypeReference<>() {});
    context.setOperationType("deleteLambdaFunctionEventMapping");
    katoService.submitOperation(getCloudProvider(), context);
  }

  /** Fill up with values required for next task */
  private Map<String, Object> buildContextOutput(LambdaUpdateEventConfigurationTaskOutput ldso) {
    List<String> urlList = new ArrayList<>();
    ldso.getEventOutputs().forEach(x -> urlList.add(x.getUrl()));
    Map<String, Object> context = new HashMap<>();
    context.put(LambdaStageConstants.eventTaskKey, urlList);
    return context;
  }
}
