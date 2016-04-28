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
import com.netflix.spinnaker.orca.igor.BuildService
import com.netflix.spinnaker.orca.pipeline.OrchestrationStarter
import com.netflix.spinnaker.orca.pipeline.PipelineLauncher
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.security.AuthenticatedRequest
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.web.bind.annotation.*

@RestController
@Slf4j
class OperationsController {

  static final String MAX_ARTIFACTS_PROP = OperationsController.simpleName + ".maxArtifacts"
  static final int MAX_ARTIFACTS_DEFAULT = 20

  static final String PREFERRED_ARTIFACTS_PROP = OperationsController.simpleName + ".preferredArtifacts"
  static final String PREFERRED_ARTIFACTS_DEFAULT = ['deb', 'rpm', 'properties', 'yml', 'json', 'xml', 'html', 'txt'].join(',')

  int getMaxArtifacts() {
    environment.getProperty(MAX_ARTIFACTS_PROP, Integer, MAX_ARTIFACTS_DEFAULT)
  }

  List<String> getPreferredArtifacts() {
    environment.getProperty(PREFERRED_ARTIFACTS_PROP, String, PREFERRED_ARTIFACTS_DEFAULT).split(',')
  }

  @Autowired
  PipelineLauncher pipelineLauncher

  @Autowired
  PipelineStarter pipelineStarter

  @Autowired
  Environment environment

  @Autowired
  OrchestrationStarter orchestrationStarter

  @Autowired(required = false)
  BuildService buildService

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  ExecutionRepository executionRepository

  @RequestMapping(value = "/orchestrate", method = RequestMethod.POST)
  Map<String, String> orchestrate(@RequestBody Map pipeline) {

    def json = objectMapper.writeValueAsString(pipeline)
    log.info('received pipeline {}:{}', pipeline.id, json)

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
        pipeline.trigger.parentStatus = parentExecution.status
        pipeline.trigger.parentExecution = parentExecution
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

    def augmentedContext = [:]
    augmentedContext.put('trigger', pipeline.trigger)
    def processedPipeline = ContextParameterProcessor.process(pipeline, augmentedContext)

    startPipeline(processedPipeline)
  }

  private void getBuildInfo(Map trigger) {
    if (trigger.master && trigger.job && trigger.buildNumber) {
      def buildInfo = buildService.getBuild(trigger.buildNumber, trigger.master, trigger.job)
      if (buildInfo?.artifacts) {
        buildInfo.artifacts = filterArtifacts(buildInfo.artifacts)
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

  private List<Map> filterArtifacts(List<Map> artifacts) {
    if (!artifacts) {
      return artifacts
    }

    final int maxArtifacts = getMaxArtifacts()
    final List<String> preferred = getPreferredArtifacts()

    if (artifacts.size() < maxArtifacts) {
      return artifacts
    }

    def ext = { String filename ->
      if (!filename) {
        return null
      }
      int idx = filename.lastIndexOf('.')
      if (idx == -1) {
        return null
      }
      filename.substring(idx + 1).toLowerCase()
    }

    def pri = { String extension ->
      int pri = preferred.indexOf(extension)
      if (pri == -1) {
        return preferred.size() + 1
      }
      return pri
    }

    artifacts.sort { Map a, Map b ->
      pri(ext(a?.fileName)) <=> pri(ext(b?.fileName))
    }.take(maxArtifacts)
  }

  @RequestMapping(value = "/ops", method = RequestMethod.POST)
  Map<String, String> ops(@RequestBody List<Map> input) {
    startTask([application: null, name: null, appConfig: null, stages: input])
  }

  @RequestMapping(value = "/ops", consumes = "application/context+json", method = RequestMethod.POST)
  Map<String, String> ops(@RequestBody Map input) {
    startTask([application: input.application, name: input.description, appConfig: input.appConfig, stages: input.job])
  }

  @RequestMapping(value = "/health", method = RequestMethod.GET)
  Boolean health() {
    true
  }

  private Map<String, String> startPipeline(Map config) {
    def json = objectMapper.writeValueAsString(config)
    log.info('requested pipeline: {}', json)

    def pipeline
    if (config.executionEngine == "v2") {
      pipeline = pipelineLauncher.start(json)
    } else {
      pipeline = pipelineStarter.start(json)
    }

    [ref: "/pipelines/${pipeline.id}".toString()]
  }

  private Map<String, String> startTask(Map config) {
    def json = objectMapper.writeValueAsString(config)
    log.info('requested task:{}', json)
    def pipeline = orchestrationStarter.start(json)
    [ref: "/tasks/${pipeline.id}".toString()]
  }
}
