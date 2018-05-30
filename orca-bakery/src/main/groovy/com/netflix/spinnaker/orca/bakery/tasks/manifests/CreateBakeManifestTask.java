/*
 * Copyright 2018 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.orca.bakery.tasks.manifests;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.bakery.api.BakeryService;
import com.netflix.spinnaker.orca.bakery.api.manifests.helm.HelmBakeManifestRequest;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class CreateBakeManifestTask implements RetryableTask {
  @Override
  public long getBackoffPeriod() {
    return 30000;
  }

  @Override
  public long getTimeout() {
    return 300000;
  }

  @Autowired(required = false)
  BakeryService bakery;

  @Autowired
  ArtifactResolver artifactResolver;

  @Autowired
  ObjectMapper objectMapper;

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    Map<String, Object> context = stage.getContext();

    List<ExpectedArtifactPair> expectedArtifacts = objectMapper.convertValue(context.get("inputArtifacts"), new TypeReference<List<ExpectedArtifactPair>>() {});
    List<Artifact> inputArtifacts;

    if (expectedArtifacts == null || expectedArtifacts.isEmpty()) {
      throw new IllegalArgumentException("At least one expected artifact to bake must be supplied");
    }

    inputArtifacts = expectedArtifacts.stream()
        .map(p -> {
          Artifact a = artifactResolver.getBoundArtifactForId(stage, p.getId());
          if (a == null) {
            throw new IllegalArgumentException(stage.getExecution().getId() + ": Expected artifact " + p.getId() + " could not be found in the execution");
          }
          a.setArtifactAccount(p.getAccount());
          return a;
        }).collect(Collectors.toList());

    HelmBakeManifestRequest request = new HelmBakeManifestRequest();
    request.setInputArtifacts(inputArtifacts);
    request.setTemplateRenderer((String) context.get("templateRenderer"));
    request.setOutputName((String) context.get("outputName"));
    request.setOverrides(objectMapper.convertValue(context.get("overrides"), new TypeReference<Map<String, Object>>() { }));
    request.setNamespace((String) context.get("namespace"));

    log.info("Requesting {}", request);
    Artifact result = bakery.bakeManifest(request);

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("artifacts", Collections.singleton(result));

    return new TaskResult(ExecutionStatus.SUCCEEDED, outputs, outputs);
  }

  @Data
  private static class ExpectedArtifactPair {
    String id;
    String account;
  }
}
