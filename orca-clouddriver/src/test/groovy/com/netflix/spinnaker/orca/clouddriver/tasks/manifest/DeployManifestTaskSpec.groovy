/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.model.TaskId
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.ArtifactUtils
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import rx.Observable
import spock.lang.Specification
import spock.lang.Subject

class DeployManifestTaskSpec extends Specification {
  String TASK_ID = "12345"

  KatoService katoService = Mock(KatoService)
  def artifactUtils = Stub(ArtifactUtils) {
    getArtifacts(*_) >> []
  }

  @Subject
  DeployManifestTask task = new DeployManifestTask(new ManifestEvaluator(artifactUtils,
    Mock(OortService), new ObjectMapper(), Mock(ContextParameterProcessor), katoService))

  def "enables traffic when the trafficManagement field is absent"() {
    given:
    def stage = createStage([:])

    when:
    task.execute(stage)

    then:
    1 * katoService.requestOperations("kubernetes", {
      Map it -> it.deployManifest.enableTraffic == true && !it.deployManifest.services
    }) >> Observable.from(new TaskId(TASK_ID))
    0 * katoService._
  }

  def "enables traffic when trafficManagement is disabled"() {
    given:
    def stage = createStage([
        trafficManagement: [
            enabled: false
        ]
    ])

    when:
    task.execute(stage)

    then:
    1 * katoService.requestOperations("kubernetes", {
      Map it -> it.deployManifest.enableTraffic == true && !it.deployManifest.services
    }) >> Observable.from(new TaskId(TASK_ID))
    0 * katoService._
  }

  def "enables traffic when trafficManagement is enabled and explicitly enables traffic"() {
    given:
    def stage = createStage([
      trafficManagement: [
        enabled: true,
        options: [
            enableTraffic: true,
            services: ["service my-service"]
        ]
      ]
    ])

    when:
    task.execute(stage)

    then:
    1 * katoService.requestOperations("kubernetes", {
      Map it -> it.deployManifest.enableTraffic == true && it.deployManifest.services == ["service my-service"]
    }) >> Observable.from(new TaskId(TASK_ID))
    0 * katoService._
  }

  def "does not enable traffic when trafficManagement is enabled and enableTraffic is disabled"() {
    given:
    def stage = createStage([
      trafficManagement: [
        enabled: true,
        options: [
          enableTraffic: false,
          services: ["service my-service"]
        ]
      ]
    ])

    when:
    task.execute(stage)

    then:
    1 * katoService.requestOperations("kubernetes", {
      Map it -> it.deployManifest.enableTraffic == false && it.deployManifest.services == ["service my-service"]
    }) >> Observable.from(new TaskId(TASK_ID))
    0 * katoService._
  }


  def createStage(Map extraParams) {
    return new Stage(Stub(Execution), "deployManifest", [
      account: "my-k8s-account",
      cloudProvider: "kubernetes",
      source: "text",
    ] + extraParams)
  }
}
