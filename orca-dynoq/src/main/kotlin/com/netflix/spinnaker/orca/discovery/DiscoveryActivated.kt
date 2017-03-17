/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.discovery

import com.netflix.appinfo.InstanceInfo.InstanceStatus.UP
import com.netflix.spinnaker.kork.eureka.RemoteStatusChangedEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationListener
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A component that starts doing something when the instance is up in discovery
 * and stops doing that thing when it goes down.
 */
abstract class DiscoveryActivated
  : ApplicationListener<RemoteStatusChangedEvent> {

  override fun onApplicationEvent(event: RemoteStatusChangedEvent) {
    if (event.source.status == UP) {
      log.info("Bean [${javaClass.simpleName}] starting...")
      enable()
    } else if (event.source.previousStatus == UP) {
      log.info("Bean [${javaClass.simpleName}] stopping...")
      disable()
    }
  }

  private fun enable() = enabled.set(true)

  private fun disable() = enabled.set(false)

  protected fun ifEnabled(block: () -> Unit) {
    if (enabled.get()) {
      block.invoke()
    }
  }

  protected val log: Logger = LoggerFactory.getLogger(javaClass)

  private val enabled = AtomicBoolean(false)
}
