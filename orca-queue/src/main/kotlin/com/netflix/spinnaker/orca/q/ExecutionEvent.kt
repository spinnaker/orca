/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.q

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Execution
import org.springframework.context.ApplicationEvent

/**
 * Events that external clients can listen for to receive updates on progress of
 * an execution. These are not used internally by the queueing system to
 * organize work but are published for external monitoring.
 */
sealed class ExecutionEvent(source: Any) : ApplicationEvent(source) {
  /**
   * An execution completed (either completed successfully or stopped due to
   * failure/cancellation/whatever).
   */
  class ExecutionCompleteEvent(
    source: Any,
    val executionType: Class<out Execution<*>>,
    val executionId: String,
    val status: ExecutionStatus
  ) : ExecutionEvent(source)
}
