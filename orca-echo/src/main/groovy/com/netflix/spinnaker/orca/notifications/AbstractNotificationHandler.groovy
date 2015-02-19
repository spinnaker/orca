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

package com.netflix.spinnaker.orca.notifications

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.appinfo.InstanceInfo
import com.netflix.discovery.DiscoveryClient
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value

abstract class AbstractNotificationHandler implements NotificationHandler {
  private final Logger log = LoggerFactory.getLogger(getClass());

  @Autowired
  PipelineStarter pipelineStarter

  @Autowired
  ObjectMapper objectMapper

  //TODO(cfieber) we aren't currently injecting a full discovery client in kork-core
  @Autowired(required = false)
  DiscoveryClient discoveryClient

  private final Map input

  @Value('${notificationHandlers.enabled:true}')
  Boolean notificationHandlersEnabled

  AbstractNotificationHandler(Map input) {
    this.input = input
  }

  abstract String getHandlerType()

  boolean handles(String type) {
    type == handlerType
  }

  @Override
  final void run() {
    if (inService) {
      handle(input)
    }
  }

  boolean isInService() {
    if (!notificationHandlersEnabled) {
      log.info("NotificationHandlers disabled via configuration")
      return false
    }

    if (discoveryClient == null) {
      log.info("No DiscoveryClient found, assuming In Service")
      return true
    }

    def remoteStatus = discoveryClient.instanceRemoteStatus
    if (remoteStatus == InstanceInfo.InstanceStatus.UP) {
      return true
    }
    log.info("NotificationHandlers disabled, current status ${remoteStatus}")
    return false
  }
}
