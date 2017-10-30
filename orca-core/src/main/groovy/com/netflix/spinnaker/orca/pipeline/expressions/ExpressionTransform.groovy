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

package com.netflix.spinnaker.orca.pipeline.expressions

import groovy.util.logging.Slf4j
import org.springframework.expression.EvaluationContext
import org.springframework.expression.Expression
import org.springframework.expression.ExpressionParser
import org.springframework.expression.ParserContext
import org.springframework.expression.common.CompositeStringExpression

import java.util.stream.Collectors
import java.util.stream.Stream

@Slf4j
class ExpressionTransform {
  private final ParserContext parserContext
  private final ExpressionParser parser
  private final List<RuntimeExpressionUpdater> runtimeExpressionUpdaters

  ExpressionTransform(ParserContext parserContext,
                      ExpressionParser parser,
                      final List<RuntimeExpressionUpdater> runtimeExpressionUpdaters) {
    this.parserContext = parserContext
    this.parser = parser
    this.runtimeExpressionUpdaters = runtimeExpressionUpdaters
  }

  /**
   * Traverses & attempts to evaluate expressions
   * Failures can either be INFO (for a simple unresolved expression) or ERROR when an exception is thrown
   * @param source
   * @param evaluationContext
   * @param summary
   * @return the transformed source object
   */
  def <T> T transform(T source,
                      EvaluationContext evaluationContext,
                      ExpressionEvaluationSummary summary,
                      Map<String, ?> additionalContext = [:]) {
    if (source == null) {
      return null
    }

    if (source instanceof Map) {
      Map<String, ?> copy = Collections.unmodifiableMap(source)
      source.collectEntries { k, v ->
        [ transform(k, evaluationContext, summary, copy), transform(v, evaluationContext, summary, copy) ]
      } as T
    } else if (source instanceof List) {
      source.collect {
        transform(it, evaluationContext, summary)
      } as T
    } else if ((source instanceof String || source instanceof GString) && source.toString().contains(parserContext.getExpressionPrefix())) {
      String literalExpression = source.toString()
      for (RuntimeExpressionUpdater runtimeExpressionUpdater : runtimeExpressionUpdaters) {
        literalExpression = runtimeExpressionUpdater.process(literalExpression)
      }

      T result
      Expression exp
      String escapedExpressionString = null
      Throwable exception = null
      try {
        exp = parser.parseExpression(literalExpression, parserContext)
        escapedExpressionString = escapeExpression(exp)
        result = exp.getValue(evaluationContext) as T
      } catch (Exception e) {
        log.info("Failed to evaluate $source, returning raw value {}", e.getMessage())
        exception = e
      } finally {
        escapedExpressionString = escapedExpressionString?: escapeSimpleExpression(source as String)
        if (exception) {
          def fields = getKeys(literalExpression, additionalContext)?: literalExpression
          String errorDescription = String.format("Failed to evaluate %s ", fields)
          Throwable originalException = unwrapOriginalException(exception)
          errorDescription += exception.getMessage() in originalException?.getMessage()?
            exception.getMessage() :originalException?.getMessage() + " - " + exception.getMessage()

          summary.add(
            escapedExpressionString,
            ExpressionEvaluationSummary.Result.Level.ERROR,
            errorDescription.replaceAll("\\\$", ""),
            originalException.getClass()
          )

          result = source
        } else if (result == null) {
          def fields = getKeys(literalExpression, additionalContext)?: literalExpression
          String errorDescription = String.format("Failed to evaluate %s ", fields)
          summary.add(
            escapedExpressionString,
            ExpressionEvaluationSummary.Result.Level.INFO,
            "$errorDescription: $escapedExpressionString not found",
            null
          )

          result = source
        }

        summary.appendAttempted(escapedExpressionString)
        summary.incrementTotalEvaluated()
      }

      return result
    } else {
      return source
    }
  }

  /**
   * finds parent keys by value in a nested map
   */
  private static Set<String> getKeys(Object value, final Map<String, ?> map) {
    if (!map) {
      return [] as Set
    }

    return map.entrySet()
      .findAll { value in flatten(it.value).collect(Collectors.toSet()).flatten() }*.key as Set
  }

  private static Stream<?> flatten(Object o) {
    if (o instanceof Map) {
      Map<?, ?> map = o as Map
      return (map.keySet() + map.values())
        .stream()
        .flatMap {
          flatten(it)
        }
    }

    return Stream.of(o)
  }

  /**
   * Finds the original exception in the exception hierarchy
   */
  private static Throwable unwrapOriginalException(Throwable e) {
    if (e == null || e.getCause() == null) return e
    return unwrapOriginalException(e.getCause())
  }

  /**
   * Helper to escape an expression: stripping ${ }
   */
  static String escapeExpression(Expression expression) {
    if (expression instanceof CompositeStringExpression) {
      StringBuilder sb = new StringBuilder()
      for (Expression e : expression.expressions) {
        sb.append(e.getExpressionString())
      }

      return sb.toString()
    }

    return expression.getExpressionString()
  }

  /**
   * Helper to escape  a simple expression string
   * Used to extract a simple expression when parsing fails
   */
  static String escapeSimpleExpression(String expression) {
    String escaped = null
    def matcher = expression =~ /\$\{(.*)}/
    if (matcher.matches()) {
      escaped = matcher.group(1).trim()
    }

    return escaped?: expression.replaceAll("\\\$", "")
  }
}


