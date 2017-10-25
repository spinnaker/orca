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

package com.netflix.spinnaker.orca.pipeline.util

import com.netflix.spinnaker.orca.pipeline.expressions.ExpressionEvaluationSummary
import com.netflix.spinnaker.orca.pipeline.expressions.ExpressionEvaluator
import com.netflix.spinnaker.orca.pipeline.expressions.PipelineExpressionEvaluator
import groovy.util.logging.Slf4j
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.config.UserConfiguredUrlRestrictions
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage

import static com.netflix.spinnaker.orca.pipeline.expressions.PipelineExpressionEvaluator.ExpressionEvaluationVersion.V2

/**
 * Common methods for dealing with passing context parameters used by both Script and Jenkins stages
 */

@Slf4j
class ContextParameterProcessor {
  private ExpressionEvaluator expressionEvaluator

  ContextParameterProcessor() {
    this(new ContextFunctionConfiguration(new UserConfiguredUrlRestrictions.Builder().build()))
  }

  ContextParameterProcessor(ContextFunctionConfiguration contextFunctionConfiguration) {
    expressionEvaluator = new PipelineExpressionEvaluator(contextFunctionConfiguration)
  }

  Map<String, Object> process(Map<String, Object> source, Map<String, Object> context, boolean allowUnknownKeys) {
    if (!source) {
      return [:]
    }

    def summary = new ExpressionEvaluationSummary()
    Map<String, Object> result = expressionEvaluator.evaluate(
      source,
      precomputeValues(context),
      summary,
      allowUnknownKeys,
    )

    if (summary.totalEvaluated > 0 && context.execution) {
      log.info("Evaluated {} in execution {}", summary, context.execution.id)
    }

    if (summary.failureCount > 0) {
      result.expressionEvaluationSummary = summary.expressionResult
    }

    return result
  }

  Map<String, Object> buildExecutionContext(Stage stage, boolean includeStageContext) {
    def augmentedContext = [:] + (includeStageContext ? stage.context : [:])
    if (stage.execution instanceof Pipeline) {
      augmentedContext.put('trigger', ((Pipeline) stage.execution).trigger)
      augmentedContext.put('execution', stage.execution)
    }

    return augmentedContext
  }

  static boolean containsExpression(String value) {
    return value?.contains('${')
  }

  Map<String, Object> precomputeValues(Map<String, Object> context) {

    if (context.trigger?.parameters) {
      context.parameters = context.trigger.parameters
    }

    context.scmInfo = context.buildInfo?.scm ?: context.trigger?.buildInfo?.scm ?: null
    if (context.scmInfo && context.scmInfo.size() >= 2) {
      def scmInfo = context.scmInfo.find { it.branch != 'master' && it.branch != 'develop' }
      context.scmInfo = scmInfo ?: context.scmInfo?.first()
    } else {
      context.scmInfo = context.scmInfo?.first()
    }

    if (context.execution) {
      def deployedServerGroups = []
      context.execution.stages.findAll {
        it.type in ['deploy', 'createServerGroup', 'cloneServerGroup', 'rollingPush'] && it.status == ExecutionStatus.SUCCEEDED
      }.each { deployStage ->
        if (deployStage.context.'deploy.server.groups') {
          Map deployDetails = [
            account    : deployStage.context.account,
            capacity   : deployStage.context.capacity,
            parentStage: deployStage.parentStageId,
            region     : deployStage.context.region ?: deployStage.context.availabilityZones.keySet().first(),
          ]
          deployDetails.putAll(context.execution?.context?.deploymentDetails?.find { it.region == deployDetails.region } ?: [:])
          deployDetails.serverGroup = deployStage.context.'deploy.server.groups'."${deployDetails.region}".first()
          deployedServerGroups << deployDetails
        }
      }
      if (!deployedServerGroups.empty) {
        context.deployedServerGroups = deployedServerGroups
      }
    }
    context
  }
}

class ContextFunctionConfiguration {
  final UserConfiguredUrlRestrictions urlRestrictions
  final String spelEvaluator

  ContextFunctionConfiguration(UserConfiguredUrlRestrictions urlRestrictions, String spelEvaluator = V2) {
    this.urlRestrictions = urlRestrictions
    this.spelEvaluator = spelEvaluator
  }
}
