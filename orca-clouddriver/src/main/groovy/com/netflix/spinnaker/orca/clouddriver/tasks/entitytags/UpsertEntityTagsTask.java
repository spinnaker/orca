/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.entitytags;

import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.KatoService;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UpsertEntityTagsTask extends AbstractCloudProviderAwareTask implements RetryableTask {
  private final KatoService kato;

  @Autowired
  public UpsertEntityTagsTask(KatoService kato) {
    this.kato = kato;
  }

  @Override
  public TaskResult execute(StageExecution stage) {
    TaskId taskId =
        kato.requestOperations(
                Collections.singletonList(
                    new HashMap<String, Map>() {
                      {
                        put("upsertEntityTags", stage.getContext());
                      }
                    }))
            .toBlocking()
            .first();

    return TaskResult.builder(ExecutionStatus.SUCCEEDED)
        .context(
            new HashMap<String, Object>() {
              {
                put("notification.type", "upsertentitytags");
                put("kato.last.task.id", taskId);
              }
            })
        .build();
  }

  @Override
  public long getBackoffPeriod() {
    return TimeUnit.SECONDS.toMillis(5);
  }

  @Override
  public long getTimeout() {
    return TimeUnit.MINUTES.toMillis(1);
  }
}
