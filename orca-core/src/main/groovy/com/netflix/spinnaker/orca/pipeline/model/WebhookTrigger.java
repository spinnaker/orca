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

package com.netflix.spinnaker.orca.pipeline.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import com.fasterxml.jackson.annotation.*;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;

@JsonTypeName("webhook")
public class WebhookTrigger extends Trigger {

  @JsonCreator
  protected WebhookTrigger(
    @JsonProperty("user") @Nullable String user,
    @JsonProperty("parameters") @Nullable Map<String, Object> parameters,
    @JsonProperty("artifacts") @Nullable List<Artifact> artifacts,
    @JsonProperty("rebake") boolean rebake
  ) {
    super(user, parameters, artifacts, rebake);
  }

  private Map<String, Object> properties = new HashMap<>();

  @JsonAnySetter
  public void addProperty(String name, Object value) {
    properties.put(name, value);
  }

  @JsonAnyGetter
  public Map<String, Object> getProperties() {
    return properties;
  }
}
