/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.dryrun.stub

import com.netflix.spinnaker.orca.pipeline.model.Stage

/**
 * Certain stages may benefit from outputting dummy data in order that they
 * generate some kind of representative output. Implement this interface in that
 * case.
 */
interface OutputStub {

  /**
   * Return `true` if a stage type is supported by this stub.
   */
  fun supports(stageType: String): Boolean = false

  /**
   * Generate stub output. This can be based on things in the stage context if
   * necessary.
   */
  fun outputs(stage: Stage): Map<String, Any> = emptyMap()

}
