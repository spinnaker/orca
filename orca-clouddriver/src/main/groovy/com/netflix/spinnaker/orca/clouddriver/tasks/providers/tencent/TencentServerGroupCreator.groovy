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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.tencent

import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator
import com.netflix.spinnaker.orca.kato.tasks.DeploymentDetailsAware
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class TencentServerGroupCreator implements ServerGroupCreator, DeploymentDetailsAware {

  boolean katoResultExpected = true
  String cloudProvider = "tencent"

  @Override
  List<Map> getOperations(Stage stage) {
    def ops = []
    def createServerGroupOp = createServerGroupOperation(stage)
    ops.add([(ServerGroupCreator.OPERATION): createServerGroupOp])
    return ops
  }

  @Override
  Optional<String> getHealthProviderName() {
    return Optional.of("Tencent")
  }

  def createServerGroupOperation(Stage stage) {
    def operation = [:]
    def context = stage.context

    operation.putAll(context)

    if (!operation.containsKey("application")) {
      throw new IllegalStateException("No application could be found in ${context}.")
    }

    if (!operation.containsKey("stack")) {
      throw new IllegalStateException("No stack could be found in ${context}.")
    }

    if (!operation.containsKey("accountName")) {
      throw new IllegalStateException("No accountName could be found in ${context}.")
    }

    if (!operation.containsKey("credentials")) {
      throw new IllegalStateException("No credentials could be found in ${context}.")
    }

    if (!operation.containsKey("region")) {
      throw new IllegalStateException("No region could be found in ${context}.")
    }

    if (!operation.containsKey("imageId")) {
      throw new IllegalStateException("No imageId could be found in ${context}.")
    }

    if (!operation.containsKey("instanceType")) {
      throw new IllegalStateException("No instanceType could be found in ${context}.")
    }

    if (!operation.containsKey("maxSize")) {
      throw new IllegalStateException("No maxSize could be found in ${context}.")
    }

    if (!operation.containsKey("minSize")) {
      throw new IllegalStateException("No minSize could be found in ${context}.")
    }

    if (!operation.containsKey("desiredCapacity")) {
      throw new IllegalStateException("No desiredCapacity could be found in ${context}.")
    }

    log.info("Deploying tencent ${operation.imageId} to ${operation.region}")

    return operation
  }

}
