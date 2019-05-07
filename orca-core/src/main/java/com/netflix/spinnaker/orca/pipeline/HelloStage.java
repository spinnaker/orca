/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.tasks.HelloTask;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;

@Component
public class HelloStage implements StageDefinitionBuilder {

  public static String STAGE_TYPE = "hello";

  @Override
  public void taskGraph(Stage stage, TaskNode.Builder builder) {
    // Task name can be changed per stage,
    builder.withTask("hello", HelloTask.class);
  }

  public static final class HelloStageContext {
    private final String yourName;

    @JsonCreator
    public HelloStageContext(
      @JsonProperty("yourName") @Nullable String yourName
    ) {
      this.yourName = yourName;
    }

    public @Nullable String getYourName() {
      return yourName;
    }
  }
}
