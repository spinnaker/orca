/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.orca.peering

import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.notifications.AbstractPollingNotificationAgent
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock
import com.netflix.spinnaker.orca.pipeline.persistence.DualExecutionRepository
import org.slf4j.LoggerFactory

/**
 * Requires DualExecutionRepository being enabled to run migrations.
 */
class PeeringAgent(
  clusterLock: NotificationClusterLock,
  private val front50Service: Front50Service,
  private val dualExecutionRepository: DualExecutionRepository,
  private val pollingIntervalMs: Long
) : AbstractPollingNotificationAgent(clusterLock) {

  private val log = LoggerFactory.getLogger(javaClass)

  override fun tick() {
    log.info("Hello, is it me you're peering for?")
  }

  override fun getPollingInterval() = pollingIntervalMs
  override fun getNotificationType() = "peeringAgent"
}
