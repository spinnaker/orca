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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import org.springframework.expression.AccessException
import org.springframework.expression.EvaluationContext
import org.springframework.expression.Expression
import org.springframework.expression.ExpressionParser
import org.springframework.expression.ParserContext
import org.springframework.expression.TypedValue
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.ReflectivePropertyAccessor
import org.springframework.expression.spel.support.StandardEvaluationContext

/**
 * Common methods for dealing with passing context parameters used by both Script and Jenkins stages
 */
class ContextParameterProcessor {

  // uses $ instead of  #
  private static ParserContext parserContext = [
    getExpressionPrefix: {
      '${'
    },
    getExpressionSuffix: {
      '}'
    },
    isTemplate         : {
      true
    }
  ] as ParserContext

  private static MapPropertyAccessor = new MapPropertyAccessor()

  private static ExpressionParser parser = new SpelExpressionParser()

  static Map process(Map parameters, Map context) {
    if (!parameters) {
      return null
    }

    transform(parameters, precomputeValues(context))
  }

  static boolean containsExpression(String value) {
    return value?.contains(parserContext.getExpressionPrefix())
  }

  static Map precomputeValues(Map context) {

    if(context.trigger?.parameters) {
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
        it.type == 'deploy' && it.status == ExecutionStatus.SUCCEEDED
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

  static def transform(parameters, context) {
    if (parameters instanceof Map) {
      return parameters.collectEntries { k, v ->
        [transform(k, context), transform(v, context)]
      }
    } else if (parameters instanceof List) {
      return parameters.collect {
        transform(it, context)
      }
    } else if (parameters instanceof String || parameters instanceof GString) {
      Object convertedValue = parameters
      EvaluationContext evaluationContext = new StandardEvaluationContext(context)
      evaluationContext.addPropertyAccessor(MapPropertyAccessor)
      evaluationContext.registerFunction('alphanumerical', ContextStringUtilities.getDeclaredMethod("alphanumerical", String))
      evaluationContext.registerFunction('toJson', ContextStringUtilities.getDeclaredMethod("toJson", Object))
      evaluationContext.registerFunction('toInt', ContextStringUtilities.getDeclaredMethod("toInt", String))
      evaluationContext.registerFunction('toFloat', ContextStringUtilities.getDeclaredMethod("toFloat", String))
      try {
        Expression exp = parser.parseExpression(parameters, parserContext)
        convertedValue = exp.getValue(evaluationContext)
      } catch (e) {
        convertedValue = parameters
      }

      if(convertedValue == null){
        convertedValue = parameters
      }

      return convertedValue
    } else {
      return parameters
    }
  }

}

abstract class ContextStringUtilities {
  static String alphanumerical(String str) {
    str.replaceAll('[^A-Za-z0-9]', '')
  }
  static String toJson(Object o) {
    new ObjectMapper().writeValueAsString(o)
  }
  static Integer toInt(String str) {
    Integer.valueOf(str)
  }
  static Float toFloat(String str) {
    Float.valueOf(str)
  }
}

class MapPropertyAccessor extends ReflectivePropertyAccessor {

  public MapPropertyAccessor() {
    super()
  }

  @Override
  Class<?>[] getSpecificTargetClasses() {
    [Map]
  }

  @Override
  boolean canRead(final EvaluationContext context, final Object target, final String name)
    throws AccessException {
    true
  }

  @Override
  public TypedValue read(final EvaluationContext context, final Object target, final String name)
    throws AccessException {
    if (!(target instanceof Map)) {
      throw new AccessException("Cannot read target of class " + target.getClass().getName())
    }
    new TypedValue(((Map<String, ?>) target).get(name))
  }

}
