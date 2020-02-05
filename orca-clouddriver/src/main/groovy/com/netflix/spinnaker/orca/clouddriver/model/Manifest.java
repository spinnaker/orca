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

package com.netflix.spinnaker.orca.clouddriver.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Value;

@JsonDeserialize(builder = Manifest.ManifestBuilder.class)
@Value
public final class Manifest {
  private final Map<String, Object> manifest;
  private final List<Artifact> artifacts;
  private final Status status;
  private final String name;
  private final List<String> warnings;

  @Builder(toBuilder = true)
  private Manifest(
      Map<String, Object> manifest,
      List<Artifact> artifacts,
      Status status,
      String name,
      List<String> warnings) {
    this.manifest = manifest;
    this.artifacts = artifacts;
    this.status = status;
    this.name = name;
    this.warnings = warnings;
  }

  @JsonDeserialize(builder = Manifest.Status.StatusBuilder.class)
  @Value
  public static final class Status {
    private final Condition stable;
    private final Condition failed;

    @Builder(toBuilder = true)
    private Status(Condition stable, Condition failed) {
      this.stable = stable;
      this.failed = failed;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static final class StatusBuilder {}
  }

  @Value
  public static final class Condition {
    private final boolean state;
    private final String message;

    public Condition(
        @JsonProperty("state") boolean state, @Nullable @JsonProperty("message") String message) {
      this.state = state;
      this.message = message;
    }
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static final class ManifestBuilder {}
}
