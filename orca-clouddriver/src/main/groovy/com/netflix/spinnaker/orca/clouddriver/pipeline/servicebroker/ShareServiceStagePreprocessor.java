/*
 *  Copyright 2019 Pivotal, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.pipeline.servicebroker;

import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;

/**
 * Supports generic modification of a Share Service stage.
 *
 * <p>Common use-cases: - injecting cloud-aware steps
 */
public interface ShareServiceStagePreprocessor {
  boolean supports(StageExecution stage);

  void addSteps(TaskNode.Builder builder, StageExecution stage);
}
