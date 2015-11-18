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

package com.netflix.spinnaker.orca.bakery.tasks

import com.netflix.spinnaker.orca.bakery.api.BakeRequest
import com.netflix.spinnaker.orca.bakery.api.BakeStatus
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.OperatingSystem
import rx.Observable
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.bakery.api.BakeStatus.State.RUNNING
import static java.util.UUID.randomUUID

class CreateBakeTaskSpec extends Specification {

  @Subject
    task = new CreateBakeTask()
  Stage stage
  def mapper = new OrcaObjectMapper()
  def runningStatus = new BakeStatus(id: randomUUID(), state: RUNNING)

  @Shared
  Pipeline pipeline = new Pipeline()

  def bakeConfig = [
    region   : "us-west-1",
    package  : "hodor",
    user     : "bran",
    baseOs   : OperatingSystem.ubuntu.name(),
    baseLabel: BakeRequest.Label.release.name()
  ]

  @Shared
  def bakeConfigWithCloudProviderType = [
    region            : "us-west-1",
    package           : "hodor",
    user              : "bran",
    cloudProviderType : "aws",
    baseOs            : OperatingSystem.ubuntu.name(),
    baseLabel         : BakeRequest.Label.release.name()
  ]

  @Shared
  def bakeConfigWithRebake = [
    region            : "us-west-1",
    package           : "hodor",
    user              : "bran",
    cloudProviderType : "aws",
    baseOs            : OperatingSystem.ubuntu.name(),
    baseLabel         : BakeRequest.Label.release.name(),
    rebake            : true
  ]

  @Shared
  def buildInfo = [
    artifacts: [
      [fileName: 'hodor_1.1_all.deb'],
      [fileName: 'hodor-1.1.noarch.rpm']
    ]
  ]

  @Shared
  def buildInfoWithUrl = [
    url: "http://spinnaker.builds.test.netflix.net/job/SPINNAKER-package-echo/69/",
    artifacts: [
      [fileName: 'hodor_1.1_all.deb'],
      [fileName: 'hodor-1.1.noarch.rpm']
    ]
  ]

  @Shared
  def buildInfoWithUrlAndSCM = [
    url: "http://spinnaker.builds.test.netflix.net/job/SPINNAKER-package-echo/69/",
    artifacts: [
      [fileName: 'hodor_1.1_all.deb'],
      [fileName: 'hodor-1.1.noarch.rpm']
    ],
    scm: [
      [name  : "refs/remotes/origin/master",
       sha1  : "f83a447f8d02a40fa84ec9d4d0dccd263d51782d",
       branch: "master"]
    ]
  ]

  @Shared
  def buildInfoWithUrlAndTwoSCMs = [
    url: "http://spinnaker.builds.test.netflix.net/job/SPINNAKER-package-echo/69/",
    artifacts: [
      [fileName: 'hodor_1.1_all.deb'],
      [fileName: 'hodor-1.1.noarch.rpm']
    ],
    scm: [
      [name  : "refs/remotes/origin/master",
       sha1  : "f83a447f8d02a40fa84ec9d4d0dccd263d51782d",
       branch: "master"],
      [name  : "refs/remotes/origin/some-feature",
       sha1  : "1234567f8d02a40fa84ec9d4d0dccd263d51782d",
       branch: "some-feature"]
    ]
  ]

  @Shared
  def buildInfoWithUrlAndMasterAndDevelopSCMs = [
    url: "http://spinnaker.builds.test.netflix.net/job/SPINNAKER-package-echo/69/",
    artifacts: [
      [fileName: 'hodor_1.1_all.deb'],
      [fileName: 'hodor-1.1.noarch.rpm']
    ],
    scm: [
      [name  : "refs/remotes/origin/master",
       sha1  : "f83a447f8d02a40fa84ec9d4d0dccd263d51782d",
       branch: "master"],
      [name  : "refs/remotes/origin/develop",
       sha1  : "1234567f8d02a40fa84ec9d4d0dccd263d51782d",
       branch: "develop"]
    ]
  ]

  @Shared
  def buildInfoWithUrlNoMatch = [
    url: "http://spinnaker.builds.test.netflix.net/job/SPINNAKER-package-echo/70/",
    artifacts: [
      [fileName: 'hodor_1.1_all.deb'],
      [fileName: 'hodor-1.1.noarch.rpm']
    ]
  ]

  @Shared
  def buildInfoNoMatch = [
    artifacts: [
      [fileName: 'hodornodor_1.1_all.deb'],
      [fileName: 'hodor-1.1.noarch.rpm']
    ]
  ]

  @Shared
  def invalidArtifactList = [
    artifacts: [
      [yolo: 'blinky'],
      [hulk: 'hogan']
    ]
  ]

  def setup() {
    task.mapper = mapper
    stage = new PipelineStage(pipeline, "bake", bakeConfig).asImmutable()
  }

  def "creates a bake for the correct region"() {
    given:
    task.bakery = Mock(BakeryService)

    when:
    task.execute(stage)

    then:
    1 * task.bakery.createBake(bakeConfig.region, _ as BakeRequest, null) >> Observable.from(runningStatus)
  }

  def "gets bake configuration from job context"() {
    given:
    def bake
    task.bakery = Mock(BakeryService) {
      1 * createBake(*_) >> {
        bake = it[1]
        Observable.from(runningStatus)
      }
    }

    when:
    task.execute(stage)

    then:
    bake.user == bakeConfig.user
    bake.packageName == bakeConfig.package
    bake.baseOs.name() == bakeConfig.baseOs
    bake.baseLabel.name() == bakeConfig.baseLabel
  }

  @Unroll
  def "finds package details from context and trigger"() {
    given:
    Pipeline pipelineWithTrigger = new Pipeline.Builder().withTrigger([buildInfo: triggerInfo]).build()
    bakeConfig.buildInfo = contextInfo

    Stage stage = new PipelineStage(pipelineWithTrigger, "bake", bakeConfig).asImmutable()
    def bake
    task.bakery = Mock(BakeryService) {
      1 * createBake(*_) >> {
        bake = it[1]
        Observable.from(runningStatus)
      }
    }

    when:
    def result = task.execute(stage)

    then:
    bake.packageName == 'hodor_1.1_all'
    result.outputs.bakePackageName == 'hodor_1.1_all'

    where:
    triggerInfo      | contextInfo
    null             | buildInfo
    buildInfo        | null
    buildInfo        | buildInfo
    buildInfo        | buildInfoNoMatch
    buildInfoNoMatch | buildInfo
  }

  @Unroll
  def "fails if pipeline trigger or context includes artifacts but no artifact for the bake package"() {
    given:
    Pipeline pipelineWithTrigger = new Pipeline.Builder().withTrigger([buildInfo: triggerInfo]).build()
    Stage stage = new PipelineStage(pipelineWithTrigger, "bake", bakeConfig).asImmutable()
    bakeConfig.buildInfo = contextInfo

    when:
    task.execute(stage)

    then:
    IllegalStateException ise = thrown(IllegalStateException)
    ise.message.startsWith("Unable to find deployable artifact starting with hodor_ and ending with .deb in")

    where:
    contextInfo         | triggerInfo
    null                | buildInfoNoMatch
    buildInfoNoMatch    | null
    buildInfoNoMatch    | buildInfoNoMatch
    buildInfoNoMatch    | invalidArtifactList
    invalidArtifactList | buildInfoNoMatch
  }

  @Unroll
  def "fails if pipeline trigger and context includes artifacts have a different match"() {
    given:
    Pipeline pipelineWithTrigger = new Pipeline.Builder().withTrigger([buildInfo: buildInfo]).build()
    Stage stage = new PipelineStage(pipelineWithTrigger, "bake", bakeConfig).asImmutable()
    bakeConfig.buildInfo = [
      artifacts: [
        [fileName: 'hodor_1.2_all.deb'],
        [fileName: 'hodor-1.2.noarch.rpm']
      ]
    ]

    when:
    task.execute(stage)

    then:
    IllegalStateException ise = thrown(IllegalStateException)
    ise.message.startsWith("Found build artifact in Jenkins")
  }

  def "outputs the status of the bake"() {
    given:
    task.bakery = Stub(BakeryService) {
      createBake(*_) >> Observable.from(runningStatus)
    }

    when:
    def result = task.execute(stage)

    then:
    with(result.outputs.status) {
      id == runningStatus.id
      state == runningStatus.state
    }
  }

  def "outputs the packageName of the bake"() {
    given:
    task.bakery = Stub(BakeryService) {
      createBake(*_) >> Observable.from(runningStatus)
    }

    when:
    def result = task.execute(stage)

    then:
    result.outputs.bakePackageName == bakeConfig.package
  }

  @Unroll
  def "build info with url yields bake stage output containing build host, job and build number"() {
    given:
    Pipeline pipelineWithTrigger = new Pipeline.Builder().withTrigger([buildInfo: triggerInfo]).build()
    bakeConfig.buildInfo = contextInfo

    Stage stage = new PipelineStage(pipelineWithTrigger, "bake", bakeConfig).asImmutable()
    task.bakery = Stub(BakeryService) {
      createBake(*_) >> Observable.from(runningStatus)
    }
    task.extractBuildDetails = true

    when:
    def result = task.execute(stage)

    then:
    result.outputs.with {
      bakePackageName == "hodor_1.1_all"
      buildHost == "http://spinnaker.builds.test.netflix.net/"
      job == "SPINNAKER-package-echo"
      buildNumber == "69"
      !commitHash
    }

    where:
    triggerInfo      | contextInfo
    buildInfoWithUrl | null
    null             | buildInfoWithUrl
  }

  @Unroll
  def "build info with url and scm yields bake stage output containing build host, job, build number and commit hash"() {
    given:
    Pipeline pipelineWithTrigger = new Pipeline.Builder().withTrigger([buildInfo: triggerInfo]).build()
    bakeConfig.buildInfo = contextInfo

    Stage stage = new PipelineStage(pipelineWithTrigger, "bake", bakeConfig).asImmutable()
    task.bakery = Stub(BakeryService) {
      createBake(*_) >> Observable.from(runningStatus)
    }
    task.extractBuildDetails = true

    when:
    def result = task.execute(stage)

    then:
    result.outputs.with {
      bakePackageName == "hodor_1.1_all"
      buildHost == "http://spinnaker.builds.test.netflix.net/"
      job == "SPINNAKER-package-echo"
      buildNumber == "69"
      commitHash == "f83a447f8d02a40fa84ec9d4d0dccd263d51782d"
    }

    where:
    triggerInfo            | contextInfo
    buildInfoWithUrlAndSCM | null
    null                   | buildInfoWithUrlAndSCM
  }

  @Unroll
  def "build info with url and two scms yields bake stage output containing build host, job, build number and correctly-chosen commit hash"() {
    given:
    Pipeline pipelineWithTrigger = new Pipeline.Builder().withTrigger([buildInfo: triggerInfo]).build()
    bakeConfig.buildInfo = contextInfo

    Stage stage = new PipelineStage(pipelineWithTrigger, "bake", bakeConfig).asImmutable()
    task.bakery = Stub(BakeryService) {
      createBake(*_) >> Observable.from(runningStatus)
    }
    task.extractBuildDetails = true

    when:
    def result = task.execute(stage)

    then:
    result.outputs.with {
      bakePackageName == "hodor_1.1_all"
      buildHost == "http://spinnaker.builds.test.netflix.net/"
      job == "SPINNAKER-package-echo"
      buildNumber == "69"
      commitHash == "1234567f8d02a40fa84ec9d4d0dccd263d51782d"
    }

    where:
    triggerInfo                | contextInfo
    buildInfoWithUrlAndTwoSCMs | null
    null                       | buildInfoWithUrlAndTwoSCMs
  }

  @Unroll
  def "build info with url and master and develop scms yields bake stage output containing build host, job, build number and first commit hash"() {
    given:
    Pipeline pipelineWithTrigger = new Pipeline.Builder().withTrigger([buildInfo: triggerInfo]).build()
    bakeConfig.buildInfo = contextInfo

    Stage stage = new PipelineStage(pipelineWithTrigger, "bake", bakeConfig).asImmutable()
    task.bakery = Stub(BakeryService) {
      createBake(*_) >> Observable.from(runningStatus)
    }
    task.extractBuildDetails = true

    when:
    def result = task.execute(stage)

    then:
    result.outputs.with {
      bakePackageName == "hodor_1.1_all"
      buildHost == "http://spinnaker.builds.test.netflix.net/"
      job == "SPINNAKER-package-echo"
      buildNumber == "69"
      commitHash == "f83a447f8d02a40fa84ec9d4d0dccd263d51782d"
    }

    where:
    triggerInfo                             | contextInfo
    buildInfoWithUrlAndMasterAndDevelopSCMs | null
    null                                    | buildInfoWithUrlAndMasterAndDevelopSCMs
  }

  @Unroll
  def "build info without url yields bake stage output without build host, job, build number and commit hash"() {
    given:
    Pipeline pipelineWithTrigger = new Pipeline.Builder().withTrigger([buildInfo: triggerInfo]).build()
    bakeConfig.buildInfo = contextInfo

    Stage stage = new PipelineStage(pipelineWithTrigger, "bake", bakeConfig).asImmutable()
    task.bakery = Stub(BakeryService) {
      createBake(*_) >> Observable.from(runningStatus)
    }
    task.extractBuildDetails = extractBuildDetails

    when:
    def result = task.execute(stage)

    then:
    result.outputs.bakePackageName == "hodor_1.1_all"
    !result.outputs.buildHost
    !result.outputs.job
    !result.outputs.buildNumber
    !result.outputs.commitHash

    where:
    triggerInfo | contextInfo | extractBuildDetails
    buildInfo   | null        | true
    null        | buildInfo   | true
    buildInfo   | null        | false
    null        | buildInfo   | false
  }

  @Unroll
  def "build info with url yields bake request containing build host, job and build number"() {
    given:
    Pipeline pipelineWithTrigger = new Pipeline.Builder().withTrigger([buildInfo: triggerInfo]).build()
    bakeConfig.buildInfo = contextInfo

    Stage stage = new PipelineStage(pipelineWithTrigger, "bake", bakeConfig).asImmutable()
    task.bakery = Mock(BakeryService)
    task.extractBuildDetails = true

    when:
    task.execute(stage)

    then:
    1 * task.bakery.createBake(bakeConfig.region,
                               {
                                 it.user == "bran" &&
                                 it.packageName == "hodor_1.1_all" &&
                                 it.baseLabel == BakeRequest.Label.release &&
                                 it.baseOs == OperatingSystem.ubuntu &&
                                 it.buildHost == "http://spinnaker.builds.test.netflix.net/" &&
                                 it.job == "SPINNAKER-package-echo" &&
                                 it.buildNumber == "69"
                                 it.commitHash == null
                               },
                               null) >> Observable.from(runningStatus)

    where:
    triggerInfo      | contextInfo
    buildInfoWithUrl | null
    null             | buildInfoWithUrl
  }

  @Unroll
  def "build info with url but without extractBuildDetails yields bake request without build host, job, build number, and commit hash"() {
    given:
    Pipeline pipelineWithTrigger = new Pipeline.Builder().withTrigger([buildInfo: triggerInfo]).build()
    bakeConfig.buildInfo = contextInfo

    Stage stage = new PipelineStage(pipelineWithTrigger, "bake", bakeConfig).asImmutable()
    task.bakery = Mock(BakeryService)

    when:
    task.execute(stage)

    then:
    1 * task.bakery.createBake(bakeConfig.region,
                               {
                                 it.user == "bran" &&
                                 it.packageName == "hodor_1.1_all" &&
                                 it.baseLabel == BakeRequest.Label.release &&
                                 it.baseOs == OperatingSystem.ubuntu &&
                                 it.buildHost == null &&
                                 it.job == null &&
                                 it.buildNumber == null &&
                                 it.commitHash == null
                               },
                               null) >> Observable.from(runningStatus)

    where:
    triggerInfo      | contextInfo
    buildInfoWithUrl | null
    null             | buildInfoWithUrl
  }

  @Unroll
  def "build info without url yields bake request without build host, job, build number and commit hash"() {
    given:
    Pipeline pipelineWithTrigger = new Pipeline.Builder().withTrigger([buildInfo: triggerInfo]).build()
    bakeConfig.buildInfo = contextInfo

    Stage stage = new PipelineStage(pipelineWithTrigger, "bake", bakeConfig).asImmutable()
    task.bakery = Mock(BakeryService)

    when:
    task.execute(stage)

    then:
    1 * task.bakery.createBake(bakeConfig.region,
                               {
                                 it.user == "bran" &&
                                 it.packageName == "hodor_1.1_all" &&
                                 it.baseLabel == BakeRequest.Label.release &&
                                 it.baseOs == OperatingSystem.ubuntu &&
                                 it.buildHost == null &&
                                 it.job == null &&
                                 it.buildNumber == null &&
                                 it.commitHash == null
                               },
                               null) >> Observable.from(runningStatus)

    where:
    triggerInfo | contextInfo
    buildInfo   | null
    null        | buildInfo
  }

  @Unroll
  def "propagation of cloudProviderType is feature-flagged"() {
    given:
    Stage stage = new PipelineStage(new Pipeline(), "bake", bakeConfigWithCloudProviderType).asImmutable()
    def bake
    task.bakery = Mock(BakeryService) {
      1 * createBake(*_) >> {
        bake = it[1]
        Observable.from(runningStatus)
      }
    }
    task.propagateCloudProviderType = propagateCloudProviderType

    when:
    task.execute(stage)

    then:
    bake.cloudProviderType == expectedCloudProviderType
    bake.user              == bakeConfigWithCloudProviderType.user
    bake.packageName       == bakeConfigWithCloudProviderType.package
    bake.baseOs.name()     == bakeConfigWithCloudProviderType.baseOs
    bake.baseLabel.name()  == bakeConfigWithCloudProviderType.baseLabel

    where:
    propagateCloudProviderType | expectedCloudProviderType
    false                      | null
    true                       | BakeRequest.CloudProviderType.aws
  }

  def "sets rebake query parameter if rebake flag is set in job context"() {
    given:
    Stage stage = new PipelineStage(new Pipeline(), "bake", bakeConfigWithRebake).asImmutable()
    task.bakery = Mock(BakeryService)

    when:
    task.execute(stage)

    then:
    1 * task.bakery.createBake(bakeConfig.region,
                               {
                                 println "** it=$it packageName=$it.packageName"
                                 it.user == "bran" &&
                                 it.packageName == "hodor" &&
                                 it.baseLabel == BakeRequest.Label.release &&
                                 it.baseOs == OperatingSystem.ubuntu
                               },
                               "1") >> Observable.from(runningStatus)
  }

}
