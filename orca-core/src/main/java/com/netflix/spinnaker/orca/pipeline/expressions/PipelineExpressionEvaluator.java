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

package com.netflix.spinnaker.orca.pipeline.expressions;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.pipeline.util.ContextFunctionConfiguration;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

public class PipelineExpressionEvaluator extends ExpressionsSupport implements ExpressionEvaluator {
  public static final String SUMMARY = "expressionEvaluationSummary";
  public static final String ERROR = "Failed Expression Evaluation";
  public static final String SPEL_CONFIGURATION = "spelEvaluation";

  private final ExpressionParser parser = new SpelExpressionParser();
  private final ObjectMapper objectMapper;

  public interface ExpressionEvaluationVersion {
    String V2 = "v2";
    String V1 = "v1";
  }

  public PipelineExpressionEvaluator(final ContextFunctionConfiguration contextFunctionConfiguration, ObjectMapper objectMapper) {
    super(contextFunctionConfiguration);
    this.objectMapper = objectMapper;
  }

  @Override
  public Map<String, Object> evaluate(Map<String, Object> source, Object rootObject, ExpressionEvaluationSummary summary, boolean allowUnknownKeys) {
    StandardEvaluationContext evaluationContext = newEvaluationContext(rootObject, allowUnknownKeys);
    SpelEvaluationConfiguration spelEvaluationConfiguration = source.containsKey(SPEL_CONFIGURATION)
      ? objectMapper.convertValue(source.get(SPEL_CONFIGURATION), SpelEvaluationConfiguration.class) : null;


    return new ExpressionTransform(parserContext, parser, buildEvaluationStrategies(spelEvaluationConfiguration))
      .transform(source, evaluationContext, summary);
  }

  private List<EvaluationStrategy> buildEvaluationStrategies(SpelEvaluationConfiguration spelEvaluationConfiguration) {
    if (spelEvaluationConfiguration != null) {
      return Collections.singletonList(new ExpressionExclusionStrategy(spelEvaluationConfiguration.getExcludes()));
    }

    return Collections.emptyList();
  }
}



