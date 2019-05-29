/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.dryrun.stub

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.absoluteValue

internal fun timestamp(format: String = "yyyyMMddHHmmss") = DateTimeFormatter
  .ofPattern(format)
  .format(LocalDateTime.now())

internal fun randomHex(length: Int) = UUID
  .randomUUID()
  .leastSignificantBits
  .absoluteValue
  .toString(16)
  .substring(0 until minOf(length, 16))

internal fun randomNumeric(length: Int) = UUID
  .randomUUID()
  .leastSignificantBits
  .absoluteValue
  .toString(10)
  .substring(0 until minOf(length, 16))
