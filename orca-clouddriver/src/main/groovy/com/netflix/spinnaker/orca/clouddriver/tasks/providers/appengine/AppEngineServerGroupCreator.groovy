/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.appengine

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType.PIPELINE

@Component
class AppEngineServerGroupCreator implements ServerGroupCreator {
  boolean katoResultExpected = false
  String cloudProvider = 'appengine'

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  ArtifactUtils artifactUtils

  @Override
  List<Map> getOperations(StageExecution stage) {
    def operation = [:]

    // If this stage was synthesized by a parallel deploy stage, the operation properties will be under 'cluster'.
    if (stage.context.containsKey("cluster")) {
      operation.putAll(stage.context.cluster as Map)
    } else {
      operation.putAll(stage.context)
    }

    appendArtifactData(stage, operation)
    operation.branch = AppEngineBranchFinder.findInStage(operation, stage) ?: operation.branch

    return [[(OPERATION): operation]]
  }

  @Override
  Optional<String> getHealthProviderName() {
    return Optional.empty()
  }

  void appendArtifactData(StageExecution stage, Map operation) {
    PipelineExecution execution = stage.getExecution()
    if (execution.type == PIPELINE) {
      String expectedId = operation.expectedArtifactId?.trim()
      Artifact expectedArtifact = operation.expectedArtifact
      if (expectedId || expectedArtifact) {
        Artifact boundArtifact = artifactUtils.getBoundArtifactForStage(stage, expectedId, expectedArtifact)
        if (boundArtifact) {
          operation.artifact = boundArtifact
        } else {
          throw new RuntimeException("Missing bound artifact for ID $expectedId")
        }
      }
      List<ArtifactAccountPair> configArtifacts = operation.configArtifacts
      if (configArtifacts != null && configArtifacts.size() > 0) {
        operation.configArtifacts = configArtifacts.collect { artifactAccountPair ->
          def artifact = artifactUtils.getBoundArtifactForStage(stage, artifactAccountPair.id, objectMapper.convertValue(artifactAccountPair.artifact, Artifact.class))
          return ArtifactUtils.withAccount(artifact, artifactAccountPair.account)
        }
      }
    }
  }
}

class ArtifactAccountPair {
  String id
  String account
  Artifact artifact
}
