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

package com.netflix.spinnaker.orca.q.handler

import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.model.StageContext
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor

/**
 * Implemented by handlers that support expression evaluation.
 */
interface ExpressionAware {

  val contextParameterProcessor: ContextParameterProcessor

  fun Stage<*>.withMergedContext(): Stage<*> {
    val processed = processEntries(this)
    val execution = getExecution()
    this.setContext(object : MutableMap<String, Any?> by processed {
      override fun get(key: String): Any? {
        if (execution is Pipeline) {
          if (key == "trigger") {
            return execution.trigger
          }

          if (key == "execution") {
            return execution
          }
        }

        val result = processed[key]

        if (result is String && ContextParameterProcessor.containsExpression(result)) {
          val augmentedContext = processed.augmentContext(this@withMergedContext)
          return contextParameterProcessor.process(mapOf(key to result), augmentedContext, true)[key]
        }

        return result
      }
    })
    return this
  }

  private fun processEntries(stage: Stage<*>) =
    contextParameterProcessor.process(
      stage.getContext(),
      stage.getContext().augmentContext(stage),
      true
    )

  // TODO: this should really be an extension on StageContext not Map
  private fun Map<String, Any?>.augmentContext(stage: Stage<*>) =
    StageContext(stage, this).apply {
      val execution = stage.getExecution()
      if (execution is Pipeline) {
        put("trigger", execution.trigger)
        put("execution", execution)
      }
    }
}
