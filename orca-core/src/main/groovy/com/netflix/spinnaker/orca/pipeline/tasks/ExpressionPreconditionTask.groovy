/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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


package com.netflix.spinnaker.orca.pipeline.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class ExpressionPreconditionTask implements PreconditionTask {
  final String preconditionType = 'expression'

  final ContextParameterProcessor contextParameterProcessor

  @Autowired
  ExpressionPreconditionTask(ContextParameterProcessor contextParameterProcessor) {
    this.contextParameterProcessor = contextParameterProcessor
  }

  @Override
  TaskResult execute(Stage stage) {
    def stageData = stage.mapTo("/context", StageData)
    String expression = contextParameterProcessor.process([
        "expression": '${' + stageData.expression + '}'
    ], contextParameterProcessor.buildExecutionContext(stage, true), true).expression

    def matcher = expression =~ /\$\{(.*)\}/
    if (matcher.matches()) {
      expression = matcher.group(1)
    }

    def status = Boolean.valueOf(expression) ? ExecutionStatus.SUCCEEDED : ExecutionStatus.TERMINAL
    return new TaskResult(status, [
      context: new HashMap(stage.context.context as Map) + [
          expressionResult: expression
      ]
    ])
  }

  static class StageData {
    String expression = "false"
  }
}
