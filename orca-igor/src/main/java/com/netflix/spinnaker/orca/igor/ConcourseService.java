/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.orca.igor;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.igor.model.ConcourseStageExecution;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.springframework.stereotype.Component;

@Component
public class ConcourseService {
  private final Cache<String, ConcourseStageExecution> executionsByStageId =
      CacheBuilder.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).build();

  public void pushExecution(ConcourseStageExecution execution) {
    executionsByStageId.put(execution.getStageId(), execution);
  }

  @Nullable
  public ConcourseStageExecution popExecution(StageExecution stage) {
    return executionsByStageId.asMap().remove(stage.getId());
  }
}
