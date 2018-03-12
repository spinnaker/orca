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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.gce

import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.orca.pipeline.util.ArtifactResolver
import com.netflix.spinnaker.orca.test.model.ExecutionBuilder
import spock.lang.Specification

class GoogleServerGroupCreatorSpec extends Specification {

  ArtifactResolver artifactResolver = Mock(ArtifactResolver)

  def "should get operations"() {
    given:
    def basectx = [
      account          : "abc",
      region           : "north-pole",
      zone             : "north-pole-1",
      deploymentDetails: [[imageId: "testImageId", region: "north-pole"]],
    ].asImmutable()

    when:
    def ctx = [:] << basectx
    def stage = ExecutionBuilder.stage {
      context.putAll(ctx)
    }
    def ops = new GoogleServerGroupCreator(artifactResolver: artifactResolver).getOperations(stage)

    then:
    ops == [
      [
        "createServerGroup": [
          account          : "abc",
          credentials      : "abc",
          image            : "testImageId",
          region           : "north-pole",
          zone             : "north-pole-1",
          deploymentDetails: [[imageId: "testImageId", region: "north-pole"]],
        ],
      ]
    ]

    when: "fallback to non-region matching image"
    ctx = [:] << basectx
    ctx.region = "south-pole"
    ctx.zone = "south-pole-1"
    stage = ExecutionBuilder.stage {
      context.putAll(ctx)
    }
    ops = new GoogleServerGroupCreator(artifactResolver: artifactResolver).getOperations(stage)

    then:
    ops == [
      [
        "createServerGroup": [
          account          : "abc",
          credentials      : "abc",
          image            : "testImageId",
          region           : "south-pole",
          zone             : "south-pole-1",
          deploymentDetails: [[imageId: "testImageId", region: "north-pole"]],
        ],
      ]
    ]

    when: "use artifact when set as imageSource"
    ctx = [:] << basectx
    ctx.imageSource = "artifact"
    ctx.imageArtifactId = "b3d33e5a-0423-4bdc-8e37-dea923b57c9a"
    stage = ExecutionBuilder.stage {
      context.putAll(ctx)
    }
    artifactResolver.getBoundArtifactForId(*_) >> {
      Artifact artifact = new Artifact();
      artifact.setName("santaImage")
      return artifact
    }
    ops = new GoogleServerGroupCreator(artifactResolver: artifactResolver).getOperations(stage)

    then:
    ops == [
      [
        "createServerGroup": [
          imageSource      : "artifact",
          imageArtifactId  : "b3d33e5a-0423-4bdc-8e37-dea923b57c9a",
          account          : "abc",
          credentials      : "abc",
          image            : "santaImage",
          region           : "north-pole",
          zone             : "north-pole-1",
          deploymentDetails: [[imageId: "testImageId", region: "north-pole"]],
        ],
      ]
    ]

    when: "throw error if imageSource is artifact but no artifact is specified"
    ctx = [:] << basectx
    ctx.imageSource = "artifact"
    stage = ExecutionBuilder.stage {
      context.putAll(ctx)
    }
    artifactResolver.getBoundArtifactForId(*_) >> {
      Artifact artifact = new Artifact();
      artifact.setName("santaImage")
      return artifact
    }
    ops = new GoogleServerGroupCreator(artifactResolver: artifactResolver).getOperations(stage)

    then:
    IllegalStateException ise = thrown()
    ise.message == "Image source was set to artifact but no artifact was specified."

    when: "throw error if no image found"
    ctx = [:] << basectx
    ctx.deploymentDetails = []
    stage = ExecutionBuilder.stage {
      context.putAll(ctx)
    }
    new GoogleServerGroupCreator(artifactResolver: artifactResolver).getOperations(stage)

    then:
    ise = thrown()
    ise.message == "No image could be found in north-pole."
  }
}
