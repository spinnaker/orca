/*
 * Copyright 2024 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.orca.bakery.tasks;

import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.web.selector.v2.SelectableService;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.bakery.BakerySelector;
import com.netflix.spinnaker.orca.bakery.api.BakeRequest;
import com.netflix.spinnaker.orca.bakery.api.BakeStatus;
import com.netflix.spinnaker.orca.bakery.api.BakeryService;
import com.netflix.spinnaker.orca.bakery.config.BakeryConfigurationProperties;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class CreateBakeTaskTest {

  private StageExecution stageExecution = new StageExecutionImpl();

  private String application = "testapp";

  private BakerySelector bakerySelector = null;

  private BakeryService bakeryService = mock(BakeryService.class);

  private CreateBakeTask createBakeTask = new CreateBakeTask();

  private Front50Service front50Service = mock(Front50Service.class);

  private BakeStatus runningStatus = new BakeStatus();

  private ArtifactUtils artifactUtils = mock(ArtifactUtils.class);

  PipelineExecution pipelineExecution =
      new PipelineExecutionImpl(ExecutionType.PIPELINE, application);

  @BeforeEach
  public void setup() {

    stageExecution.setExecution(pipelineExecution);
    runningStatus.setId(UUID.randomUUID().toString());
    runningStatus.setState(BakeStatus.State.RUNNING);

    BakeryConfigurationProperties bakeryConfigurationProperties =
        new BakeryConfigurationProperties();
    bakeryConfigurationProperties.setExtractBuildDetails(true);
    bakeryConfigurationProperties.setSelectorEnabled(true);
    bakeryConfigurationProperties.setAllowMissingPackageInstallation(false);
    bakeryConfigurationProperties.setRoscoApisEnabled(false);

    bakerySelector =
        spy(
            new BakerySelector(
                bakeryService, bakeryConfigurationProperties, (bakerySvc) -> bakeryService));
  }

  @Test
  void testWarningLogsWhenSpinnakerHttpExceptionOccurs() {

    var url = "https://front50service.com/v2/applications/" + application;

    Response response =
        new Response(
            url,
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.name(),
            Collections.emptyList(),
            null);
    RetrofitError httpError = RetrofitError.httpError(url, response, null, null);

    when(front50Service.get(application)).thenThrow(httpError);

    SelectableService.SelectedService<BakeryService> selectedBakeryService =
        new SelectableService.SelectedService<BakeryService>(
            bakeryService, Collections.EMPTY_MAP, Collections.emptyList());

    createBakeTask.setBakerySelector(bakerySelector);
    createBakeTask.setArtifactUtils(artifactUtils);
    createBakeTask.setMapper(new ObjectMapper());

    when(bakerySelector.select(stageExecution)).thenReturn(selectedBakeryService);
    System.out.println("selected bakery service in tests : " + selectedBakeryService);
    System.out.println("bakery selector in tests : " + bakerySelector);
    System.out.println("bakery service in tests : " + bakeryService);
    when(artifactUtils.getAllArtifacts(pipelineExecution)).thenReturn(Collections.emptyList());
    when(bakeryService.createBake(anyString(), any(BakeRequest.class), anyString()))
        .thenReturn(runningStatus);

    //    createBakeTask.execute(stageExecution);

  }
}
