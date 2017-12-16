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

import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("docker")
public class DockerTrigger extends Trigger {

  private final String account;
  private final String organization;
  private final String registry;
  private final String repository;
  private final String tag;

  @JsonCreator
  public DockerTrigger(
    @JsonProperty("account") @Nonnull String account,
    @JsonProperty("organization") @Nonnull String organization,
    @JsonProperty("registry") @Nonnull String registry,
    @JsonProperty("repository") @Nonnull String repository,
    @JsonProperty("tag") @Nonnull String tag,
    @JsonProperty("user") @Nullable String user,
    @JsonProperty("parameters") @Nullable Map<String, Object> parameters
  ) {
    super(user, parameters);
    this.account = account;
    this.organization = organization;
    this.registry = registry;
    this.repository = repository;
    this.tag = tag;
  }

  public @Nonnull String getAccount() {
    return account;
  }

  public @Nonnull String getOrganization() {
    return organization;
  }

  public @Nonnull String getRegistry() {
    return registry;
  }

  public @Nonnull String getRepository() {
    return repository;
  }

  public @Nonnull String getTag() {
    return tag;
  }
}
