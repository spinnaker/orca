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

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WaitForUpsertedImageTagsTask implements RetryableTask, CloudProviderAware {
  @Autowired
  List<ImageTagger> imageTaggers;

  @Value("${tasks.waitForImageTags.backoffSeconds:30}")
  int backoffSeconds;

  @Value("${tasks.waitForImageTags.timeoutMinutes:10}")
  int timeoutMinutes;

  @Override
  public TaskResult execute(Stage stage) {
    String cloudProvider = getCloudProvider(stage);

    ImageTagger tagger = imageTaggers.stream()
      .filter(it -> it.getCloudProvider().equalsIgnoreCase(cloudProvider))
      .findFirst()
      .orElseThrow(() -> new IllegalStateException("ImageTagger not found for cloudProvider " + cloudProvider));

    StageData stageData = (StageData) stage.mapTo(StageData.class);
    return new TaskResult(
      tagger.areImagesTagged(stageData.targets, stage) ? ExecutionStatus.SUCCEEDED : ExecutionStatus.RUNNING
    );
  }

  @Override
  public long getBackoffPeriod() {
    return TimeUnit.SECONDS.toMillis(backoffSeconds);
  }

  @Override
  public long getTimeout() {
    return TimeUnit.MINUTES.toMillis(timeoutMinutes);
  }

  static class StageData {
    @JsonProperty
    Collection<ImageTagger.Image> targets;
  }
}
