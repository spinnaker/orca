/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline

import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.tasks.PreconditionTask
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class CheckPreconditionsStage implements BranchingStageDefinitionBuilder {

  static final String PIPELINE_CONFIG_TYPE = "checkPreconditions"

  private final List<? extends PreconditionTask> preconditionTasks

  @Autowired
  CheckPreconditionsStage(List<? extends PreconditionTask> preconditionTasks) {
    this.preconditionTasks = preconditionTasks
  }

  @Override
  def <T extends Execution<T>> void taskGraph(Stage<T> stage, TaskNode.Builder builder) {
    String preconditionType = stage.context.preconditionType
    if (!preconditionType) {
      throw new IllegalStateException("no preconditionType specified for stage $stage.id")
    }
    Task preconditionTask = preconditionTasks.find {
      it.preconditionType == preconditionType
    }
    if (!preconditionTask) {
      throw new IllegalStateException("no Precondition implementation for type $preconditionType")
    }
    builder.withTask("checkPrecondition", preconditionTask.getClass() as Class<? extends Task>)
  }

  @Override
  def <T extends Execution<T>> Collection<Map<String, Object>> parallelContexts(Stage<T> stage) {
    stage.resolveStrategyParams()
    def baseContext = new HashMap(stage.context)
    List<Map> preconditions = baseContext.remove('preconditions') as List<Map>
    return preconditions.collect { preconditionConfig ->
      def context = baseContext + preconditionConfig + [
        type            : PIPELINE_CONFIG_TYPE,
        preconditionType: preconditionConfig.type
      ]

      context['context'] = context['context'] ?: [:]

      ['cluster', 'regions', 'credentials', 'zones'].each {
        context['context'][it] = context['context'][it] ?: baseContext[it]
      }

      context.name = context.name ?: "Check precondition (${context.preconditionType})".toString()
      return context
    }
  }
}
