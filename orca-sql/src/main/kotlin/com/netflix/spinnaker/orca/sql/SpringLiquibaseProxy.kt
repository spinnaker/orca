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

import com.netflix.spinnaker.kork.sql.config.SqlProperties
import liquibase.integration.spring.SpringLiquibase
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import javax.sql.DataSource

/**
 * Proxies Spring's Liquibase bean to allow multiple, independent Liquibase
 * changesets to be used within a single Orca application.
 *
 * The use case behind this is to allow private extensions to make additional
 * database schema changes atop OSS.
 *
 * IMPORTANT: While using this, ensure that you do not make any changes to any
 * OSS schemas, and namespace tables that you've created so not to collide with
 * potential future Orca changes. Spinnaker's OSS schema cannot and will not
 * make considerations for custom integrations layered atop its schema.
 */
class SpringLiquibaseProxy(
  private val sqlProperties: SqlProperties
) : SpringLiquibase() {

  init {
    changeLog = "classpath:db/changelog-master.yml"
    dataSource = createDataSource()
  }

  /**
   * Everything has to be done in afterPropertiesSet, because that's how Spring
   * expects things to be done for cleanup purposes, etc.
   */
  override fun afterPropertiesSet() {
    // First do the OSS migrations
    super.afterPropertiesSet()

    SpringLiquibase().apply {
      changeLog = "classpath:db/changelog-keiko.yml"
      dataSource = createDataSource()
      resourceLoader = this@SpringLiquibaseProxy.resourceLoader
    }.afterPropertiesSet()

    // Then if anything else has been defined, do that afterwards
    sqlProperties.migration.additionalChangeLogs
      .filter { !it.isEmpty() }
      .map {
        SpringLiquibase().apply {
          changeLog = "classpath:$it"
          dataSource = createDataSource()
          resourceLoader = this@SpringLiquibaseProxy.resourceLoader
        }
      }
      .forEach {
        it.afterPropertiesSet()
      }
  }

  private fun createDataSource(): DataSource =
    sqlProperties.migration.run {
      val ds = SingleConnectionDataSource(jdbcUrl, user, password, true)
      if (driver != null) {
        ds.setDriverClassName(driver)
      }
      ds
    }
}
