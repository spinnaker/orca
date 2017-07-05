/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.pipelinetemplate.tasks;

import com.netflix.servo.util.Strings;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;

import java.util.ArrayList;
import java.util.List;

public interface SavePipelineTemplateTask {

  default void validate(PipelineTemplate template) {
    if (template.getId().contains(".")) {
      throw new IllegalArgumentException("Pipeline Template IDs cannot have dots");
    }

    List<String> missingFields = new ArrayList<>();
    if (template.getMetadata().getName() == null || "".equalsIgnoreCase(template.getMetadata().getName())) {
      missingFields.add("metadata.name");
    }
    if (template.getMetadata().getDescription() == null || "".equalsIgnoreCase(template.getMetadata().getDescription())) {
      missingFields.add("metadata.description");
    }

    if (!missingFields.isEmpty()) {
      throw new IllegalArgumentException("Missing required fields: " + Strings.join(",", missingFields.iterator()));
    }
  }
}
