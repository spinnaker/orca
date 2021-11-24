/*
 * Copyright 2021 Salesforce.com, Inc.
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

package com.netflix.spinnaker.orca.pipeline;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.config.ExecutionConfigurationProperties;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
public class ExecutionLauncherTest {

  private ObjectMapper objectMapper;
  @Mock private ExecutionRepository executionRepository;
  @Mock private ExecutionRunner executionRunner;
  @Mock private ApplicationEventPublisher applicationEventPublisher;
  private ExecutionConfigurationProperties executionConfigurationProperties;
  private ExecutionLauncher executionLauncher;

  @BeforeEach
  void setup() {
    objectMapper = new ObjectMapper();
    executionConfigurationProperties = new ExecutionConfigurationProperties();

    executionLauncher =
        new ExecutionLauncher(
            objectMapper,
            executionRepository,
            executionRunner,
            Clock.systemUTC(),
            applicationEventPublisher,
            Optional.empty(),
            Optional.empty(),
            executionConfigurationProperties);
  }

  @DisplayName(
      "when some ad-hoc executions should be blocked, then those execution attempts should fail")
  @Test
  public void testOrchestrationExecutionsThatAreDisabled() throws Exception {
    // setup
    executionConfigurationProperties.setBlockOrchestrationExecutions(true);
    // when
    PipelineExecution pipelineExecution =
        executionLauncher.start(
            ExecutionType.ORCHESTRATION, getConfigJson("ad-hoc/deploy-manifest.json"));

    // then
    // verify that the failure reason is what we expect
    verify(executionRepository).updateStatus(pipelineExecution);
    verify(executionRepository)
        .cancel(
            ExecutionType.ORCHESTRATION,
            pipelineExecution.getId(),
            "system",
            "Failed on startup: ad-hoc execution of type: deployManifest has been explicitly disabled");
  }

  @DisplayName(
      "when some ad-hoc executions should be blocked, then executions not configured to be blocked "
          + "should be allowed to run")
  @Test
  public void testOrchestrationExecutionsThatAreEnabled() throws Exception {
    executionConfigurationProperties.setBlockOrchestrationExecutions(true);
    // when
    PipelineExecution pipelineExecution =
        executionLauncher.start(
            ExecutionType.ORCHESTRATION, getConfigJson("ad-hoc/save-pipeline.json"));

    // then
    // verify that the execution runner attempted to start the execution as expected
    verify(executionRunner).start(pipelineExecution);
    // verify that no errors were thrown such as the explicitly disabled ones
    verify(executionRepository, never()).updateStatus(any());
    verify(executionRepository, never()).cancel(any(), anyString(), anyString(), anyString());
  }

  @DisplayName(
      "when ad-hoc executions should not be blocked, then all executions should be allowed to run")
  @Test
  public void testOrchestrationExecutionsThatAreDisabledWithFlagTurnedOff() throws Exception {
    // when
    PipelineExecution pipelineExecution =
        executionLauncher.start(
            ExecutionType.ORCHESTRATION, getConfigJson("ad-hoc/deploy-manifest.json"));

    // then
    // verify that the execution runner attempted to start the execution as expected
    verify(executionRunner).start(pipelineExecution);
    // verify that no errors were thrown such as the explicitly disabled ones
    verify(executionRepository, never()).updateStatus(any());
    verify(executionRepository, never()).cancel(any(), anyString(), anyString(), anyString());
  }

  private String getConfigJson(String resource) throws Exception {
    Map requestBody =
        objectMapper.readValue(
            ExecutionLauncherTest.class.getResourceAsStream(resource), Map.class);
    return objectMapper.writeValueAsString(requestBody);
  }
}
