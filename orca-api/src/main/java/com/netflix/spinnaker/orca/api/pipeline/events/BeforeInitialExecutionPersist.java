/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.orca.api.pipeline.events;

import com.netflix.spinnaker.orca.api.annotations.Sync;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import javax.annotation.Nonnull;

/**
 * An event emitted immediately before the initial persist of a {@link PipelineExecution}.
 *
 * <p>Under most circumstances, event listeners for this event will also want to use the {@link
 * Sync} annotation so that any changes made to the pipeline execution are actually persisted.
 */
public interface BeforeInitialExecutionPersist extends OrcaApplicationEvent {

  /** @return The {@link PipelineExecution} that is being persisted. */
  @Nonnull
  PipelineExecution getExecution();
}
