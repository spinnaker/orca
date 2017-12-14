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

package com.netflix.spinnaker.orca.echo.model;

import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.netflix.spinnaker.orca.pipeline.model.Trigger;

@JsonTypeName("cron")
public class CronTrigger implements Trigger {
  private final UUID id;
  private final String cronExpression;
  private final boolean enabled;
  private final String user;
  private final Map<String, Object> parameters;

  @JsonCreator
  public CronTrigger(
    @JsonProperty("id") UUID id,
    @JsonProperty("cronExpression") String cronExpression,
    @JsonProperty("enabled") boolean enabled,
    @JsonProperty("user") String user,
    @JsonProperty("parameters") Map<String, Object> parameters
  ) {
    this.id = id;
    this.cronExpression = cronExpression;
    this.enabled = enabled;
    this.user = user;
    this.parameters = parameters;
  }

  @Nonnull @Override public String getType() {
    return "cron";
  }
}
