/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.bakery.pipeline

import com.netflix.spinnaker.orca.batch.StageBuilder
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.batch.core.job.builder.JobFlowBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class MultiRegionBakeStage extends StageBuilder {
  static final String MAYO_CONFIG_TYPE = "multiBake"

  @Autowired
  BakeStage bakeStage

  MultiRegionBakeStage() {
    super(MAYO_CONFIG_TYPE)
  }

  @Override
  JobFlowBuilder build(JobFlowBuilder jobBuilder, Stage stage) {
    def flow1 = new Flow
  }
}
