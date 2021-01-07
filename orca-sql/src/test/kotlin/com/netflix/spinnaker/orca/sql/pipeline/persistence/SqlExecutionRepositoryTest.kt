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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.CompressionType
import com.netflix.spinnaker.config.ExecutionCompressionProperties
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doNothing
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.spy
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import java.nio.charset.StandardCharsets
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.table
import org.mockito.InOrder

class SqlExecutionRepositoryTest : JUnit5Minutests {

  fun tests() = rootContext<Unit> {

    val mockedDslContext = mock<DSLContext>()
    val mockedObjectMapper = mock<ObjectMapper>()
    val testRetryProprties = RetryProperties()
    val testExecutionCompressionProperties = ExecutionCompressionProperties()

    context("execution body compression") {

      testExecutionCompressionProperties.enabled = true
      testExecutionCompressionProperties.bodyCompressionThreshold = 9

      val sqlExecutionRepository = SqlExecutionRepository("test",
        mockedDslContext,
        mockedObjectMapper,
        testRetryProprties,
        10,
        100,
        "poolName",
        null,
        emptyList(),
        testExecutionCompressionProperties
      )

      test("Compression performed when length limit breached") {
        val compressedBody =
          sqlExecutionRepository.getCompressedBody(id = "12345", body = "12345678910")
        assert(compressedBody is ByteArray)
      }

      test("Compression ignored when length limit not breached") {
        assertThat(sqlExecutionRepository.getCompressedBody(id = "12345", body = "123456789"))
          .isEqualTo(null)
      }
    }

    context("upserting executions with body compression") {

      val testTable = table("my_table")
      val testId = "test_id"
      val testBody = "test_body"
      val testPairs = mutableMapOf(
        field("body") to testBody
      )

      test("Compressed upsert not performed when body not compressed") {
        val sqlExecutionRepository = spy(SqlExecutionRepository("test",
          mockedDslContext,
          mockedObjectMapper,
          testRetryProprties,
          10,
          100,
          "poolName",
          null,
          emptyList(),
          testExecutionCompressionProperties))

        doNothing().`when`(sqlExecutionRepository)
          .upsert(mockedDslContext, testTable, testPairs, testPairs, testId)
        doReturn(null).`when`(sqlExecutionRepository)
          .getCompressedBody(testId, testBody)

        sqlExecutionRepository.upsert(mockedDslContext,
          table = testTable,
          insertPairs = testPairs,
          updatePairs = testPairs,
          id = testId,
          enableCompression = true)

        verify(sqlExecutionRepository, times(1)).upsert(
          ctx = mockedDslContext,
          table = table("my_table"),
          insertPairs = testPairs,
          updatePairs = testPairs,
          updateId = testId
        )
      }

      test("Compressed upsert performed when body is compressed") {
        val sqlExecutionRepository = spy(SqlExecutionRepository("test",
          mockedDslContext,
          mockedObjectMapper,
          testRetryProprties,
          10,
          100,
          "poolName",
          null,
          emptyList(),
          testExecutionCompressionProperties))

        val testCompressedBody = testBody.toByteArray(StandardCharsets.UTF_8)
        doReturn(testCompressedBody).`when`(sqlExecutionRepository)
          .getCompressedBody(testId, testBody)
        doNothing().`when`(sqlExecutionRepository)
          .upsert(eq(mockedDslContext), any(), any(), any(), eq(testId))

        sqlExecutionRepository.upsert(mockedDslContext,
          table = testTable,
          insertPairs = testPairs,
          updatePairs = testPairs,
          id = testId,
          enableCompression = true)

        testPairs[field("body")] = ""
        val inOrder: InOrder = inOrder(sqlExecutionRepository)
        inOrder.verify(sqlExecutionRepository).upsert(
          ctx = mockedDslContext,
          table = table("my_table"),
          insertPairs = testPairs,
          updatePairs = testPairs,
          updateId = testId
        )

        val expectedCompressedTablePairs = mapOf(
          field("id") to testId,
          field("compressed_body") to testCompressedBody,
          field("compression_type") to CompressionType.ZLIB.type
        )
        inOrder.verify(sqlExecutionRepository).upsert(
          ctx = mockedDslContext,
          table = table("my_table_compressed_executions"),
          insertPairs = expectedCompressedTablePairs,
          updatePairs = expectedCompressedTablePairs,
          updateId = testId
        )
      }
    }
  }
}
