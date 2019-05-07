/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.tasks;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.pipeline.HelloStage;
import com.netflix.spinnaker.orca.pipeline.WaitStage;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING;
import static java.util.Collections.singletonMap;

@Component
public class HelloTask implements Task {

  private final String yourName;

  @Autowired
  public HelloTask(String yourName) {
    this.yourName = yourName;
  }

  @Override
  public @Nonnull
  TaskResult execute(@Nonnull Stage stage) {

    Map outputs = new HashMap();
    outputs.put("yourName", yourName);

    HelloStage.HelloStageContext context = stage.mapTo(HelloStage.HelloStageContext.class);

    if (context.getYourName() == null) {
      return TaskResult.ofStatus(ExecutionStatus.TERMINAL);
    }

    return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build();
  }

}
