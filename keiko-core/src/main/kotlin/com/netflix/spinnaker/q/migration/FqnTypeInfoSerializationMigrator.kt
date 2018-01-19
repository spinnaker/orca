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
package com.netflix.spinnaker.q.migration

import com.fasterxml.jackson.annotation.JsonTypeName
import com.netflix.spinnaker.q.JSON_NAME_PROPERTY
import org.slf4j.LoggerFactory
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger

/**
 * This migrator converts Keiko messages & attributes to use JsonTypeInfo to use NAME on a "kind" property.
 *
 * NOTE: Running this migrator is unnecessary except on services that were using Keiko before 1.5.3, or Orca
 * before it was moved over to Keiko.
 */
class FqnTypeInfoSerializationMigrator : SerializationMigrator {

  private val log = LoggerFactory.getLogger(javaClass)

  private val migrated = AtomicInteger(0)
  private var lastReport: LocalTime? = null

  override fun migrate(json: MutableMap<String, Any?>): MutableMap<String, Any?> {
    migrateFqnClass(json)

    if (json.containsKey("attributes") && json["attributes"] is List<*>) {
      (json["attributes"] as List<*>)
        .filterIsInstance<MutableMap<String, Any?>>()
        .forEach { migrateFqnClass(it) }
    }

    return json
  }

  override fun report() {
    log.info(
      "Migrated {} objects since {}",
      migrated.get(),
      if (lastReport == null) "startup" else lastReport!!.format(DateTimeFormatter.ISO_TIME)
    )

    migrated.set(0)
    lastReport = LocalTime.now()
  }

  private fun migrateFqnClass(json: MutableMap<String, Any?>) {
    if (json.containsKey("@class")) {
      migrated.incrementAndGet()

      val cls = javaClass.classLoader.loadClass(json["@class"].toString())
      val typeInfoName = cls.annotations.filterIsInstance<JsonTypeName>().firstOrNull() ?: return

      json.apply {
        remove("@class")
        put(JSON_NAME_PROPERTY, typeInfoName.value)
      }
    }
  }
}
