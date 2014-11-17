/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.batch

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.mayo.MayoService
import com.netflix.spinnaker.orca.pipeline.Pipeline
import groovy.transform.CompileStatic
import groovy.transform.TupleConstructor
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.core.step.tasklet.TaskletStep
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.beans.factory.annotation.Autowired
import static org.springframework.batch.repeat.RepeatStatus.FINISHED

@CompileStatic
@TupleConstructor(includeFields = true)
class PipelineInitializerTasklet implements Tasklet {
  @Autowired
  MayoService mayoService

  @Autowired
  ObjectMapper objectMapper

  static TaskletStep initializationStep(StepBuilderFactory steps, Pipeline pipeline) {
    steps.get("orca-init-step")
      .tasklet(new PipelineInitializerTasklet(pipeline))
      .build()
  }

  public static final String APPLICATION_CONTEXT_KEY = "application"
  public static final String PIPELINE_CONTEXT_KEY = "pipeline"

  private final Pipeline pipeline

  @Override
  RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    addAppConfigToPipeline()
    addPipelineConfigToContext(chunkContext)
    return FINISHED
  }

  private addAppConfigToPipeline() {
    Map appConfig = objectMapper.readValue(mayoService.getApplication(pipeline.application).body.in(), Map)
    pipeline.config.putAll(appConfig)
  }

  private addPipelineConfigToContext(ChunkContext chunkContext) {
    chunkContext.stepContext.stepExecution.jobExecution.with {
      pipeline.id = id.toString()
      executionContext.put(PIPELINE_CONTEXT_KEY, pipeline)
      executionContext.put(APPLICATION_CONTEXT_KEY, pipeline.application)
      for (stage in pipeline.stages) {
        executionContext.put(stage.type, stage)
      }
    }
  }
}
