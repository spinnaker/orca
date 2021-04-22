/*
 * Copyright 2021 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.config;

import com.netflix.spinnaker.orca.controllers.TaskController;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("tasks.controller")
@Data
public class TaskControllerConfigurationProperties {
  /**
   * flag to enable speeding up execution retrieval. This is applicable for the {@link
   * TaskController#getPipelinesForApplication(String, int, String, Boolean)} endpoint
   */
  boolean optimizeExecutionRetrieval = false;

  /** moved this to here. Earlier definition was in the {@link TaskController} class */
  int daysOfExecutionHistory = 14;

  /** moved this to here. Earlier definition was in the {@link TaskController} class */
  int numberOfOldPipelineExecutionsToInclude = 2;

  public boolean getOptimizeExecutionRetrieval() {
    return this.optimizeExecutionRetrieval;
  }

  // need to set this explicitly so that it works in kotlin tests
  public void setOptimizeExecutionRetrieval(boolean optimizeExecutionRetrieval) {
    this.optimizeExecutionRetrieval = optimizeExecutionRetrieval;
  }
}
