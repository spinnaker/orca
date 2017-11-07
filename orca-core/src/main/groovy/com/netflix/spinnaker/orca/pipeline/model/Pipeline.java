/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.pipeline.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spectator.api.Registry;

public class Pipeline extends Execution<Pipeline> {

  public Pipeline(String application) {
    super(application);
  }

  @JsonCreator
  public Pipeline(
    @JsonProperty("id") String id,
    @JsonProperty("application") String application) {
    super(id, application);
  }

  public static PipelineBuilder builder(String application, Registry registry) {
    return new PipelineBuilder(application, registry);
  }

  public static PipelineBuilder builder(String application) {
    return new PipelineBuilder(application);
  }
}
