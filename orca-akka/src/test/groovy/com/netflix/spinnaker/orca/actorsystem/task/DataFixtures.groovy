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

package com.netflix.spinnaker.orca.actorsystem.task

import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.actorsystem.task.TaskMessage.RequestTask
import com.netflix.spinnaker.orca.pipeline.model.DefaultTask
import com.netflix.spinnaker.orca.pipeline.model.Pipeline

trait DataFixtures {
  Pipeline pipelineWithOneTask() {
    def pipeline = Pipeline
      .builder()
      .withId()
      .withStage("whatever")
      .build()
    def stage = pipeline.stages.first()
    stage.id = UUID.randomUUID().toString()
    def taskModel = new DefaultTask(id: UUID.randomUUID().toString())
    stage.tasks << taskModel
    return pipeline
  }

  RequestTask runFirstTask(Pipeline pipeline) {
    new RequestTask(Task, pipeline.id, pipeline.stages.first().id, pipeline.stages.first().tasks.first().id)
  }

}
