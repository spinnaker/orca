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

package com.netflix.spinnaker.orca.mahe.pipeline

import com.netflix.spinnaker.orca.mahe.tasks.GutenbergPublishTask
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.TaskNode
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class GutenbergPublishStage implements StageDefinitionBuilder {
  public static final String PIPELINE_CONFIG_TYPE = getType(GutenbergPublishStage)
  public static final String TOPIC = "topic"
  public static final String DATA_POINTER = "dataPointer"
  public static final String GUTENBERG_RESULT = "gutenbergResult"
  public static final String S3_BUCKET_NAME = "s3BucketName"
  public static final String S3_OBJECT_NAME = "s3ObjectName"

  @Override
  <T extends Execution<T>> void taskGraph(Stage<T> stage, TaskNode.Builder builder) {
    if (stage.context.contentSource != "stringText") {
      builder.withTask("fileUpload", GutenbergFileUploadTask)
    }

    builder.withTask("publish", GutenbergPublishTask)
  }
}

