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

package com.netflix.spinnaker.orca.pipeline.expressions;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;

import static com.netflix.spinnaker.orca.pipeline.expressions.PipelineExpressionEvaluator.SPEL_CONFIGURATION;

public interface EvaluationStrategy {
  boolean shouldEvaluate(@Nonnull Object obj);
}

class ExpressionExclusionStrategy implements EvaluationStrategy {
  private final List<Excludes> excludes;

  public ExpressionExclusionStrategy(List<Excludes> excludes) {
    this.excludes = excludes;
  }

  @Override
  public boolean shouldEvaluate(@Nonnull Object obj) {
    if (obj instanceof String && ((String) obj).equalsIgnoreCase(SPEL_CONFIGURATION)) {
      return true;
    }

    if (obj instanceof String) {
      return excludes.stream().noneMatch(i -> i.key.equals(obj));
    }

    if (obj instanceof Collection) {
      return excludes.stream().map(e -> e.key).noneMatch(((Collection) obj)::contains);
    }

    return true;
  }
}
