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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws;

import static com.netflix.spinnaker.orca.TestUtils.getResource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.model.Task;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaCloudDriverInvokeOperationResults;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaCloudDriverTaskResults;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.LambdaDefinition;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaDeploymentInput;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaGetInput;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;

public class LambdaUtilsTest {

  @InjectMocks private LambdaUtils lambdaUtils;

  @Mock private KatoService katoService;

  @Mock private OortService oortService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void init() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  public void validateUpsertLambdaInput_EnableLambdaAtEdgeIsFalse_True() {
    LambdaDeploymentInput ldi =
        LambdaDeploymentInput.builder()
            .account("account-test")
            .region("us-west-2")
            .functionName("function-test")
            .runtime("runtime")
            .s3bucket("s3-bucket")
            .s3key("abcdefg123456")
            .handler("handler")
            .role("arn:aws:iam::123456789012:role/LambdaAccess")
            .enableLambdaAtEdge(false)
            .build();
    assertTrue(lambdaUtils.validateUpsertLambdaInput(ldi, new ArrayList<>()));
  }

  @Test
  public void validateLambdaEdgeInput_EnvVariablesIsNotEmpty_False() {
    HashMap<String, String> envVariables = new HashMap<>();
    envVariables.put("ENV_TEST_PORT", "8888");
    LambdaDeploymentInput ldi =
        LambdaDeploymentInput.builder()
            .envVariables(envVariables)
            .region("us-east-1")
            .memorySize(128)
            .subnetIds(new ArrayList<>())
            .securityGroupIds(new ArrayList<>())
            .build();
    assertFalse(lambdaUtils.validateLambdaEdgeInput(ldi, new ArrayList<>()));
  }

  @Test
  public void validateLambdaEdgeInput_TimeoutGreaterThan5_False() {
    LambdaDeploymentInput ldi =
        LambdaDeploymentInput.builder()
            .envVariables(new HashMap<>())
            .timeout(10)
            .memorySize(128)
            .subnetIds(new ArrayList<>())
            .securityGroupIds(new ArrayList<>())
            .region("us-east-1")
            .build();
    assertFalse(lambdaUtils.validateLambdaEdgeInput(ldi, new ArrayList<>()));
  }

  @Test
  public void validateLambdaEdgeInput_MemorySizeGreaterThan128_False() {
    LambdaDeploymentInput ldi =
        LambdaDeploymentInput.builder()
            .envVariables(new HashMap<>())
            .timeout(5)
            .region("us-east-1")
            .memorySize(256)
            .subnetIds(new ArrayList<>())
            .securityGroupIds(new ArrayList<>())
            .build();
    assertFalse(lambdaUtils.validateLambdaEdgeInput(ldi, new ArrayList<>()));
  }

  @Test
  public void validateLambdaEdgeInput_RegionIsNotUsEast1_False() {
    LambdaDeploymentInput ldi =
        LambdaDeploymentInput.builder()
            .envVariables(new HashMap<>())
            .timeout(5)
            .region("us-west-2")
            .memorySize(128)
            .subnetIds(new ArrayList<>())
            .securityGroupIds(new ArrayList<>())
            .build();
    assertFalse(lambdaUtils.validateLambdaEdgeInput(ldi, new ArrayList<>()));
  }

  @Test
  public void validateLambdaEdgeInput_VpcIdIsNotNull_False() {
    LambdaDeploymentInput ldi =
        LambdaDeploymentInput.builder()
            .envVariables(new HashMap<>())
            .timeout(5)
            .region("us-east-1")
            .memorySize(128)
            .subnetIds(new ArrayList<>())
            .securityGroupIds(new ArrayList<>())
            .vpcId("vpc-2f09a348")
            .build();
    assertFalse(lambdaUtils.validateLambdaEdgeInput(ldi, new ArrayList<>()));
  }

  @Test
  public void validateLambdaEdgeInput_HaveSubNetIds_False() {
    LambdaDeploymentInput ldi =
        LambdaDeploymentInput.builder()
            .envVariables(new HashMap<>())
            .timeout(5)
            .region("us-east-1")
            .memorySize(128)
            .subnetIds(Stream.of("subnet-b46032ec").collect(Collectors.toList()))
            .securityGroupIds(new ArrayList<>())
            .build();
    assertFalse(lambdaUtils.validateLambdaEdgeInput(ldi, new ArrayList<>()));
  }

  @Test
  public void validateLambdaEdgeInput_HaveSecurityGroups_False() {
    LambdaDeploymentInput ldi =
        LambdaDeploymentInput.builder()
            .envVariables(new HashMap<>())
            .timeout(5)
            .region("us-east-1")
            .memorySize(128)
            .subnetIds(new ArrayList<>())
            .securityGroupIds(Stream.of("sg-b46032ec").collect(Collectors.toList()))
            .build();
    assertFalse(lambdaUtils.validateLambdaEdgeInput(ldi, new ArrayList<>()));
  }

  @Test
  public void getSortedRevisions_SortVersion_321() {
    Map<String, String> revisions = new HashMap<>();
    revisions.put("first", "1");
    revisions.put("second", "2");
    revisions.put("third", "3");
    List<String> sortedList = Stream.of("3", "2", "1").collect(Collectors.toList());
    LambdaDefinition lambdaDefinition = LambdaDefinition.builder().revisions(revisions).build();
    assertEquals(sortedList, lambdaUtils.getSortedRevisions(lambdaDefinition));
  }

  @Test
  public void getCanonicalVersion_NoRevisions_NoPublishedVersionsExist() {
    Map<String, String> revisions = new HashMap<>();
    LambdaDefinition lambdaDefinition = LambdaDefinition.builder().revisions(revisions).build();
    assertNull(lambdaUtils.getCanonicalVersion(lambdaDefinition, "", "", 1));
  }

  @Test
  public void getCanonicalVersion_ProvidedRevision_PROVIDED() {
    Map<String, String> revisions = new HashMap<>();
    revisions.put("first", "1");
    LambdaDefinition lambdaDefinition = LambdaDefinition.builder().revisions(revisions).build();
    assertEquals("3", lambdaUtils.getCanonicalVersion(lambdaDefinition, "$PROVIDED", "3", 0));
  }

  @Test
  public void getCanonicalVersion_LatestRevision_GetLatestVersion() {
    Map<String, String> revisions = new HashMap<>();
    revisions.put("first", "1");
    revisions.put("third", "3");
    revisions.put("second", "2");
    LambdaDefinition lambdaDefinition = LambdaDefinition.builder().revisions(revisions).build();
    assertEquals("3", lambdaUtils.getCanonicalVersion(lambdaDefinition, "$LATEST", "5", 0));
  }

  @Test
  public void getCanonicalVersion_OldestRevision_GetOldestVersion() {
    Map<String, String> revisions = new HashMap<>();
    revisions.put("first", "5");
    revisions.put("third", "1");
    revisions.put("second", "7");
    LambdaDefinition lambdaDefinition = LambdaDefinition.builder().revisions(revisions).build();
    assertEquals("1", lambdaUtils.getCanonicalVersion(lambdaDefinition, "$OLDEST", "5", 0));
  }

  @Test
  public void getCanonicalVersion_PreviousRevision_GetPreviousVersion() {
    Map<String, String> revisions = new HashMap<>();
    revisions.put("first", "5");
    revisions.put("third", "1");
    revisions.put("second", "7");
    LambdaDefinition lambdaDefinition = LambdaDefinition.builder().revisions(revisions).build();
    assertEquals("5", lambdaUtils.getCanonicalVersion(lambdaDefinition, "$PREVIOUS", "5", 0));
  }

  @Test
  public void getCanonicalVersion_MovingRevision_GetMovingVersion() {
    Map<String, String> revisions = new HashMap<>();
    revisions.put("first", "5");
    revisions.put("third", "1");
    revisions.put("second", "7");
    revisions.put("other", "8");
    LambdaDefinition lambdaDefinition = LambdaDefinition.builder().revisions(revisions).build();
    assertEquals("5,1", lambdaUtils.getCanonicalVersion(lambdaDefinition, "$MOVING", "5", 2));
  }

  @Test
  public void getCanonicalVersion_InvalidInputVersion_Null() {
    Map<String, String> revisions = new HashMap<>();
    revisions.put("first", "5");
    LambdaDefinition lambdaDefinition = LambdaDefinition.builder().revisions(revisions).build();
    assertNull(lambdaUtils.getCanonicalVersion(lambdaDefinition, "$FAKE_INOUT_VERSION", "5", 2));
  }

  @Test
  public void getCanonicalVersion_NoRevisions_Null() {
    LambdaDefinition lambdaDefinition =
        LambdaDefinition.builder().revisions(new HashMap<>()).build();
    assertNull(lambdaUtils.getCanonicalVersion(lambdaDefinition, "$PREVIOUS", "5", 2));
  }

  @Test
  public void retrieveLambdaFromCache_ShouldGetLambdaGetInput_NotNull() {
    List<LambdaDefinition> definitions =
        List.of(
            getResource(
                objectMapper,
                "clouddriver/tasks/providers/aws/lambda/functions.json",
                LambdaDefinition[].class));
    Mockito.when(oortService.getFunction("account1", "us-west-2", "app-test-function-test"))
        .thenReturn(definitions);

    LambdaGetInput lambdaGetInput =
        LambdaGetInput.builder()
            .region("us-west-2")
            .account("account1")
            .functionName("function-test")
            .appName("app-test")
            .build();

    StageExecution execution = new StageExecutionImpl();
    execution.setExecution(new PipelineExecutionImpl(ExecutionType.PIPELINE, "app-test"));
    execution.setContext(inputToMap(lambdaGetInput));

    assertNotNull(lambdaUtils.retrieveLambdaFromCache(execution));
  }

  @Test
  public void retrieveLambdaFromCache_ShouldNotGetLambdaGetInput_Null() {
    Mockito.when(oortService.getFunction("account1", "us-west-2", "app-test-function-test"))
        .thenReturn(List.of());

    LambdaGetInput lambdaGetInput =
        LambdaGetInput.builder()
            .region("us-west-2")
            .account("account2")
            .functionName("function-test")
            .appName("app-test")
            .build();

    StageExecution execution = new StageExecutionImpl();
    execution.setExecution(new PipelineExecutionImpl(ExecutionType.PIPELINE, "app-test"));
    execution.setContext(inputToMap(lambdaGetInput));

    assertNull(lambdaUtils.retrieveLambdaFromCache(execution));
  }

  @Test
  public void getLambdaInvokeResults_ShouldGetLambdaCloudDriverInvokeOperationResults_NotNull() {
    Task task =
        getResource(
            objectMapper,
            "clouddriver/tasks/providers/aws/lambda/kato-status-success.json",
            Task.class);
    Mockito.when(katoService.lookupTaskWithUri(any())).thenReturn(task);

    LambdaCloudDriverInvokeOperationResults results = lambdaUtils.getLambdaInvokeResults("/uri");
    assertNotNull(results);
    assertEquals(200, results.getStatusCode());
  }

  @Test
  public void getPublishedVersion_getVersion_1() {
    Task task =
        getResource(
            objectMapper,
            "clouddriver/tasks/providers/aws/lambda/kato-published-version.json",
            Task.class);
    Mockito.when(katoService.lookupTaskWithUri(any())).thenReturn(task);

    String publishedVersion = lambdaUtils.getPublishedVersion("/uri");
    assertNotNull(publishedVersion);
    assertEquals("1", publishedVersion);
  }

  @Test
  public void getPublishedVersion_getLatest_$LATEST() {
    Task task =
        getResource(
            objectMapper,
            "clouddriver/tasks/providers/aws/lambda/kato-published-version-empty.json",
            Task.class);
    Mockito.when(katoService.lookupTaskWithUri(any())).thenReturn(task);

    String publishedVersion = lambdaUtils.getPublishedVersion("/uri");
    assertNotNull(publishedVersion);
    assertEquals("$LATEST", publishedVersion);
  }

  @Test
  public void getPublishedVersion_ShouldGetLatestIfNoVersionIsSpecified_$LATEST() {
    Mockito.when(katoService.lookupTaskWithUri(any()))
        .thenThrow(new HttpServerErrorException(HttpStatus.BAD_REQUEST));

    String publishedVersion = lambdaUtils.getPublishedVersion("/uri");
    assertNotNull(publishedVersion);
    assertEquals("$LATEST", publishedVersion);
  }

  @Test
  public void verifyStatus_getTheStatus_ShouldNotBeNull() {
    Task task =
        getResource(
            objectMapper,
            "clouddriver/tasks/providers/aws/lambda/kato-status-success.json",
            Task.class);
    Mockito.when(katoService.lookupTaskWithUri(any())).thenReturn(task);

    LambdaCloudDriverTaskResults lambdaCloudDriverTaskResults = lambdaUtils.verifyStatus("/uri");
    assertNotNull(lambdaCloudDriverTaskResults);
  }

  @Test
  public void retrieveLambdaFromCache_ShouldNotBeNull() {
    List<LambdaDefinition> definitions =
        List.of(
            getResource(
                objectMapper,
                "clouddriver/tasks/providers/aws/lambda/functions.json",
                LambdaDefinition[].class));
    Mockito.when(oortService.getFunction("account1", "us-west-2", "lambdaApp-function-test"))
        .thenReturn(definitions);

    StageExecution stageExecutionMock = Mockito.mock(StageExecution.class);
    Map<String, Object> lambdaGetInput =
        ImmutableMap.of(
            "region", "us-west-2",
            "account", "account1",
            "functionName", "function-test");
    Mockito.when(stageExecutionMock.getContext()).thenReturn(lambdaGetInput);

    PipelineExecution pipelineExecutionMock = Mockito.mock(PipelineExecution.class);
    Mockito.when(pipelineExecutionMock.getApplication()).thenReturn("lambdaApp");
    Mockito.when(stageExecutionMock.getExecution()).thenReturn(pipelineExecutionMock);

    LambdaGetInput lgi =
        LambdaGetInput.builder()
            .region("us-west-2")
            .account("account1")
            .functionName("function-test")
            .build();
    Mockito.when(stageExecutionMock.mapTo(any())).thenReturn(lgi);

    LambdaDefinition lambdaDefinition = lambdaUtils.retrieveLambdaFromCache(stageExecutionMock);

    assertNotNull(lambdaDefinition);
    assertEquals("account1", lambdaDefinition.getAccount());
  }

  private <T> Map<String, Object> inputToMap(T input) {
    return objectMapper.convertValue(input, new TypeReference<>() {});
  }
}
