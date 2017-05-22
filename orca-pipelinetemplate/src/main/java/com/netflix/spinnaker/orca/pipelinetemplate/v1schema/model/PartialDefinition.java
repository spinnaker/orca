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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PartialDefinition implements Identifiable {

  private String id;
  private String usage;
  private List<NamedHashMap> variables = new ArrayList<>();
  private List<StageDefinition> stages = new ArrayList<>();

  private Map<String, List<StageDefinition>> renderedPartials = new HashMap<>();

  @Override
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getUsage() {
    return usage;
  }

  public void setUsage(String usage) {
    this.usage = usage;
  }

  public List<NamedHashMap> getVariables() {
    return variables;
  }

  public void setVariables(List<NamedHashMap> variables) {
    this.variables = variables;
  }

  public List<StageDefinition> getStages() {
    return stages;
  }

  public void setStages(List<StageDefinition> stages) {
    this.stages = stages;
  }

  public Map<String, List<StageDefinition>> getRenderedPartials() {
    return renderedPartials;
  }
}
