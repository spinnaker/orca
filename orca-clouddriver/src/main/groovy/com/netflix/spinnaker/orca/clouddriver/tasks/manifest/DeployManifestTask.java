/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.OortService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import retrofit.client.Response;

@Component
@Slf4j
public class DeployManifestTask extends AbstractCloudProviderAwareTask implements Task {
  @Autowired
  KatoService kato;

  @Autowired
  OortService oort;

  @Autowired
  ArtifactResolver artifactResolver;

  @Autowired
  ObjectMapper objectMapper;

  Yaml yamlParser = new Yaml();

  @Autowired
  ContextParameterProcessor contextParameterProcessor;

  RetrySupport retrySupport = new RetrySupport();

  public static final String TASK_NAME = "deployManifest";

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    String credentials = getCredentials(stage);
    String cloudProvider = getCloudProvider(stage);

    List<Artifact> artifacts = artifactResolver.getArtifacts(stage);
    Map task = new HashMap(stage.getContext());
    String artifactSource = (String) task.get("source");
    if (StringUtils.isNotEmpty(artifactSource) && artifactSource.equals("artifact")) {
      Artifact manifestArtifact = artifactResolver.getBoundArtifactForId(stage, task.get("manifestArtifactId").toString());

      if (manifestArtifact == null) {
        throw new IllegalArgumentException("No artifact could be bound to '" + task.get("manifestArtifactId") + "'");
      }

      log.info("Using {} as the manifest to be deployed", manifestArtifact);

      manifestArtifact.setArtifactAccount((String) task.get("manifestArtifactAccount"));
      Response manifestText = retrySupport.retry(() -> oort.fetchArtifact(manifestArtifact.getArtifactAccount(),
          manifestArtifact.getType(),
          manifestArtifact.getReference()
      ), 5, 1000, true);

      try {
        Map manifest = objectMapper.convertValue(yamlParser.load(manifestText.getBody().in()), Map.class);
        Map<String, Object> manifestWrapper = new HashMap<>();
        manifestWrapper.put("manifest", manifest);

        manifestWrapper = contextParameterProcessor.process(
            manifestWrapper,
          contextParameterProcessor.buildExecutionContext(stage, true),
          true
        );

        if (manifestWrapper.containsKey("expressionEvaluationSummary")) {
          throw new IllegalStateException("Failure evaluating manifest expressions: " + manifestWrapper.get("expressionEvaluationSummary"));
        }

        task.put("manifest", manifestWrapper.get("manifest"));
        task.put("source", "text");
      } catch (IOException e) {
        throw new IllegalArgumentException("Failed to read manifest from '" + manifestArtifact + "' as '" + manifestText + "': " + e.getMessage(), e);
      }
    }

    List<String> requiredArtifactIds = (List<String>) task.get("requiredArtifactIds");
    requiredArtifactIds = requiredArtifactIds == null ? new ArrayList<>() : requiredArtifactIds;
    List<Artifact> requiredArtifacts = requiredArtifactIds.stream()
        .map(id -> artifactResolver.getBoundArtifactForId(stage, id))
        .collect(Collectors.toList());

    log.info("Deploying {} artifacts within the provided manifest", requiredArtifacts);

    task.put("requiredArtifacts", requiredArtifacts);
    task.put("optionalArtifacts", artifacts);
    Map<String, Map> operation = new ImmutableMap.Builder<String, Map>()
        .put(TASK_NAME, task)
        .build();

    TaskId taskId = kato.requestOperations(cloudProvider, Collections.singletonList(operation)).toBlocking().first();

    Map<String, Object> outputs = new ImmutableMap.Builder<String, Object>()
        .put("kato.result.expected", true)
        .put("kato.last.task.id", taskId)
        .put("deploy.account.name", credentials)
        .build();

    return new TaskResult(ExecutionStatus.SUCCEEDED, outputs);
  }
}
