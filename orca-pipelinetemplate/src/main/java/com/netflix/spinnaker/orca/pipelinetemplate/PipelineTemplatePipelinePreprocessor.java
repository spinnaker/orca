/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipelinetemplate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.netflix.spinnaker.orca.extensionpoint.pipeline.PipelinePreprocessor;
import com.netflix.spinnaker.orca.pipelinetemplate.model.pipeline.PipelineBindings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

@Component
public class PipelineTemplatePipelinePreprocessor implements PipelinePreprocessor {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  @Override
  public Map<String, Object> process(Map<String, Object> pipeline) {
    TemplatedPipelineRequest request = mapper.convertValue(pipeline, TemplatedPipelineRequest.class);
    if (!request.isTemplatedPipelineRequest()) {
      return pipeline;
    }

    try {
      PipelineBindings bindings = mapper.readValue(getClass().getResourceAsStream("/" + request.getPipelineConfigurationUri()), PipelineBindings.class);
      Map<String, Object> result = bindings.getPipelineExecution(request.getPipelineExecutionName());
      for (String key : Arrays.asList("id", "application", "trigger")) {
        result.computeIfAbsent(key, pipeline::get);
      }

      log.info("Produced pipeline {}", mapper.writeValueAsString(result));
      return result;
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    }

  }

  private static class TemplatedPipelineRequest {
    String type;
    String id; //pipelineConfigId
    String application;
    String pipelineConfigurationUri; //URI once resolvers is sorted out
    String pipelineExecutionName; //optional, if there is more than one binding in the bindings doc a pipelineConfigurationUri
    Map<String, Object> trigger;

    public boolean isTemplatedPipelineRequest() {
      return "templatedPipeline".equals(type) && pipelineConfigurationUri != null;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getApplication() {
      return application;
    }

    public void setApplication(String application) {
      this.application = application;
    }

    public String getPipelineConfigurationUri() {
      return pipelineConfigurationUri;
    }

    public void setPipelineConfigurationUri(String pipelineConfigurationUri) {
      this.pipelineConfigurationUri = pipelineConfigurationUri;
    }

    public String getPipelineExecutionName() {
      return pipelineExecutionName;
    }

    public void setPipelineExecutionName(String pipelineExecutionName) {
      this.pipelineExecutionName = pipelineExecutionName;
    }

    public Map<String, Object> getTrigger() {
      return trigger;
    }

    public void setTrigger(Map<String, Object> trigger) {
      this.trigger = trigger;
    }
  }
}
