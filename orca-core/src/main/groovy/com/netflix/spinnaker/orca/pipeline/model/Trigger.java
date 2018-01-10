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

package com.netflix.spinnaker.orca.pipeline.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Objects.hash;

@JsonTypeInfo(use = Id.NAME, include = As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
  @Type(CronTrigger.class),
  @Type(DockerTrigger.class),
  @Type(DryRunTrigger.class),
  @Type(GitTrigger.class),
  @Type(JenkinsTrigger.class),
  @Type(ManualTrigger.class),
  @Type(PipelineTrigger.class)
  // to add more sub-types without modifying this class you can autowire the
  // [ObjectMapper] then call [ObjectMapper#registerSubtypes].
})
public abstract class Trigger {

  private final String user;
  private final Map<String, Object> parameters;
  private final List<Artifact> artifacts;
  private final boolean rebake;
  private final String type;

  protected Trigger(
    @Nullable String user,
    @Nullable Map<String, Object> parameters,
    @Nullable List<Artifact> artifacts,
    boolean rebake
  ) {
    this.type = getClass().getAnnotation(JsonTypeName.class).value();
    this.user = user == null ? "[anonymous]" : user;
    this.parameters = parameters == null ? emptyMap() : parameters;
    this.artifacts = artifacts == null ? emptyList() : artifacts;
    this.rebake = rebake;
  }

  public @Nonnull String getType() {
    return type;
  }

  public final @Nonnull String getUser() {
    return user;
  }

  public final @Nonnull Map<String, Object> getParameters() {
    return parameters;
  }

  public final @Nonnull List<Artifact> getArtifacts() {
    return artifacts;
  }

  public final boolean isRebake() {
    return rebake;
  }

  @Override public boolean equals(Object obj) {
    if (obj == null) { return false; }
    if (obj == this) { return true; }
    if (!Trigger.class.isAssignableFrom(obj.getClass())) { return false; }
    Trigger other = (Trigger) obj;
    return Objects.equals(type, other.type)
      && Objects.equals(user, other.user)
      && Objects.equals(parameters, other.parameters)
      && Objects.equals(artifacts, other.artifacts)
      && Objects.equals(rebake, other.rebake);
  }

  @Override public int hashCode() {
    return hash(type, user, parameters, artifacts, rebake);
  }
}
