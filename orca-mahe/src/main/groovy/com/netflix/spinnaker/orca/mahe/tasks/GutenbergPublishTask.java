/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.mahe.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.mahe.MaheService;
import com.netflix.spinnaker.orca.mahe.pipeline.GutenbergPublishStage;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.client.Response;

import javax.annotation.Nonnull;
import java.util.Map;

import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED;

@Component
public class GutenbergPublishTask implements Task {
  @Autowired
  private MaheService maheService;

  @Autowired
  private ObjectMapper mapper;

  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    Map context = stage.getContext();
    try {
      Map outputs = stage.getOutputs();
      if (outputs.containsKey(GutenbergPublishStage.S3_BUCKET_NAME) && outputs.containsKey(GutenbergPublishStage.S3_OBJECT_NAME)) {
        context.put(GutenbergPublishStage.S3_BUCKET_NAME, outputs.get(GutenbergPublishStage.S3_BUCKET_NAME));
        context.put(GutenbergPublishStage.S3_OBJECT_NAME, outputs.get(GutenbergPublishStage.S3_OBJECT_NAME));
      }

      String env = (String) context.getOrDefault("env", "test");
      Map dataPointer = (Map) context.get(GutenbergPublishStage.DATA_POINTER);
      if (dataPointer == null || !dataPointer.containsKey(GutenbergPublishStage.TOPIC)) {
        throw new GutenbergPublishException(
          String.format("Publish failed. topic not specified: %s", context)
        );
      }

      String topic = (String) dataPointer.get(GutenbergPublishStage.TOPIC);
      Response response = maheService.gutenbergPublish(env, context);
      Map result = mapper.readValue(response.getBody().in(), Map.class);
      if (result == null || response.getStatus() != 200 ||  !result.containsKey(GutenbergPublishStage.GUTENBERG_RESULT)) {
        throw new GutenbergPublishException(
          String.format("Unable to publish to topic %s, %s for request %s", topic, result, context)
        );
      }

      return new TaskResult(SUCCEEDED, result, result);
    } catch (Exception e) {
      throw new GutenbergPublishException(
        String.format("Failed to publish to topic %s %s", context), e
      );
    }
  }

  class GutenbergPublishException extends RuntimeException {
    public GutenbergPublishException(String message, Throwable cause) {
      super(message, cause);
    }

    public GutenbergPublishException(String message) {
      super(message);
    }
  }
}
