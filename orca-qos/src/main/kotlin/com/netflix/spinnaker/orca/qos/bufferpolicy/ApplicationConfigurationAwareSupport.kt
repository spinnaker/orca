/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.orca.qos.bufferpolicy

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.model.Application
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

open class ApplicationConfigurationAwareSupport(
  private val clock: Clock,
  private val front50Service: Front50Service
) {
  private val log: Logger = LoggerFactory.getLogger(javaClass)
  private val applicationCache = applicationsCache(10)
  internal fun Application.quietTimeEnabled(): Boolean = this.quietTimeConfiguration().isEnabled()

  internal fun getApplication(name: String): Application {
    return applicationCache.get(name)
  }

  internal fun Application.hasExitedQuietTimeWindow(): Boolean = !this.hasEnteredQuietTimeWindow()

  internal fun Application.hasEnteredQuietTimeWindow(): Boolean {
    val quietTimeConfiguration = quietTimeConfiguration()
    return quietTimeConfiguration.isEnabled() && quietTimeConfiguration.inWindow(LocalDateTime.now(clock))
  }

  private fun Application.quietTimeConfiguration(): QuietTimeConfig {
    (details()["quietTime"] as? Map<String, Any>)?.let {
      return QuietTimeConfig(it)
    }

    return QuietTimeConfig(emptyMap())
  }

  private fun applicationsCache(durationMins: Long): LoadingCache<String, Application> {
    return CacheBuilder.newBuilder()
      .expireAfterWrite(durationMins, TimeUnit.MINUTES)
      .build(
        object : CacheLoader<String, Application>() {
          override fun load(name: String): Application {
            return front50Service.get(name)
          }
        }
      )
  }
}

class QuietTimeConfig(
  private val map: Map<String, Any>
) {
  fun isEnabled(): Boolean {
    return map.containsKey("enabled") && map["enabled"] as Boolean
  }

  // ISO_LOCAL_DATE_TIME example 2018-06-20T10:15:30"
  fun inWindow(dateTime: LocalDateTime): Boolean {
    (map["dates"] as? List<Map<*, *>>)?.forEach { startEndDate ->
      val startDateTime: LocalDateTime = (startEndDate["startDateTime"] as String).toLocalDateTime()
      val endDateTime: LocalDateTime = (startEndDate["endDateTime"] as String).toLocalDateTime()
      return (dateTime.isAfter(startDateTime) && dateTime.isBefore(endDateTime))
    }

    return false
  }
}

private fun String.toLocalDateTime(): LocalDateTime = LocalDateTime.parse(this)
