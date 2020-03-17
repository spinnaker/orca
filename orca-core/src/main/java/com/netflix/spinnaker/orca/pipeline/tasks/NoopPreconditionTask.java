/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.tasks;

import static com.netflix.spinnaker.orca.api.pipeline.TaskResult.SUCCEEDED;

import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import org.springframework.stereotype.Component;

@Component
public class NoopPreconditionTask implements PreconditionTask {

  @Override
  public TaskResult execute(StageExecution stage) {
    return SUCCEEDED;
  }

  public final String getPreconditionType() {
    return "noop";
  }
}
