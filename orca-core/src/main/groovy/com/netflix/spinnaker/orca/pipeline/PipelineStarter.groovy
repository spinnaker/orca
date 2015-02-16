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

package com.netflix.spinnaker.orca.pipeline

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.transform.TypeCheckingMode
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.orca.batch.StageBuilder
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.job.builder.JobFlowBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import static com.netflix.spinnaker.orca.batch.PipelineInitializerTasklet.initializationStep

@Component
@CompileStatic
class PipelineStarter extends AbstractOrchestrationInitiator<Pipeline> {

  @Autowired ExecutionRepository executionRepository

  PipelineStarter() {
    super("pipeline")
  }

  @Override
  protected Pipeline create(Map<String, Object> config) {
    return parseConfig(config)
  }

  /**
   * Builds a _pipeline_ based on config from _Mayo_.
   *
   * @param configJson _Mayo_ pipeline configuration.
   * @return the pipeline that was created.
   */
  @CompileDynamic
  protected Job build(Map<String, Object> config, Pipeline pipeline) {
    def jobBuilder = jobs.get("Pipeline:${pipeline.application}:${pipeline.name}:${pipeline.id}")
    pipelineListeners.each {
      jobBuilder = jobBuilder.listener(it)
    }
    jobBuilder = jobBuilder.flow(initializationStep(steps, pipeline)) as JobFlowBuilder
    buildFlow(jobBuilder, pipeline).build().build()
  }

  @Override
  protected void persistExecution(Pipeline pipeline) {
    executionRepository.store(pipeline)
  }

  @VisibleForTesting
  @PackageScope
  static Pipeline parseConfig(Map<String, Object> config) {
    Pipeline.builder()
            .withApplication(config.application.toString())
            .withName(config.name.toString())
            .withTrigger((Map<String, Object>) config.trigger)
            .withStages((List<Map<String, Object>>) config.stages)
            .build()
  }

  // static compiler doesn't seem to know what to do here anymore...
  @CompileStatic(TypeCheckingMode.SKIP)
  private JobFlowBuilder buildFlow(JobFlowBuilder jobBuilder, Pipeline pipeline) {
    def stages = []
    stages.addAll(pipeline.stages)
    stages.inject(jobBuilder, this.&createStage)
  }

  protected JobFlowBuilder createStage(JobFlowBuilder jobBuilder, Stage<Pipeline> stage) {
    builderFor(stage).build(jobBuilder, stage)
  }

  protected StageBuilder builderFor(Stage<Pipeline> stage) {
    if (stages.containsKey(stage.type)) {
      stages.get(stage.type)
    } else {
      throw new NoSuchStageException(stage.type)
    }
  }

  @Override
  protected JobParameters createJobParameters(Pipeline pipeline, Map<String, Object> config) {
    def params = new JobParametersBuilder(super.createJobParameters(pipeline, config))
    params.addString("pipeline", pipeline.id)
    params.toJobParameters()
  }
}
