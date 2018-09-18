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

import com.netflix.spinnaker.config.SqlProperties
import liquibase.integration.spring.SpringLiquibase
import org.springframework.jdbc.datasource.SingleConnectionDataSource

/**
 * Performs migrations for Orca.
 *
 * By default, it will just run the primary `changelog-master.yml`. If provided,
 * however, additional changelog sets can be defined to support custom extensions.
 *
 * IMPORTANT: For any custom migrations you're going to make, do not make changes
 * to the tables created by Orca OSS or you may have a bad time. Spinnaker's OSS
 * cannot and will not make considerations for custom integrations layered atop it.
 *
 * As such, if you need to add metadata for executions, or store information for
 * other systems, create them as separate, isolated tables, ideally with a
 * namespace so that you do not risk table name collision with future changes to
 * Orca itself.
 */
class MigrationService(
  private val properties: SqlProperties
) {

  fun migrate() {
    properties.migration
      .run {
        val ds = SingleConnectionDataSource(jdbcUrl, user, password, false)
        if (driver != null) {
          ds.setDriverClassName(driver)
        }
        ds
      }
      .let { ds ->
        mutableListOf("db/changelog-master.yml")
          .apply { addAll(properties.migration.additionalChangeLogs) }
          .map { "classpath:$it" }
          .distinct()
          .forEach {
            SpringLiquibase().apply {
              changeLog = it
              dataSource = ds
            }
          }
      }
  }
}
