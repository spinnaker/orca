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
import com.netflix.spinnaker.orca.TestUtils;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheService;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverCacheStatusService;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws.lambda.model.input.LambdaCacheRefreshInput;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import retrofit.client.Header;
import retrofit.client.Response;
import retrofit.converter.Converter;
import retrofit.converter.JacksonConverter;
import retrofit.mime.TypedInput;

public class LambdaCacheRefreshTaskTest {

  @InjectMocks private LambdaCacheRefreshTask lambdaCacheRefreshTask;

  @Mock private CloudDriverCacheService cloudDriverCacheService;

  @Mock private CloudDriverCacheStatusService cloudDriverCacheStatusService;

  @Mock private StageExecution stageExecution;

  @Mock private PipelineExecution pipelineExecution;

  @Mock private ObjectMapper objectMapper;

  private final Converter converter = new JacksonConverter(objectMapper);
  private final List<Header> responseHeaders =
      List.of(new Header(HttpHeaders.CONTENT_TYPE, "application/json"));

  @BeforeEach
  void init() {
    MockitoAnnotations.openMocks(this);

    pipelineExecution.setApplication("lambdaApp");
    Mockito.when(stageExecution.getExecution()).thenReturn(pipelineExecution);
    Mockito.when(stageExecution.getContext()).thenReturn(new HashMap<>());

    // would rather just call the real method here, but StageExecution ends up being
    // an abstract class, so forced to mock
    LambdaCacheRefreshInput input = LambdaCacheRefreshInput.builder().appName("lambdaApp").build();
    Mockito.when(stageExecution.mapTo(any())).thenReturn(input);

    TypedInput mockTypedInput = new TestUtils.MockTypedInput(converter, Map.of());
    Response response =
        new Response("", HttpStatus.OK.value(), "", responseHeaders, mockTypedInput);
    Mockito.when(cloudDriverCacheService.forceCacheUpdate(any(), any(), any()))
        .thenReturn(response);
  }

  @Test
  public void execute_ShouldForceCacheRefreshAndGet200Code_SUCCEDED() {
    Map<String, Object> map = Map.of();
    Mockito.when(cloudDriverCacheStatusService.pendingForceCacheUpdatesById(any(), any(), any()))
        .thenReturn(List.of(map));

    assertEquals(
        ExecutionStatus.SUCCEEDED, lambdaCacheRefreshTask.execute(stageExecution).getStatus());
  }

  @Test
  public void execute_ShouldWaitForCacheToComplete_CachingShouldBeCompleted_SUCCEEDED() {
    Map<String, Object> map =
        Map.of("processedCount", 1, "cacheTime", System.currentTimeMillis() + 15 * 1000);
    Mockito.when(cloudDriverCacheStatusService.pendingForceCacheUpdatesById(any(), any(), any()))
        .thenReturn(List.of(map));

    assertEquals(
        ExecutionStatus.SUCCEEDED, lambdaCacheRefreshTask.execute(stageExecution).getStatus());
  }

  @Test
  public void
      forceCacheRefresh_waitForCacheToComplete_NotFoundAndThenCachingShouldBeCompleted_SUCCEEDED() {
    Map<String, Object> mapSecondCall =
        Map.of("processedCount", 1, "cacheTime", System.currentTimeMillis() + 15 * 1000);
    Mockito.when(cloudDriverCacheStatusService.pendingForceCacheUpdatesById(any(), any(), any()))
        .thenReturn(List.of())
        .thenReturn(List.of(mapSecondCall));

    assertEquals(
        ExecutionStatus.SUCCEEDED, lambdaCacheRefreshTask.execute(stageExecution).getStatus());
  }

  @Test
  public void
      forceCacheRefresh_waitForCacheToComplete_ShouldRetryAndThenCachingShouldBeCompleted_SUCCEEDED() {
    Map<String, Object> mapFirstCall = Map.of("processedCount", 0);
    Map<String, Object> mapSecondCall =
        Map.of("processedCount", 1, "cacheTime", System.currentTimeMillis() + 15 * 1000);
    Mockito.when(cloudDriverCacheStatusService.pendingForceCacheUpdatesById(any(), any(), any()))
        .thenReturn(List.of(mapFirstCall))
        .thenReturn(List.of(mapSecondCall));

    assertEquals(
        ExecutionStatus.SUCCEEDED, lambdaCacheRefreshTask.execute(stageExecution).getStatus());
  }
}
