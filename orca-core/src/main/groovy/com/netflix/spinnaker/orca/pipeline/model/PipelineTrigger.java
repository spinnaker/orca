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
import com.netflix.spinnaker.orca.ExecutionStatus;

/**
 * The trigger used when a pipeline is triggered by another pipeline completing.
 */
@JsonTypeName("pipeline")
public final class PipelineTrigger extends Trigger {

  @JsonCreator
  public PipelineTrigger(
    @Nonnull @JsonProperty("parentExecution") Execution parentExecution,
    @JsonProperty("isPipeline") boolean isPipeline,
    @Nullable @JsonProperty("parentPipelineId") String parentPipelineId,
    @Nullable @JsonProperty("parentPipelineName") String parentPipelineName,
    @Nullable @JsonProperty("parentPipelineApplication")
      String parentPipelineApplication,
    @Nullable @JsonProperty("parentPipelineStageId")
      String parentPipelineStageId,
    @Nullable @JsonProperty("parentStatus") ExecutionStatus parentStatus,
    @Nullable @JsonProperty("user") String user,
    @Nullable @JsonProperty("parameters") Map<String, Object> parameters,
    @Nonnull @JsonProperty("enabled") boolean enabled
  ) {
    super(user, parameters, enabled);
    this.parentExecution = parentExecution;
    this.isPipeline = isPipeline;
    this.parentPipelineId = parentPipelineId;
    this.parentPipelineName = parentPipelineName;
    this.parentPipelineApplication = parentPipelineApplication;
    this.parentPipelineStageId = parentPipelineStageId;
    this.parentStatus = parentStatus;
  }

  private final Execution parentExecution;
  private final boolean isPipeline;
  private final String parentPipelineId;
  private final String parentPipelineName;
  private final String parentPipelineApplication;
  private final String parentPipelineStageId;
  private final ExecutionStatus parentStatus;

  public @Nonnull Execution getParentExecution() {
    return parentExecution;
  }

  public boolean isPipeline() {
    return isPipeline;
  }

  public @Nullable String getParentPipelineId() {
    return parentPipelineId;
  }

  public @Nullable String getParentPipelineName() {
    return parentPipelineName;
  }

  public @Nullable String getParentPipelineApplication() {
    return parentPipelineApplication;
  }

  public @Nullable String getParentPipelineStageId() {
    return parentPipelineStageId;
  }

  public @Nullable ExecutionStatus getParentStatus() {
    return parentStatus;
  }

}
