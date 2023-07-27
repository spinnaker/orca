/*
 * Copyright 2021 Armory, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaDefinition;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaDeploymentInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaGetInput;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

public class LambdaCreateTaskTest {

  @InjectMocks private LambdaCreateTask lambdaCreateTask;

  @Mock private LambdaUtils lambdaUtils;

  @Mock private KatoService katoService;

  @Mock private StageExecution stageExecution;

  @Mock private PipelineExecution pipelineExecution;

  @Spy private ObjectMapper objectMapper;

  @BeforeEach
  void init() {
    MockitoAnnotations.openMocks(this);

    pipelineExecution.setApplication("lambdaApp");
    Mockito.when(stageExecution.getExecution()).thenReturn(pipelineExecution);
    Mockito.when(stageExecution.getContext()).thenReturn(new HashMap<>());

    Mockito.when(stageExecution.mapTo(LambdaDeploymentInput.class))
        .thenReturn(mockLambdaDeploymentInput());
    LambdaGetInput input = LambdaGetInput.builder().appName("lambdaApp").build();
    Mockito.when(stageExecution.mapTo(LambdaGetInput.class)).thenReturn(input);
  }

  @Test
  public void execute_InsertLambda_SUCCEEDED() {
    Mockito.when(lambdaUtils.validateUpsertLambdaInput(any(), any())).thenReturn(true);
    Mockito.when(lambdaUtils.retrieveLambdaFromCache(any())).thenReturn(null);

    SubmitOperationResult result = new SubmitOperationResult();
    result.setResourceUri("/resourceUri");
    Mockito.when(katoService.submitOperation(any(), any())).thenReturn(result);

    assertEquals(ExecutionStatus.SUCCEEDED, lambdaCreateTask.execute(stageExecution).getStatus());
  }

  @Test
  public void execute_UpsertLambda_SUCCEEDED() {
    Mockito.when(lambdaUtils.validateUpsertLambdaInput(any(), any())).thenReturn(true);

    Map<String, String> revisions = Map.of("revision", "1");
    LambdaDefinition ld = LambdaDefinition.builder().revisions(revisions).build();
    Mockito.when(lambdaUtils.retrieveLambdaFromCache(any())).thenReturn(ld);

    assertEquals(ExecutionStatus.SUCCEEDED, lambdaCreateTask.execute(stageExecution).getStatus());
  }

  @Test
  public void execute_InvalidUpsertLambdaInput_TERMINAL() {
    Mockito.when(lambdaUtils.validateUpsertLambdaInput(any(), any())).thenReturn(false);
    assertEquals(ExecutionStatus.TERMINAL, lambdaCreateTask.execute(stageExecution).getStatus());
  }

  private LambdaDeploymentInput mockLambdaDeploymentInput() {
    return LambdaDeploymentInput.builder()
        .appName("lambdaApp")
        .functionName("functionName")
        .account("account")
        .credentials("credentials")
        .region("us-east-1")
        .runtime("java17")
        .s3bucket("bucket")
        .s3key("key")
        .handler("handler")
        .role("role")
        .build();
  }
}
