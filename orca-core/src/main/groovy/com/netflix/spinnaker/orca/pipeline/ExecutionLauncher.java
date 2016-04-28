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

import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class ExecutionLauncher<T extends Execution> {

  protected final ObjectMapper objectMapper;
  protected final InstanceInfo currentInstance;
  private final ExecutionRunner runner;

  protected ExecutionLauncher(ObjectMapper objectMapper,
                              InstanceInfo currentInstance,
                              ExecutionRunner runner) {
    this.objectMapper = objectMapper;
    this.currentInstance = currentInstance;
    this.runner = runner;
  }

  public T start(String configJson) throws Exception {
    final T execution = parse(configJson);
    if (shouldQueue(execution)) {
      log.info("Queueing {}", execution.getId());
    } else {
      runner.start(execution);
    }
    return execution;
  }

  protected abstract T parse(String configJson) throws IOException;

  /**
   * Hook for subclasses to decide if this execution should be queued or start immediately.
   *
   * @return true if the stage should be queued.
   */
  protected boolean shouldQueue(T execution) {
    return false;
  }
}

