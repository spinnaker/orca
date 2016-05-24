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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.clouddriver.InstanceService
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.OperatingSystem
import com.netflix.spinnaker.orca.pipeline.util.PackageInfo
import com.netflix.spinnaker.orca.pipeline.util.PackageType
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import retrofit.RestAdapter
import retrofit.RetrofitError
import retrofit.client.Client

import java.util.concurrent.ConcurrentHashMap

import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.StageDefinitionBuilderSupport.*

/**
 * Wrapper stage over BuilkQuickPatchStage.  We do this so we can reuse the same steps whether or not we are doing
 * a rolling quick patch.  The difference is that the rolling version will only update one instance at a time while
 * the non-rolling version will act on all instances at once.  This is done by controlling the instances we
 * send to BuilkQuickPatchStage.
 */
@Slf4j
@Component
class QuickPatchStage implements StageDefinitionBuilder {

  @Autowired
  BulkQuickPatchStage bulkQuickPatchStage

  @Autowired
  OortService oortService

  @Autowired
  BakeryService bakeryService

  @Value('${bakery.roscoApisEnabled:false}')
  boolean roscoApisEnabled

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  OortHelper oortHelper

  @Autowired
  Client retrofitClient

  private static INSTANCE_VERSION_SLEEP = 10000

  @Override
  def <T extends Execution> List<Stage<T>> aroundStages(Stage<T> parentStage) {
    def stages = []

    PackageType packageType
    if (roscoApisEnabled) {
      def baseImage = bakeryService.getBaseImage(parentStage.context.cloudProviderType as String,
                                                 parentStage.context.baseOs as String).toBlocking().single()
      packageType = baseImage.packageType
    } else {
      OperatingSystem operatingSystem = OperatingSystem.valueOf(parentStage.context.baseOs as String)
      packageType = operatingSystem.packageType
    }
    PackageInfo packageInfo = new PackageInfo(parentStage,
                                              packageType.packageType,
                                              packageType.versionDelimiter,
                                              true /* extractBuildDetails */,
                                              true /* extractVersion */,
                                              objectMapper)
    String version = parentStage.context?.patchVersion ?:  packageInfo.findTargetPackage()?.packageVersion

    parentStage.context.put("version", version) // so the ui can display the discovered package version and we can verify for skipUpToDate
    def instances = getInstancesForCluster(parentStage)

    if (instances.size() == 0) {
      // skip since nothing to do
    } else if (parentStage.context.rollingPatch) { // rolling means instances in the asg will be updated sequentially
      instances.each { key, value ->
        def instance = [:]
        instance.put(key, value)
        def nextStageContext = [:]
        nextStageContext.putAll(parentStage.context)
        nextStageContext << [instances: instance]
        nextStageContext.put("instanceIds", [key]) // for WaitForDown/UpInstancesTask

        stages << newStage(
          parentStage.execution,
          bulkQuickPatchStage.type,
          "bulkQuickPatchStage",
          nextStageContext,
          parentStage,
          Stage.SyntheticStageOwner.STAGE_AFTER
        )
      }
    } else { // quickpatch all instances in the asg at once
      def nextStageContext = [:]
      nextStageContext.putAll(parentStage.context)
      nextStageContext << [instances: instances]
      nextStageContext.put("instanceIds", instances.collect { key, value -> key }) // for WaitForDown/UpInstancesTask

      stages << newStage(
        parentStage.execution,
        bulkQuickPatchStage.type,
        "bulkQuickPatchStage",
        nextStageContext,
        parentStage,
        Stage.SyntheticStageOwner.STAGE_AFTER
      )
    }

    parentStage.initializationStage = true
    // mark as SUCCEEDED otherwise a stage w/o child tasks will remain in NOT_STARTED
    parentStage.status = ExecutionStatus.SUCCEEDED

    return stages
  }

  Map getInstancesForCluster(Stage stage) {
    ConcurrentHashMap instances = new ConcurrentHashMap(oortHelper.getInstancesForCluster(stage.context, null, true, false))
    ConcurrentHashMap skippedMap = new ConcurrentHashMap()

    if(stage.context.skipUpToDate) {
      instances.each { instanceId, instanceInfo ->
        // optionally check the installed package version and skip if == target version
        if(getAppVersion(instanceInfo.hostName, stage.context.package) == stage.context.version) {
          skippedMap.put(instanceId, instanceInfo)
          instances.remove(instanceId)
        }
      }
    }

    stage.context.put("skippedInstances", skippedMap)
    return instances
  }

  String getAppVersion(String hostName, String packageName) {
    InstanceService instanceService = createInstanceService("http://${hostName}:5050")
    int retries = 5;
    def instanceResponse
    String version

    while(retries) {
      try {
        instanceResponse  = instanceService.getCurrentVersion(packageName)
        version = objectMapper.readValue(instanceResponse.body.in().text, Map)?.version
      } catch (RetrofitError e) {
        //retry
      }

      if(!version || version.isEmpty()) {
        sleep(INSTANCE_VERSION_SLEEP)
      } else {
        break
      }
      --retries
    }

    // instead of failing the stage if we can't detect the version, try to install new version anyway
    return version
  }

  InstanceService createInstanceService(String address) {
    RestAdapter restAdapter = new RestAdapter.Builder()
      .setEndpoint(address)
      .setClient(retrofitClient)
      .build()
    return restAdapter.create(InstanceService.class)
  }
}
