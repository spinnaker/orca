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

package com.netflix.spinnaker.orca.pipeline.util;

import static java.util.stream.Collectors.toList;

import com.netflix.spinnaker.orca.StageResolver;
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Provides an enhanced version of {@link StageExecution#ancestors()} that returns tuples of the
 * ancestor stages and their {@link StageDefinitionBuilder}s.
 */
@Component
public class StageNavigator {
  private final StageResolver stageResolver;

  @Autowired
  public StageNavigator(StageResolver stageResolver) {
    this.stageResolver = stageResolver;
  }

  /**
   * As per `Stage.ancestors` except this method returns tuples of the stages and their
   * `StageDefinitionBuilder`.
   */
  public List<Result> ancestors(StageExecution startingStage) {
    return startingStage.ancestors().stream()
        .map(
            it ->
                new Result(
                    it,
                    stageResolver.getStageDefinitionBuilder(
                        it.getType(), (String) it.getContext().get("alias"))))
        .collect(toList());
  }

  public static class Result {
    private final StageExecution stage;
    private final StageDefinitionBuilder stageBuilder;

    Result(StageExecution stage, StageDefinitionBuilder stageBuilder) {
      this.stage = stage;
      this.stageBuilder = stageBuilder;
    }

    public StageExecution getStage() {
      return stage;
    }

    public StageDefinitionBuilder getStageBuilder() {
      return stageBuilder;
    }
  }
}
