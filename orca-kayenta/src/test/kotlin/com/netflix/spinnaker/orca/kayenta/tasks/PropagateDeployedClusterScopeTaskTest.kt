/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kayenta.tasks

import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.CreateServerGroupStage
import com.netflix.spinnaker.orca.fixture.pipeline
import com.netflix.spinnaker.orca.fixture.stage
import com.netflix.spinnaker.orca.kato.pipeline.ParallelDeployStage
import com.netflix.spinnaker.orca.kayenta.pipeline.DeployCanaryClustersStage
import com.netflix.spinnaker.orca.kayenta.pipeline.KayentaCanaryStage
import org.assertj.core.api.Assertions.*
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it

internal object PropagateDeployedClusterScopeTaskTest : Spek({

  val subject = PropagateDeployedClusterScopeTask()

  describe("a canary deployment pipeline") {

    val controlClusterName = "spindemo-prestaging-prestaging-baseline"
    val controlClusterRegion = "us-west-1"
    val experimentClusterName = "spindemo-prestaging-prestaging-canary"
    val experimentClusterRegion = "us-west-1"

    val baseline = mapOf(
      "application" to "spindemo",
      "account" to "prod",
      "cluster" to "spindemo-prestaging-prestaging"
    )
    val controlCluster = mapOf(
      "account" to "prod",
      "application" to "spindemo",
      "availabilityZones" to mapOf(
        controlClusterRegion to listOf("us-west-1a", "us-west-1c")
      ),
      "capacity" to mapOf("desired" to 1, "max" to 1, "min" to 1),
      "cloudProvider" to "aws",
      "cooldown" to 10,
      "copySourceCustomBlockDeviceMappings" to true,
      "ebsOptimized" to false,
      "enabledMetrics" to listOf<Any>(),
      "freeFormDetails" to "prestaging-baseline",
      "healthCheckGracePeriod" to 600,
      "healthCheckType" to "EC2",
      "iamRole" to "spindemoInstanceProfile",
      "instanceMonitoring" to true,
      "instanceType" to "m3.large",
      "interestingHealthProviderNames" to listOf("Amazon"),
      "keyPair" to "nf-prod-keypair-a",
      "loadBalancers" to listOf<Any>(),
      "moniker" to mapOf(
        "app" to "spindemo",
        "cluster" to controlClusterName,
        "detail" to "prestaging-baseline",
        "stack" to "prestaging"
      ),
      "provider" to "aws",
      "securityGroups" to listOf("sg-b575ded0", "sg-b775ded2", "sg-dbe43abf"),
      "spotPrice" to "",
      "stack" to "prestaging",
      "subnetType" to "internal (vpc0)",
      "suspendedProcesses" to listOf<Any>(),
      "tags" to mapOf<String, Any>(),
      "targetGroups" to listOf<Any>(),
      "targetHealthyDeployPercentage" to 100,
      "terminationPolicies" to listOf("Default"),
      "useAmiBlockDeviceMappings" to false,
      "useSourceCapacity" to false
    )
    val experimentCluster = mapOf(
      "account" to "prod",
      "application" to "spindemo",
      "availabilityZones" to mapOf(
        experimentClusterRegion to listOf("us-west-1a", "us-west-1c")
      ),
      "capacity" to mapOf("desired" to 1, "max" to 1, "min" to 1),
      "cloudProvider" to "aws",
      "cooldown" to 10,
      "copySourceCustomBlockDeviceMappings" to true,
      "ebsOptimized" to false,
      "enabledMetrics" to listOf<Any>(),
      "freeFormDetails" to "prestaging-canary",
      "healthCheckGracePeriod" to 600,
      "healthCheckType" to "EC2",
      "iamRole" to "spindemoInstanceProfile",
      "instanceMonitoring" to true,
      "instanceType" to "m3.large",
      "interestingHealthProviderNames" to listOf("Amazon"),
      "keyPair" to "nf-prod-keypair-a",
      "loadBalancers" to listOf<Any>(),
      "moniker" to mapOf(
        "app" to "spindemo",
        "cluster" to experimentClusterName,
        "detail" to "prestaging-canary",
        "stack" to "prestaging"
      ),
      "provider" to "aws",
      "securityGroups" to listOf("sg-b575ded0", "sg-b775ded2", "sg-dbe43abf"),
      "spotPrice" to "",
      "stack" to "prestaging",
      "subnetType" to "internal (vpc0)",
      "suspendedProcesses" to listOf<Any>(),
      "tags" to mapOf<String, Any>(),
      "targetGroups" to listOf<Any>(),
      "targetHealthyDeployPercentage" to 100,
      "terminationPolicies" to listOf("Default"),
      "useAmiBlockDeviceMappings" to false,
      "useSourceCapacity" to false
    )
    val pipeline = pipeline {
      stage {
        refId = "1"
        id = "top-level-canary"
        type = KayentaCanaryStage.STAGE_TYPE
        context["deployments"] = mapOf(
          "baseline" to baseline,
          "control" to controlCluster,
          "experiment" to experimentCluster
        )
      }
      stage {
        refId = "1<1"
        id = "deploy-canary-clusters"
        parentStageId = "top-level-canary"
        type = DeployCanaryClustersStage.STAGE_TYPE
      }
      stage {
        refId = "1<1<1"
        id = "deploy-control-cluster"
        parentStageId = "deploy-canary-clusters"
        type = ParallelDeployStage.PIPELINE_CONFIG_TYPE
        name = "Deploy control cluster"
      }
      stage {
        refId = "1<1<1<1"
        parentStageId = "deploy-control-cluster"
        type = CreateServerGroupStage.PIPELINE_CONFIG_TYPE
        context["deploy.server.groups"] = mapOf(
          controlClusterRegion to listOf("$controlClusterName-v000")
        )
      }
      stage {
        refId = "1<1<2"
        id = "deploy-experiment-cluster"
        parentStageId = "deploy-canary-clusters"
        type = ParallelDeployStage.PIPELINE_CONFIG_TYPE
        name = "Deploy experiment cluster"
      }
      stage {
        refId = "1<1<2<1"
        parentStageId = "deploy-experiment-cluster"
        type = CreateServerGroupStage.PIPELINE_CONFIG_TYPE
        context["deploy.server.groups"] = mapOf(
          experimentClusterRegion to listOf("$experimentClusterName-v000")
        )
      }
    }
    val canaryDeployStage = pipeline.stageByRef("1<1")

    it("generates a summary of deployed canary clusters") {
      subject.execute(canaryDeployStage).let {
        assertThat(it.outputs).isEqualTo(mapOf(
          "deployedCanaryClusters" to mapOf(
            "controlLocation" to controlClusterRegion,
            "controlServerGroup" to "$controlClusterName-v000",
            "experimentLocation" to experimentClusterRegion,
            "experimentServerGroup" to "$experimentClusterName-v000"
          )
        ))
      }
    }
  }
})
