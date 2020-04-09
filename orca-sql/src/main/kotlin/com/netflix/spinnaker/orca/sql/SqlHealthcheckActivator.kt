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
package com.netflix.spinnaker.orca.sql

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.q.Activator
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.jooq.DSLContext
import org.jooq.impl.DSL.table
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

/**
 * Continually performs writes to the SQL backend in order to detect master
 * failures and to deactivate queue processing as a result.
 *
 * Behaves quite like load balancer healthchecks. By default, this activator
 * will fail fast and be slow to come healthy again.
 */
class SqlHealthcheckActivator(
  private val jooq: DSLContext,
  private val registry: Registry,
  private val unhealthyThreshold: Int = 2,
  private val healthyThreshold: Int = 10
) : Activator {

  private val log = LoggerFactory.getLogger(javaClass)

  val _enabled = AtomicBoolean(false)
  private val _healthException: AtomicReference<Exception> = AtomicReference()

  private val healthyCounter = AtomicInteger(0)
  private val unhealthyCounter = AtomicInteger(0)

  private val invocationId = registry.createId("sql.queueActivator.invocations")

  override val enabled: Boolean
    get() = _enabled.get()

  val healthException: Exception?
    get() = _healthException.get()

  @Scheduled(fixedDelay = 1_000)
  fun performWrite() {
    try {
      // THIS IS VERY ADVANCED
      jooq.delete(table("healthcheck")).execute()

      if (!_enabled.get()) {
        if (healthyCounter.incrementAndGet() >= healthyThreshold) {
          _enabled.set(true)
          _healthException.set(null)
          log.info("Enabling activator after $healthyThreshold healthy cycles")
        }
      }
      unhealthyCounter.set(0)
    } catch (e: Exception) {
      _healthException.set(e)
      healthyCounter.set(0)
      unhealthyCounter.incrementAndGet().also { unhealthyCount ->
        log.error("Encountered exception, $unhealthyCount/$unhealthyThreshold failures", e)
        if (unhealthyCount >= unhealthyThreshold && _enabled.get()) {
          log.warn("Encountered exception, disabling Activator after $unhealthyCount failures")
          _enabled.set(false)
        }
      }
    } finally {
      registry.counter(invocationId.withTag("status", if (enabled) "enabled" else "disabled")).increment()
    }
  }
}
