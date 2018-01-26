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

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("dryrun")
public class DryRunTrigger extends Trigger {

  private final Execution lastSuccessfulExecution;

  @JsonCreator
  public DryRunTrigger(
    @JsonProperty("lastSuccessfulExecution") Execution lastSuccessfulExecution
  ) {
    super(null, null, null);
    this.lastSuccessfulExecution = lastSuccessfulExecution;
  }

  public Execution getLastSuccessfulExecution() {
    return lastSuccessfulExecution;
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    DryRunTrigger that = (DryRunTrigger) o;
    return Objects.equals(lastSuccessfulExecution.getId(), that.lastSuccessfulExecution.getId());
  }

  @Override public int hashCode() {
    return Objects.hash(super.hashCode(), lastSuccessfulExecution.getId());
  }

  @Override public String toString() {
    return "DryRunTrigger{" +
      super.toString() +
      ", lastSuccessfulExecution=" + lastSuccessfulExecution.getId() +
      '}';
  }
}
