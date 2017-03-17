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

package com.netflix.spinnaker.orca.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.extensionpoint.pipeline.PipelinePreprocessor
import com.netflix.spinnaker.orca.igor.BuildArtifactFilter
import com.netflix.spinnaker.orca.igor.BuildService
import com.netflix.spinnaker.orca.pipeline.OrchestrationLauncher
import com.netflix.spinnaker.orca.pipeline.PipelineLauncher
import com.netflix.spinnaker.orca.pipeline.PipelineValidator
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.InvalidPipelineTemplateException
import com.netflix.spinnaker.security.AuthenticatedRequest
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

import javax.servlet.http.HttpServletResponse

@RestController
@Slf4j
class OperationsController {
  @Autowired
  PipelineLauncher pipelineLauncher

  @Autowired
  OrchestrationLauncher orchestrationLauncher

  @Autowired(required = false)
  BuildService buildService

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  ExecutionRepository executionRepository

  @Autowired(required = false)
  BuildArtifactFilter buildArtifactFilter

  @Autowired(required = false)
  List<PipelinePreprocessor> pipelinePreprocessors

  @RequestMapping(value = "/orchestrate", method = RequestMethod.POST)
  Map<String, String> orchestrate(@RequestBody Map pipeline, HttpServletResponse response) {
    parsePipelineTrigger(executionRepository, buildService, pipeline)
    Map trigger = pipeline.trigger

    for (PipelinePreprocessor preprocessor : (pipelinePreprocessors ?: [])) {
      pipeline = preprocessor.process(pipeline)
    }

    pipeline.trigger = trigger

    def json = objectMapper.writeValueAsString(pipeline)
    log.info('received pipeline {}:{}', pipeline.id, json)

    if (pipeline.disabled) {
      throw new DisabledPipelineException("Pipeline is disabled and cannot be started.")
    }

    def parallel = pipeline.parallel as Boolean
    if (!parallel) {
      convertLinearToParallel(pipeline)
    }

    def augmentedContext = [trigger: pipeline.trigger]
    def processedPipeline = ContextParameterProcessor.process(pipeline, augmentedContext, false)

    if (pipeline.plan == true) {
      log.info('not starting pipeline (plan: true): {}', pipeline.id)
      if (pipeline.errors != null) {
        throw new InvalidPipelineTemplateException("Pipeline template is invalid", pipeline.errors as List<Map<String, Object>>)
      }
      return processedPipeline
    }

    if (pipeline.errors != null) {
      throw new InvalidPipelineTemplateException("Pipeline template is invalid", pipeline.errors as List<Map<String, Object>>)
    }

    startPipeline(processedPipeline)
  }

  private void parsePipelineTrigger(ExecutionRepository executionRepository, BuildService buildService, Map pipeline) {
    if (!(pipeline.trigger instanceof Map)) {
      pipeline.trigger = [:]
    }

    if (!pipeline.trigger.type) {
      pipeline.trigger.type = "manual"
    }

    if (!pipeline.trigger.user) {
      pipeline.trigger.user = AuthenticatedRequest.getSpinnakerUser().orElse("[anonymous]")
    }

    if (buildService) {
      getBuildInfo(pipeline.trigger)
    }

    if (pipeline.trigger.parentPipelineId && !pipeline.trigger.parentExecution) {
      Pipeline parentExecution = executionRepository.retrievePipeline(pipeline.trigger.parentPipelineId)
      if (parentExecution) {
        pipeline.trigger.isPipeline         = true
        pipeline.trigger.parentStatus       = parentExecution.status
        pipeline.trigger.parentExecution    = parentExecution
        pipeline.trigger.parentPipelineName = parentExecution.name
      }
    }

    if (pipeline.parameterConfig) {
      if (!pipeline.trigger.parameters) {
        pipeline.trigger.parameters = [:]
      }

      pipeline.parameterConfig.each {
        pipeline.trigger.parameters[it.name] = pipeline.trigger.parameters.containsKey(it.name) ? pipeline.trigger.parameters[it.name] : it.default
      }
    }
  }

  private void getBuildInfo(Map trigger) {
    if (trigger.master && trigger.job && trigger.buildNumber) {
      def buildInfo = buildService.getBuild(trigger.buildNumber, trigger.master, trigger.job)
      if (buildInfo?.artifacts) {
        if (!buildArtifactFilter) {
          log.warn("Igor is not enabled, unable to lookup build artifacts. Fix this by setting igor.enabled: true")
        } else {
          buildInfo.artifacts = buildArtifactFilter.filterArtifacts(buildInfo.artifacts)
        }

      }
      trigger.buildInfo = buildInfo
      if (trigger.propertyFile) {
        trigger.properties = buildService.getPropertyFile(
          trigger.buildNumber as Integer,
          trigger.propertyFile as String,
          trigger.master as String,
          trigger.job as String
        )
      }
    } else if (trigger?.registry && trigger?.repository && trigger?.tag) {
      trigger.buildInfo = [
        taggedImages: [[registry: trigger.registry, repository: trigger.repository, tag: trigger.tag]]
      ]
    }
  }

  @RequestMapping(value = "/ops", method = RequestMethod.POST)
  Map<String, String> ops(@RequestBody List<Map> input) {
    startTask([application: null, name: null, appConfig: null, stages: input])
  }

  @RequestMapping(value = "/ops", consumes = "application/context+json", method = RequestMethod.POST)
  Map<String, String> ops(@RequestBody Map input) {
    startTask([application: input.application, name: input.description, appConfig: input.appConfig, stages: input.job])
  }

  private void convertLinearToParallel(Map<String, Serializable> pipelineConfig) {
    def stages = (List<Map<String, Object>>) pipelineConfig.stages
    stages.eachWithIndex { Map<String, Object> stage, int index ->
      stage.put("refId", String.valueOf(index));
      if (index > 0) {
        stage.put("requisiteStageRefIds", Collections.singletonList(String.valueOf(index - 1)));
      } else {
        stage.put("requisiteStageRefIds", Collections.emptyList());
      }
    }

    pipelineConfig.parallel = Boolean.TRUE
  }

  private Map<String, String> startPipeline(Map config) {
    def json = objectMapper.writeValueAsString(config)
    log.info('requested pipeline: {}', json)

    def pipeline = pipelineLauncher.start(json)

    [ref: "/pipelines/${pipeline.id}".toString()]
  }

  private Map<String, String> startTask(Map config) {
    convertLinearToParallel(config)
    def json = objectMapper.writeValueAsString(config)
    log.info('requested task:{}', json)
    def pipeline = orchestrationLauncher.start(json)
    [ref: "/tasks/${pipeline.id}".toString()]
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(InvalidPipelineTemplateException)
  Map invalidPipelineTemplateHandler(InvalidPipelineTemplateException e) {
    return [message: e.message, status:HttpStatus.BAD_REQUEST, errors: e.getErrors()]
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(DisabledPipelineException)
  Map disabledPipelineHandler(DisabledPipelineException e) {
    return [message: e.message, status: HttpStatus.BAD_REQUEST]
  }

  @ResponseStatus(HttpStatus.BAD_REQUEST)
  @ExceptionHandler(PipelineValidator.PipelineValidationFailed)
  Map disabledPipelineHandler(PipelineValidator.PipelineValidationFailed e) {
    return [message: e.message, status: HttpStatus.BAD_REQUEST]
  }

  @Deprecated
  static class DisabledPipelineException extends RuntimeException {
    DisabledPipelineException(String msg) {
      super(msg)
    }
  }
}
