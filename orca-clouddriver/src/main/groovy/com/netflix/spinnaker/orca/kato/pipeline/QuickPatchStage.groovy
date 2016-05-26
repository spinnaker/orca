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
import com.netflix.spinnaker.orca.pipeline.LinearStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.OperatingSystem
import com.netflix.spinnaker.orca.pipeline.util.PackageInfo
import com.netflix.spinnaker.orca.pipeline.util.PackageType
import groovy.util.logging.Slf4j
import org.springframework.batch.core.Step
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import retrofit.RestAdapter
import retrofit.RetrofitError
import retrofit.client.Client

import java.util.concurrent.ConcurrentHashMap

/**
 * Wrapper stage over BuilkQuickPatchStage.  We do this so we can reuse the same steps whether or not we are doing
 * a rolling quick patch.  The difference is that the rolling version will only update one instance at a time while
 * the non-rolling version will act on all instances at once.  This is done by controlling the instances we
 * send to BuilkQuickPatchStage.
 */
@Slf4j
@Component
class QuickPatchStage extends LinearStage {

  @Autowired
  BulkQuickPatchStage bulkQuickPatchStage

  @Autowired
  OortService oortService

  @Autowired
  BakeryService bakeryService

  @Value('${bakery.roscoApisEnabled:false}')
  boolean roscoApisEnabled

  @Value('${bakery.allowMissingPackageInstallation:false}')
  boolean allowMissingPackageInstallation

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  OortHelper oortHelper

  @Autowired
  Client retrofitClient

  public static final String PIPELINE_CONFIG_TYPE = "quickPatch"

  private static INSTANCE_VERSION_SLEEP = 10000

  QuickPatchStage() {
    super(PIPELINE_CONFIG_TYPE)
  }

  @Override
  List<Step> buildSteps(Stage stage) {
    List<Step> steps = []

    PackageType packageType
    if (roscoApisEnabled) {
      def baseImage = bakeryService.getBaseImage(stage.context.cloudProviderType as String,
                                                 stage.context.baseOs as String).toBlocking().single()
      packageType = baseImage.packageType
    } else {
      OperatingSystem operatingSystem = OperatingSystem.valueOf(stage.context.baseOs as String)
      packageType = operatingSystem.packageType
    }
    PackageInfo packageInfo = new PackageInfo(stage,
                                              packageType.packageType,
                                              packageType.versionDelimiter,
                                              true /* extractBuildDetails */,
                                              true /* extractVersion */,
                                              objectMapper)
    String version = stage.context?.patchVersion ?:  packageInfo.findTargetPackage(allowMissingPackageInstallation)?.packageVersion

    stage.context.put("version", version) // so the ui can display the discovered package version and we can verify for skipUpToDate
    def instances = getInstancesForCluster(stage)

    if(instances.size() == 0) {
      // skip since nothing to do
    } else if(stage.context.rollingPatch) { // rolling means instances in the asg will be updated sequentially
      instances.each { key, value ->
        def instance = [:]
        instance.put(key, value)
        def nextStageContext = [:]
        nextStageContext.putAll(stage.context)
        nextStageContext << [instances : instance]
        nextStageContext.put("instanceIds", [key]) // for WaitForDown/UpInstancesTask
        injectAfter(stage, "bulkQuickPatchStage", bulkQuickPatchStage, nextStageContext)
      }
    } else { // quickpatch all instances in the asg at once
      def nextStageContext = [:]
      nextStageContext.putAll(stage.context)
      nextStageContext << [instances : instances]
      nextStageContext.put("instanceIds", instances.collect {key, value -> key}) // for WaitForDown/UpInstancesTask
      injectAfter(stage, "bulkQuickPatchStage", bulkQuickPatchStage, nextStageContext)
    }

    stage.initializationStage = true
    // mark as SUCCEEDED otherwise a stage w/o child tasks will remain in NOT_STARTED
    stage.status = ExecutionStatus.SUCCEEDED
    return steps
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
