/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.clouddriver.config.PreconfiguredJobStageParameter;
import com.netflix.spinnaker.orca.clouddriver.config.PreconfiguredJobStageProperties;
import com.netflix.spinnaker.orca.clouddriver.exception.PreconfiguredJobNotFoundException;
import com.netflix.spinnaker.orca.clouddriver.service.JobService;
import com.netflix.spinnaker.orca.pipeline.TaskNode;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import groovy.util.Eval;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
class PreconfiguredJobStage extends RunJobStage {

  private JobService jobService;
  private ObjectMapper objectMapper;

  public PreconfiguredJobStage(Optional<JobService> optionalJobService) {
    this.jobService = optionalJobService.orElse(null);
    this.objectMapper = new ObjectMapper();
  }

  @Override
  public void taskGraph(Stage stage, TaskNode.Builder builder) {
    PreconfiguredJobStageProperties preconfiguredJob = jobService.getPreconfiguredStages().stream()
      .filter( p -> stage.getType().equals(p.getType()))
      .findFirst()
      .orElseThrow(() -> new PreconfiguredJobNotFoundException(stage.getType()));

    stage.setContext(overrideIfNotSetInContextAndOverrideDefault(stage.getContext(), preconfiguredJob));
    super.taskGraph(stage, builder);
  }

  private Map<String, Object> overrideIfNotSetInContextAndOverrideDefault(Map<String, Object> context, PreconfiguredJobStageProperties preconfiguredJob) {
    Map<String, Object> preconfiguredMap = objectMapper.convertValue(preconfiguredJob, Map.class);
    preconfiguredJob.getOverridableFields().stream().forEach(field -> {
        Object contextValue = context.containsKey(field) ? context.get(field) : null;
        Object preconfiguredValue = preconfiguredMap.containsKey(field) ? preconfiguredMap.get(field) : null;
      if (contextValue == null || preconfiguredValue != null) {
        context.put(field, preconfiguredValue);
      }
    });

    preconfiguredJob.getParameters().stream().forEach( parameter -> {
      if (parameter.getDefaultValue() != null) {
        Eval.xy(context, parameter.getDefaultValue(), String.format("x.%s = y.toString()", parameter.getMapping()));
      }
    });

    if (context.containsKey("parameters") && context.get("parameters") != null) {
      Map<String, String> contextParameters = (Map<String, String>) context.get("parameters");
      contextParameters.entrySet().stream()
        .forEach( entry -> {
          String parameterKey = entry.getKey();
          String parameterValue = entry.getValue();
          PreconfiguredJobStageParameter parameter = preconfiguredJob.getParameters().stream()
            .filter( p ->  p.getName().equals(parameterKey))
            .findFirst()
            .orElse(null);

          if (parameter != null) {
            Eval.xy(context, parameterValue, String.format("x.%s = y.toString()", parameter.getMapping()));
          }
      });
    }

    context.put("preconfiguredJobParameters", preconfiguredJob.getParameters());
    return context;
  }
}
