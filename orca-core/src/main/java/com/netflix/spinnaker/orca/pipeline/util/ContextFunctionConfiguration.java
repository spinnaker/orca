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

package com.netflix.spinnaker.orca.pipeline.util;

import static com.netflix.spinnaker.orca.pipeline.expressions.PipelineExpressionEvaluator.ExpressionEvaluationVersion.V2;

import com.netflix.spinnaker.orca.config.UserConfiguredUrlRestrictions;
import com.netflix.spinnaker.orca.pipeline.expressions.ExpressionFunctionProvider;
import java.util.List;

public class ContextFunctionConfiguration {
  private final UserConfiguredUrlRestrictions urlRestrictions;
  private final List<ExpressionFunctionProvider> expressionFunctionProviders;
  private final String spelEvaluator;

  public ContextFunctionConfiguration(
      UserConfiguredUrlRestrictions urlRestrictions,
      List<ExpressionFunctionProvider> expressionFunctionProviders,
      String spelEvaluator) {
    this.urlRestrictions = urlRestrictions;
    this.expressionFunctionProviders = expressionFunctionProviders;
    this.spelEvaluator = spelEvaluator;
  }

  public ContextFunctionConfiguration(
      UserConfiguredUrlRestrictions urlRestrictions,
      List<ExpressionFunctionProvider> expressionFunctionProviders) {
    this(urlRestrictions, expressionFunctionProviders, V2);
  }

  public UserConfiguredUrlRestrictions getUrlRestrictions() {
    return urlRestrictions;
  }

  public List<ExpressionFunctionProvider> getExpressionFunctionProviders() {
    return expressionFunctionProviders;
  }

  public String getSpelEvaluator() {
    return spelEvaluator;
  }
}
