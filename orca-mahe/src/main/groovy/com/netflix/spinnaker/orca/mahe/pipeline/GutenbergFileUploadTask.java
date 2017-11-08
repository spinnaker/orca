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

package com.netflix.spinnaker.orca.mahe.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.mahe.MaheService;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.client.Response;
import retrofit.mime.TypedFile;

import javax.annotation.Nonnull;
import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED;

@Component
public class GutenbergFileUploadTask implements Task {
  @Autowired
  private MaheService maheService;

  @Autowired
  private ObjectMapper mapper;

  @Autowired
  private List<FileLoader> fileLoaders;

  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    Map context = stage.getContext();
    String source = (String) context.get("source");
    try {
      URI uri = new URI(source);
      File file = fileLoaders
        .stream()
        .filter(f -> f.supports(uri, source))
        .findFirst()
        .orElseThrow(() -> new GutenbergFileUploadException(
          String.format("Failed to load source file to upload %s. %s", source, context))
        ).load(uri);

      String env = (String) context.getOrDefault("env", "test");
      Map dataPointer = (Map) context.get(GutenbergPublishStage.DATA_POINTER);
      if (dataPointer == null || !dataPointer.containsKey(GutenbergPublishStage.TOPIC)) {
        throw new GutenbergFileUploadException(
          String.format("Failed to upload file from source %s. Publish topic not specified: %s", source, context)
        );
      }

      String topic = (String) dataPointer.get(GutenbergPublishStage.TOPIC);
      Response response = maheService.gutenbergFileUpload(env, topic, new TypedFile("multipart/form-data", file));
      Map result = mapper.readValue(response.getBody().in(), Map.class);
      if (result == null || response.getStatus() != 200 || !result.containsKey(GutenbergPublishStage.GUTENBERG_RESULT)) {
        throw new GutenbergFileUploadException(
          String.format("Failed to upload file from source %s %s", source, result)
        );
      }

      Map gutenbergResult = (Map) result.get(GutenbergPublishStage.GUTENBERG_RESULT);
      Map<String, Object> output = new HashMap<>();
      output.put(GutenbergPublishStage.TOPIC, topic);
      output.put(GutenbergPublishStage.S3_BUCKET_NAME, gutenbergResult.get(GutenbergPublishStage.S3_BUCKET_NAME));
      output.put(GutenbergPublishStage.S3_OBJECT_NAME, gutenbergResult.get(GutenbergPublishStage.S3_OBJECT_NAME));
      return new TaskResult(SUCCEEDED, output, output);
    } catch (Exception e) {
      throw new GutenbergFileUploadException(
        String.format("Failed to upload file from source %s %s", source, context), e
      );
    }
  }

  class GutenbergFileUploadException extends RuntimeException {
    public GutenbergFileUploadException(String message, Throwable cause) {
      super(message, cause);
    }

    public GutenbergFileUploadException(String message) {
      super(message);
    }
  }
}
