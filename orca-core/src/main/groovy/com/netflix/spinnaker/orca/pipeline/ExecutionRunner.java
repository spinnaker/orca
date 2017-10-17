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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import static java.lang.String.format;

public interface ExecutionRunner {
  <T extends Execution<T>> void start(@Nonnull T execution) throws Exception;

  default <T extends Execution<T>> void restart(
    @Nonnull T execution, @Nonnull String stageId) throws Exception {
    throw new UnsupportedOperationException();
  }

  default <T extends Execution<T>> void reschedule(
    @Nonnull T execution) throws Exception {
    throw new UnsupportedOperationException();
  }

  default <T extends Execution<T>> void unpause(
    @Nonnull T execution) throws Exception {
    throw new UnsupportedOperationException();
  }

  default <T extends Execution<T>> void cancel(
    @Nonnull T execution,
    @Nonnull String user, @Nullable String reason) throws Exception {
    throw new UnsupportedOperationException();
  }

  class NoSuchStageDefinitionBuilder extends RuntimeException {
    public NoSuchStageDefinitionBuilder(String type) {
      super(format("No StageDefinitionBuilder implementation for %s found", type));
    }
  }
}
