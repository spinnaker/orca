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

package com.netflix.spinnaker.orca.clouddriver.tasks.image;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FindImageFromTagsTask implements CloudProviderAware, RetryableTask {
  @Autowired ObjectMapper objectMapper;

  @Autowired List<ImageFinder> imageFinders;

  @Value("${tasks.find-image-from-tags-timeout-millis:600000}")
  private Long findImageFromTagsTimeoutMillis;

  @Override
  public TaskResult execute(StageExecution stage) {
    String cloudProvider = getCloudProvider(stage);

    ImageFinder imageFinder =
        imageFinders.stream()
            .filter(it -> it.getCloudProvider().equals(cloudProvider))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "ImageFinder not found for cloudProvider " + cloudProvider));

    StageData stageData = (StageData) stage.mapTo(StageData.class);

    if (stageData.tags == null) {
      stageData.tags = Collections.emptyMap();
    }

    List<String> warnings = new ArrayList<>();
    Collection<ImageFinder.ImageDetails> imageDetails =
        imageFinder.byTags(stage, stageData.packageName, stageData.tags, warnings);

    if (imageDetails == null || imageDetails.isEmpty()) {
      throw new IllegalStateException(
          "Could not find tagged image for package: "
              + stageData.packageName
              + " and tags: "
              + stageData.tags);
    }

    List<Artifact> artifacts = new ArrayList<>();
    imageDetails.forEach(
        imageDetail -> artifacts.add(generateArtifactFrom(imageDetail, cloudProvider)));

    Map<String, Object> stageOutputs = new HashMap<>();
    stageOutputs.put("amiDetails", imageDetails);
    stageOutputs.put("artifacts", artifacts);

    if (!warnings.isEmpty()) {
      Map<String, String[]> messages = new HashMap<>();
      messages.put("errors", warnings.toArray(new String[0]));
      Map<String, Object> details = new HashMap<>();
      details.put("details", messages);
      stageOutputs.put("exception", details);
    }

    return TaskResult.builder(ExecutionStatus.SUCCEEDED)
        .context(stageOutputs)
        .outputs(Collections.singletonMap("deploymentDetails", imageDetails))
        .build();
  }

  private Artifact generateArtifactFrom(
      ImageFinder.ImageDetails imageDetails, String cloudProvider) {
    Map<String, Object> metadata = new HashMap<>();
    try {
      ImageFinder.JenkinsDetails jenkinsDetails = imageDetails.getJenkins();
      metadata.put("build_info_url", jenkinsDetails.get("host"));
      metadata.put("build_number", jenkinsDetails.get("number"));
    } catch (Exception e) {
      // This is either all or nothing
    }

    return Artifact.builder()
        .name(imageDetails.getImageName())
        .reference(imageDetails.getImageId())
        .location(imageDetails.getRegion())
        .type(cloudProvider + "/image")
        .metadata(metadata)
        .uuid(UUID.randomUUID().toString())
        .build();
  }

  @Override
  public long getBackoffPeriod() {
    return 10000;
  }

  @Override
  public long getTimeout() {
    return this.findImageFromTagsTimeoutMillis;
  }

  static class StageData {
    @JsonProperty String packageName;

    @JsonProperty Map<String, String> tags;
  }
}
