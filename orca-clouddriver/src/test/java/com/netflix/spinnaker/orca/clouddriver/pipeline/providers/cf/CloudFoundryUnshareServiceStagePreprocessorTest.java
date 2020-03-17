/*
 *  Copyright 2019 Pivotal, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.pipeline.providers.cf;

import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE;
import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf.CloudFoundryMonitorKatoServicesTask;
import com.netflix.spinnaker.orca.clouddriver.tasks.providers.cf.CloudFoundryUnshareServiceTask;
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CloudFoundryUnshareServiceStagePreprocessorTest {
  @Test
  void ensureThatCorrectTasksAreAddedForUnsharingCloudFoundryService() {
    TaskNode.Builder expectedBuilder = TaskNode.Builder(TaskNode.GraphType.FULL);
    expectedBuilder
        .withTask("unshareService", CloudFoundryUnshareServiceTask.class)
        .withTask("monitorUnshareService", CloudFoundryMonitorKatoServicesTask.class);

    CloudFoundryUnshareServiceStagePreprocessor preprocessor =
        new CloudFoundryUnshareServiceStagePreprocessor();
    Map<String, Object> context = new HashMap<>();
    context.put("cloudProvider", "my-cloud");
    context.put("manifest", Collections.singletonMap("type", "direct"));
    StageExecutionImpl stage =
        new StageExecutionImpl(
            new PipelineExecutionImpl(PIPELINE, "orca"), "unshareService", context);

    TaskNode.Builder builder = new TaskNode.Builder(TaskNode.GraphType.FULL);
    preprocessor.addSteps(builder, stage);

    assertThat(builder).isEqualToComparingFieldByFieldRecursively(expectedBuilder);
  }
}
