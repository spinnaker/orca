/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.orca.pipeline;

import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.tasks.AcquireLockTask;
import javax.annotation.Nonnull;
import org.springframework.stereotype.Component;

@Component
public class AcquireLockStage implements StageDefinitionBuilder {

  public static final String PIPELINE_TYPE = "acquireLock";

  @Nonnull
  @Override
  public String getType() {
    return PIPELINE_TYPE;
  }

  @Override
  public void taskGraph(@Nonnull Stage stage, @Nonnull TaskNode.Builder builder) {
    builder.withTask("acquireLock", AcquireLockTask.class);
  }

  @Override
  public void prepareStageForRestart(@Nonnull Stage stage) {
    stage.getContext().remove("completeOtherBranchesThenFail");
  }
}
