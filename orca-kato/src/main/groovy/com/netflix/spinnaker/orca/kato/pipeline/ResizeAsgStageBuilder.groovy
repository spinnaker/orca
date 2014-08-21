/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.pipeline

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.kato.tasks.MonitorKatoTask
import com.netflix.spinnaker.orca.kato.tasks.ResizeAsgTask
import com.netflix.spinnaker.orca.kato.tasks.WaitForCapacityMatchTask
import com.netflix.spinnaker.orca.pipeline.LinearStageBuilder
import org.springframework.batch.core.Step
import org.springframework.stereotype.Component

@Component
@CompileStatic
class ResizeAsgStageBuilder extends LinearStageBuilder {

  public static final String MAYO_CONFIG_TYPE = "resizeAsg"

  ResizeAsgStageBuilder() {
    super(MAYO_CONFIG_TYPE)
  }

  @Override
  protected List<Step> buildSteps() {
    def step1 = steps.get("ResizeAsgStep")
                     .tasklet(buildTask(ResizeAsgTask))
                     .build()
    def step2 = steps.get("MonitorAsgStep")
                     .tasklet(buildTask(MonitorKatoTask))
                     .build()
    def step3 = steps.get("WaitForCapacityMatchStep")
                     .tasklet(buildTask(WaitForCapacityMatchTask))
                     .build()

    [step1, step2, step3]
  }
}
