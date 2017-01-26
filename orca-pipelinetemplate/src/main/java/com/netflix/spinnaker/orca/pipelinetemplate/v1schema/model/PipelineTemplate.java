/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model;

import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.PipelineTemplateVisitor;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PipelineTemplate {

  private String schema;
  private String id;
  private String source;
  private List<Variable> variables;
  private Configuration configuration;
  private List<StageDefinition> stages;
  private List<TemplateModule> modules;

  public static class Variable implements NamedContent {
    private String name;
    private String description;
    private String type;
    private Object defaultValue;

    @Override
    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public Object getDefaultValue() {
      return defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
      this.defaultValue = defaultValue;
    }
  }

  public static class Configuration {
    private Map<String, Object> concurrentExecutions;
    private List<NamedHashMap> triggers;
    private List<NamedHashMap> parameters;
    private List<NamedHashMap> notifications;

    public Map<String, Object> getConcurrentExecutions() {
      return concurrentExecutions;
    }

    public void setConcurrentExecutions(Map<String, Object> concurrentExecutions) {
      this.concurrentExecutions = concurrentExecutions;
    }

    public List<NamedHashMap> getTriggers() {
      return triggers;
    }

    public void setTriggers(List<NamedHashMap> triggers) {
      this.triggers = triggers;
    }

    public List<NamedHashMap> getParameters() {
      return parameters;
    }

    public void setParameters(List<NamedHashMap> parameters) {
      this.parameters = parameters;
    }

    public List<NamedHashMap> getNotifications() {
      return notifications;
    }

    public void setNotifications(List<NamedHashMap> notifications) {
      this.notifications = notifications;
    }
  }

  public String getSchema() {
    return schema;
  }

  public void setSchema(String schema) {
    this.schema = schema;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public List<Variable> getVariables() {
    return variables;
  }

  public void setVariables(List<Variable> variables) {
    this.variables = variables;
  }

  public Configuration getConfiguration() {
    return configuration;
  }

  public void setConfiguration(Configuration configuration) {
    this.configuration = configuration;
  }

  public List<StageDefinition> getStages() {
    return Optional.ofNullable(stages).orElse(Collections.emptyList());
  }

  public void setStages(List<StageDefinition> stages) {
    this.stages = stages;
  }

  public List<TemplateModule> getModules() {
    return modules;
  }

  public void setModules(List<TemplateModule> modules) {
    this.modules = modules;
  }

  public void accept(PipelineTemplateVisitor visitor) {
    visitor.visitPipelineTemplate(this);
  }
}
