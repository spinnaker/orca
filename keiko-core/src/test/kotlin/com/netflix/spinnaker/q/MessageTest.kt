/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.q

import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.should.shouldMatch
import com.netflix.spinnaker.spek.shouldEqual
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it

object MessageTest : Spek({
  val message = SimpleMessage("message")

  describe("a message supports attributes") {
    it("should have no attributes by default") {
      message.attributes shouldMatch isEmpty
    }

    it("should return null if attribute does not exist") {
      message.getAttribute<MaxAttemptsAttribute>() shouldEqual null
    }

    it("should support adding an attribute") {
      message.setAttribute(MaxAttemptsAttribute(10))
      message.getAttribute<MaxAttemptsAttribute>()?.maxAttempts shouldEqual 10
    }

//    it("should support removing an attribute") {
//      val attribute = message.getAttribute<MaxAttemptsAttribute>()!!
//      message.removeAttribute(attribute)
//      message.attributes shouldMatch isEmpty
//    }
  }
})
