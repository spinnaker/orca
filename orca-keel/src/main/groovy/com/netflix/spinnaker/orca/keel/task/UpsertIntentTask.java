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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.KeelService;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.keel.model.UpsertIntent;
import com.netflix.spinnaker.orca.keel.model.UpsertIntentDryRunResponse;
import com.netflix.spinnaker.orca.keel.model.UpsertIntentRequest;
import com.netflix.spinnaker.orca.keel.model.UpsertIntentResponse;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class UpsertIntentTask implements RetryableTask {
  @Autowired(required = false)
  private KeelService keelService;

  @Autowired
  private ObjectMapper keelObjectMapper;

  private static final Logger logger = LoggerFactory.getLogger(UpsertIntentTask.class);

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    if (keelService == null) {
      throw new UnsupportedOperationException("Keel is not enabled, no way to process intents. Fix this by setting keel.enabled: true");
    }

    if (!stage.getContext().containsKey("intents")){
      throw new IllegalArgumentException("Missing required task parameter (intents)");
    }

    if (!stage.getContext().containsKey("dryRun")){
      throw new IllegalArgumentException("Missing required task parameter (dryRun)");
    }

    UpsertIntentRequest upsertIntentRequest = new UpsertIntentRequest();
    upsertIntentRequest.intents = keelObjectMapper.convertValue(stage.getContext().get("intents"), new TypeReference<List<UpsertIntent>>(){});
    upsertIntentRequest.dryRun = Boolean.valueOf(stage.getContext().get("dryRun").toString());

    Response response = keelService.upsertIntents(upsertIntentRequest);

    Map<String, Object> outputs = new HashMap<>();

    if (upsertIntentRequest.dryRun) {
      try {
        List<UpsertIntentDryRunResponse> dryRunResponse = keelObjectMapper.readValue(response.getBody().in(), new TypeReference<List<UpsertIntentDryRunResponse>>(){});
        outputs.put("upsertIntentResponse", dryRunResponse);
      } catch (Exception e){
        logger.warn("Error processing upsert intent dry run response");
      }
    } else {
      try {
        List<UpsertIntentResponse> upsertResponse = keelObjectMapper.readValue(response.getBody().in(), new TypeReference<List<UpsertIntentResponse>>(){});
        outputs.put("upsertIntentResponse", upsertResponse);
      } catch (Exception e){
        logger.error("Error processing upsert intent response");
      }
    }

    return new TaskResult(
      (response.getStatus() == HttpStatus.ACCEPTED.value()) ? ExecutionStatus.SUCCEEDED : ExecutionStatus.TERMINAL,
      outputs
    );
  }

  @Override
  public long getBackoffPeriod() { return 15000; }

  @Override
  public long getTimeout() {
    return TimeUnit.MINUTES.toMillis(1);
  }
}
