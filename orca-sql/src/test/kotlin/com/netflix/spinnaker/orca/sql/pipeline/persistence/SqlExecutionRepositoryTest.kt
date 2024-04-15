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
package com.netflix.spinnaker.orca.sql.pipeline.persistence

import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.testcontainers.DockerClientFactory

class SqlExecutionRepositoryTest : JUnit5Minutests {

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    beforeAll {
      assumeTrue(DockerClientFactory.instance().isDockerAvailable)
    }

    after {
      SqlTestUtil.cleanupDb(database.context)
    }

    context("retrievePipelinesForApplication") {
      val pipelineExecution1 = PipelineExecutionImpl(ExecutionType.PIPELINE, "application-1")
      val pipelineExecution2 = PipelineExecutionImpl(ExecutionType.PIPELINE, "application-2")

      test("correctly use where clause") {
        // Store pipelines in two different applications
        sqlExecutionRepository.store(pipelineExecution1)
        sqlExecutionRepository.store(pipelineExecution2)

        val observable = sqlExecutionRepository.retrievePipelinesForApplication("application-2")
        val executions = observable.toList().toBlocking().single()
        assertThat(executions.map(PipelineExecution::getApplication).single()).isEqualTo("application-2")
      }
    }
  }

  private inner class Fixture {
    val database = SqlTestUtil.initTcMysqlDatabase()!!

    val testRetryProprties = RetryProperties()

    val orcaObjectMapper = OrcaObjectMapper.newInstance()

    val sqlExecutionRepository =
      SqlExecutionRepository(
        "test",
        database.context,
        orcaObjectMapper,
        testRetryProprties,
        10,
        100,
        "poolName",
        null,
        emptyList()
      )
  }
}

/**
 * Build a top-level stage. Use in the context of [#pipeline].  This duplicates
 * a function in orca-api-tck, but liquibase complains about duplicate schema
 * files when orca-sql depends on orca-api-tck.
 *
 * Automatically hooks up execution.
 */
private fun PipelineExecution.stage(init: StageExecution.() -> Unit): StageExecution {
  val stage = StageExecutionImpl()
  stage.execution = this
  stage.type = "test"
  stage.refId = "1"
  stages.add(stage)
  stage.init()
  return stage
}
