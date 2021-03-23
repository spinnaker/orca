/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca;

import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import java.util.Optional;

/**
 * This interface allows an implementing StageDefinitionBuilder to override the default pipeline
 * authentication context.
 *
 * <p>TODO(rz): Move to orca-api. Need to rearrange kork, however, since it would require a
 * dependency on kork-security, which includes a bunch of extra dependencies. Perhaps a new kork-api
 * module for just the User class?
 */
public interface AuthenticatedStage {
  Optional<PipelineExecution.AuthenticationDetails> authenticatedUser(StageExecution stage);
}
