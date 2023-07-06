/*
 * Copyright 2023 Apple, Inc.
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

package com.netflix.spinnaker.q.sql

import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import com.netflix.spinnaker.q.AckAttemptsAttribute
import com.netflix.spinnaker.q.DeadMessageCallback
import com.netflix.spinnaker.q.TestMessage
import com.netflix.spinnaker.q.metrics.EventPublisher
import com.netflix.spinnaker.time.MutableClock
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith
import java.util.*


@RunWith(JUnitPlatform::class)
class SqlAckQueueTest : Spek({
  describe("both values of resetAttemptsOnAck") {
    // map of resetAttemptsOnAck to expected number of ackAttempts still on the message after ack
    val flagToAckAttempts = mapOf(
      true to 0,
      false to 1
    )

    // check both values of resetAttemptsOnAck
    flagToAckAttempts.forEach { resetFlag, expectedAckAttempts ->
      val testDescription = "SqlQueue with resetAttemptsOnAck = $resetFlag"

      given(testDescription) {
        var queue: SqlQueue? = null
        val clock = MutableClock()
        val deadMessageHandler: DeadMessageCallback = mock()
        val publisher: EventPublisher = mock()

        val testDb = SqlTestUtil.initTcMysqlDatabase()
        val jooq = testDb.context

        fun resetMocks() = reset(deadMessageHandler, publisher)

        fun stopQueue() {
          SqlTestUtil.cleanupDb(jooq)
        }

        fun startQueue() {
          queue = createQueueWithResetAck(clock, deadMessageHandler, publisher, resetFlag) // from SqlQueueTest
        }

        describe("message is dropped once then retried successfully") {
          beforeGroup(::startQueue)
          afterGroup(::stopQueue)
          afterGroup(::resetMocks)

          given("a test message") {
            val message = TestMessage("a")

            on("pushing a message that gets dropped") {
              with(queue!!) {
                push(message)
                poll { _, _ -> } // do not ack the message after de-queuing it
                clock.incrementBy(ackTimeout)

                retry() // make it available again
                clock.incrementBy(ackTimeout)
              }
            }

            it("has an ackAttempt count of 1 upon retrying") {
              with(queue!!) {
                poll { msg, ack ->
                  val ackAttemptsAttribute = (msg.getAttribute() ?: AckAttemptsAttribute())
                  assert(ackAttemptsAttribute.ackAttempts == 1)
                  ack() // *now* we acknowledge the message
                  push(msg) // and re-enqueue it (as with a RunTask returning RUNNING)
                }
              }
            }

            val validatesAckAttrAfterAckdRetry = if (expectedAckAttempts == 0) {
              "has reset its ackAttempts upon being ack'd"
            } else {
              "still has ackAttempts=1 even after being ack'd"
            }
            it(validatesAckAttrAfterAckdRetry) {
              with(queue!!) {
                poll { msg, ack ->
                  val ackAttemptsAttribute = (msg.getAttribute() ?: AckAttemptsAttribute())
                  assert(ackAttemptsAttribute.ackAttempts == expectedAckAttempts)
                }
              }
            }
          }
        }
      }
    }
  }
})
