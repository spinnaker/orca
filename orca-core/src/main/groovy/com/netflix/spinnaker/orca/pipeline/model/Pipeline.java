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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.netflix.spectator.api.Registry;

public class Pipeline extends Execution<Pipeline> {

  public Pipeline(String application) {
    super(application);
  }

  public Pipeline(String id, String application) {
    super(id, application);
  }

  private String pipelineConfigId;

  public @Nullable String getPipelineConfigId() {
    return pipelineConfigId;
  }

  public void setPipelineConfigId(@Nullable String pipelineConfigId) {
    this.pipelineConfigId = pipelineConfigId;
  }

  private final Map<String, Object> trigger = new HashMap<>();

  public @Nonnull Map<String, Object> getTrigger() {
    return trigger;
  }

  private final List<Map<String, Object>> notifications = new ArrayList<>();

  public @Nonnull List<Map<String, Object>> getNotifications() {
    return notifications;
  }

  private final Map<String, Serializable> initialConfig = new HashMap<>();

  public @Nonnull Map<String, Serializable> getInitialConfig() {
    return initialConfig;
  }

  public static PipelineBuilder builder(String application, Registry registry) {
    return new PipelineBuilder(application, registry);
  }

  public static PipelineBuilder builder(String application) {
    return new PipelineBuilder(application);
  }
}
