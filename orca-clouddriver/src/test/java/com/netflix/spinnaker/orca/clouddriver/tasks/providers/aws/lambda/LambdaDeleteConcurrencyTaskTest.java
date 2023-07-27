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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.SubmitOperationResult;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.LambdaUtils;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaConcurrencyInput;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

public class LambdaDeleteConcurrencyTaskTest {

  @InjectMocks private LambdaDeleteConcurrencyTask lambdaDeleteConcurrencyTask;

  @Mock private KatoService katoService;

  @Mock private LambdaUtils lambdaUtils;

  @Mock private StageExecution stageExecution;

  @Mock private PipelineExecution pipelineExecution;

  @Spy private ObjectMapper objectMapper;

  @BeforeEach
  void init() {
    MockitoAnnotations.openMocks(this);

    pipelineExecution.setApplication("lambdaApp");
    Mockito.when(stageExecution.getExecution()).thenReturn(pipelineExecution);
    Mockito.when(stageExecution.getContext()).thenReturn(new HashMap<>());
    Mockito.when(stageExecution.getOutputs()).thenReturn(new HashMap<>());

    Mockito.when(lambdaUtils.validateUpsertLambdaInput(any(), any())).thenReturn(true);

    LambdaConcurrencyInput ldi =
        LambdaConcurrencyInput.builder().functionName("functionName").build();
    Mockito.when(stageExecution.mapTo(LambdaConcurrencyInput.class)).thenReturn(ldi);
  }

  @Test
  public void execute_DeleteReservedConcurrency_SUCCEEDED() {
    Mockito.when(stageExecution.getType()).thenReturn("Aws.LambdaDeploymentStage");

    SubmitOperationResult result = new SubmitOperationResult();
    result.setResourceUri("/resourceUri");
    Mockito.when(katoService.submitOperation(any(), any())).thenReturn(result);

    assertEquals(
        ExecutionStatus.SUCCEEDED, lambdaDeleteConcurrencyTask.execute(stageExecution).getStatus());
  }

  @Test
  public void execute_DeleteReservedConcurrency_NOTHING_TO_DELETE() {
    Mockito.when(stageExecution.getType()).thenReturn("Aws.LambdaDeploymentStage");

    LambdaConcurrencyInput ldi =
        LambdaConcurrencyInput.builder()
            .functionName("functionName")
            .reservedConcurrentExecutions(10)
            .build();
    Mockito.when(stageExecution.mapTo(LambdaConcurrencyInput.class)).thenReturn(ldi);

    assertEquals(
        ExecutionStatus.SUCCEEDED, lambdaDeleteConcurrencyTask.execute(stageExecution).getStatus());
    assertEquals(
        "Lambda delete concurrency : nothing to delete",
        stageExecution.getOutputs().get("LambdaDeleteConcurrencyTask"));
  }

  @Test
  public void execute_DeleteProvisionedConcurrency_SUCCEEDED() {
    Mockito.when(stageExecution.getType()).thenReturn("Aws.LambdaTrafficRoutingStage");

    SubmitOperationResult result = new SubmitOperationResult();
    result.setResourceUri("/resourceUri");
    Mockito.when(katoService.submitOperation(any(), any())).thenReturn(result);

    assertEquals(
        ExecutionStatus.SUCCEEDED, lambdaDeleteConcurrencyTask.execute(stageExecution).getStatus());
  }

  @Test
  public void execute_DeleteProvisionedConcurrency_NOTHING_TO_DELETE() {
    Mockito.when(stageExecution.getType()).thenReturn("Aws.LambdaTrafficRoutingStage");

    LambdaConcurrencyInput ldi =
        LambdaConcurrencyInput.builder()
            .functionName("functionName")
            .provisionedConcurrentExecutions(10)
            .build();
    Mockito.when(stageExecution.mapTo(LambdaConcurrencyInput.class)).thenReturn(ldi);

    assertEquals(
        ExecutionStatus.SUCCEEDED, lambdaDeleteConcurrencyTask.execute(stageExecution).getStatus());
    assertEquals(
        "Lambda delete concurrency : nothing to delete",
        stageExecution.getOutputs().get("LambdaDeleteConcurrencyTask"));
  }
}
