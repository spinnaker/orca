/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.spinnaker.orca.pipeline.model.Pipeline;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import static java.lang.Boolean.parseBoolean;

@Component
class PipelineLauncher extends ExecutionLauncher<Pipeline> {

  private final PipelineStartTracker startTracker;

  @Autowired(required = false)
  public PipelineLauncher(ObjectMapper objectMapper,
                          @Qualifier("instanceInfo") InstanceInfo currentInstance,
                          ExecutionRunner runner,
                          Collection<StageDefinitionBuilder> stageDefinitionBuilders) {
    this(objectMapper, currentInstance, runner, stageDefinitionBuilders, null);
  }

  @Autowired(required = false)
  public PipelineLauncher(ObjectMapper objectMapper,
                          @Qualifier("instanceInfo") InstanceInfo currentInstance,
                          ExecutionRunner runner,
                          Collection<StageDefinitionBuilder> stageDefinitionBuilders,
                          PipelineStartTracker startTracker) {
    super(objectMapper, currentInstance, runner);
    this.startTracker = startTracker;
  }

  @SuppressWarnings("unchecked")
  @Override protected Pipeline parse(String configJson) throws IOException {
    // TODO: can we not just annotate the class properly to avoid all this?
    Map<String, Serializable> config = objectMapper.readValue(configJson, Map.class);
    return Pipeline
      .builder()
      .withApplication(getString(config, "application"))
      .withName(getString(config, "name"))
      .withPipelineConfigId(getString(config, "id"))
      .withTrigger((Map<String, Object>) config.get("trigger"))
      .withStages((List<Map<String, Object>>) config.get("stages"))
      .withAppConfig((Map<String, Serializable>) config.get("appConfig"))
      .withParallel(getBoolean(config, "parallel"))
      .withLimitConcurrent(getBoolean(config, "limitConcurrent"))
      .withKeepWaitingPipelines(getBoolean(config, "keepWaitingPipelines"))
      .withExecutingInstance(currentInstance)
      .withNotifications((List<Map<String, Object>>) config.get("notifications"))
      .build();
  }

  private boolean getBoolean(Map<String, ?> map, String key) {
    return parseBoolean(getString(map, key));
  }

  private String getString(Map<String, ?> map, String key) {
    return map.containsKey(key) ? map.get(key).toString() : null;
  }

  @Override protected boolean shouldQueue(Pipeline execution) {
    return startTracker == null ||
      execution.getPipelineConfigId() == null ||
      startTracker.queueIfNotStarted(execution.getPipelineConfigId(), execution.getId());
  }
}
