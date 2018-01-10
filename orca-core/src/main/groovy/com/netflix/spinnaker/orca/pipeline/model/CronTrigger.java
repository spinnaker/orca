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
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;

@JsonTypeName("cron")
public class CronTrigger extends Trigger {
  private final UUID id;
  private final String cronExpression;

  @JsonCreator
  public CronTrigger(
    @JsonProperty("id") @Nonnull UUID id,
    @JsonProperty("cronExpression") @Nonnull String cronExpression,
    @JsonProperty("user") @Nullable String user,
    @JsonProperty("parameters") @Nullable Map<String, Object> parameters,
    @JsonProperty("artifacts") @Nullable List<Artifact> artifacts
  ) {
    super(user, parameters, artifacts, false);
    this.id = id;
    this.cronExpression = cronExpression;
  }

  public UUID getId() {
    return id;
  }

  public String getCronExpression() {
    return cronExpression;
  }

  @Override public boolean equals(Object o) {
    if (!super.equals(o)) return false;
    CronTrigger that = (CronTrigger) o;
    return Objects.equals(id, that.id) &&
      Objects.equals(cronExpression, that.cronExpression);
  }

  @Override public int hashCode() {
    return Objects.hash(super.hashCode(), id, cronExpression);
  }
}
