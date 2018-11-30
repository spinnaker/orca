/*
 * Copyright 2018 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.pipelinetemplate.v2schema;

import com.netflix.spinnaker.orca.pipelinetemplate.TemplatedPipelineRequest;
import com.netflix.spinnaker.orca.pipelinetemplate.generator.V2ExecutionGenerator;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.TemplateMerge;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.NamedHashMap;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration;
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2PipelineTemplate;

import java.util.*;
import java.util.stream.Collectors;

public class V2SchemaExecutionGenerator implements V2ExecutionGenerator {

  @Override
  public Map<String, Object> generate(V2PipelineTemplate template, TemplateConfiguration configuration, TemplatedPipelineRequest request) {
    Map<String, Object> pipeline = template.getPipeline();
    pipeline.put("id", Optional.ofNullable(request.getId()).orElse(Optional.ofNullable(configuration.getPipeline().getPipelineConfigId()).orElse("unknown")));
    pipeline.put("application", configuration.getPipeline().getApplication());
    if (request.getExecutionId() != null) {
      pipeline.put("executionId", request.getExecutionId());
    }
    pipeline.put("name", Optional.ofNullable(configuration.getPipeline().getName()).orElse("Unnamed Execution"));

    if (!pipeline.containsKey("limitConcurrent")) {
      pipeline.put("limitConcurrent", request.isLimitConcurrent());
    }
    if (!pipeline.containsKey("keepWaitingPipelines")) {
      pipeline.put("keepWaitingPipelines", request.isKeepWaitingPipelines());
    }

    addNotifications(pipeline, template, configuration);
    addParameters(pipeline, template, configuration);
    addTriggers(pipeline, template, configuration);
    pipeline.put("templateVariables", configuration.getPipeline().getVariables());

    if (request.getTrigger() != null && !request.getTrigger().isEmpty()) {
      pipeline.put("trigger", request.getTrigger());
    }

    return pipeline;
  }

  private void addNotifications(Map<String, Object> pipeline, V2PipelineTemplate template, TemplateConfiguration configuration) {
    if (configuration.getConfiguration().getInherit().contains("notifications")) {
      pipeline.put(
        "notifications",
        TemplateMerge.mergeDistinct(
          (List<HashMap<String, Object>>) template.getPipeline().get("notifications"),
          castAllToHashMap(configuration.getConfiguration().getNotifications())
        )
      );
    } else {
      pipeline.put(
        "notifications",
        Optional.ofNullable(configuration.getConfiguration().getNotifications()).orElse(Collections.emptyList())
      );
    }
  }

  private void addParameters(Map<String, Object> pipeline, V2PipelineTemplate template, TemplateConfiguration configuration) {
    if (configuration.getConfiguration().getInherit().contains("parameters")) {
      pipeline.put(
        "parameterConfig",
        TemplateMerge.mergeDistinct(
          (List<HashMap<String, Object>>) template.getPipeline().get("parameterConfig"),
          castAllToHashMap(configuration.getConfiguration().getParameters())
        )
      );
    } else {
      pipeline.put(
        "parameterConfig",
        Optional.ofNullable(configuration.getConfiguration().getParameters()).orElse(Collections.emptyList())
      );
    }
  }

  private void addTriggers(Map<String, Object> pipeline,
                           V2PipelineTemplate template,
                           TemplateConfiguration configuration) {
    if (configuration.getConfiguration().getInherit().contains("triggers")) {
      pipeline.put(
        "triggers",
        TemplateMerge.mergeDistinct(
          (List<HashMap<String, Object>>) template.getPipeline().get("triggers"),
          castAllToHashMap(configuration.getConfiguration().getTriggers())
        )
      );
    } else {
      pipeline.put(
        "triggers",
        Optional.ofNullable(configuration.getConfiguration().getTriggers()).orElse(Collections.emptyList())
      );
    }
  }

  // TODO(jacobkiefer): Consider adding a v2 context class to simplify processing in the V2SchemaExecutionGenerator.
  // This results in a TemplateContext class with overly-concrete attributes that complicate things.
  private List<HashMap<String, Object>> castAllToHashMap(List<NamedHashMap> namedHashMaps) {
    return namedHashMaps.stream().map(nhm -> (HashMap<String, Object>) nhm).collect(Collectors.toList());
  }
}
