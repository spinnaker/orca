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

package com.netflix.spinnaker.orca.keel.task;

import com.netflix.spinnaker.orca.*;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.netflix.spinnaker.orca.RetryableTask;
import retrofit.client.Response;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class DeleteIntentTask implements RetryableTask {
  @Autowired(required = false)
  private KeelService keelService;

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    if (keelService == null) {
      throw new UnsupportedOperationException("Keel is not enabled, no way to process intents. Fix this by setting keel.enabled: true");
    }

    if (!stage.getContext().containsKey("intentId")){
      throw new IllegalArgumentException("Missing required task parameter (intentId)");
    }
    String intentId = stage.getContext().get("intentId").toString();

    String status = stage.getContext().containsKey("status")? stage.getContext().get("status").toString(): null;

    Response response = keelService.deleteIntents(intentId, null);

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("intent.id", intentId);

    return new TaskResult(
      (response.getStatus() == HttpStatus.NO_CONTENT.value()) ? ExecutionStatus.SUCCEEDED : ExecutionStatus.TERMINAL,
      outputs
    );
  }

  @Override
  public long getBackoffPeriod() {
    return 15000;
  }

  @Override
  public long getTimeout() {
    return TimeUnit.MINUTES.toMillis(1);
  }
}
