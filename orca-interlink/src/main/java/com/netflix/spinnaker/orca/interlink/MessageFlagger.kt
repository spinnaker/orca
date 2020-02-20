/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.orca.interlink

import com.google.common.base.Preconditions
import com.google.common.collect.EvictingQueue
import com.google.common.hash.HashCode
import com.google.common.hash.Hashing
import com.netflix.spinnaker.orca.interlink.events.InterlinkEvent

class MessageFlagger(maxSize: Int, val threshold: Int) {
  val queue: EvictingQueue<HashCode> = EvictingQueue.create(maxSize)
  val hasher = Hashing.goodFastHash(64)

  init {
    Preconditions.checkArgument(maxSize >= threshold, "maxSize (%s) has to be larger than threshold (%s)", maxSize, threshold)
    Preconditions.checkArgument(threshold > 0, "threshold (%s) has to be > 0", threshold)
  }

  fun isFlagged(event: InterlinkEvent): Boolean {
    val hashCode = hasher.hashString("${event.eventType}:${event.executionId}:${event.executionType}", Charsets.UTF_8)
    val count = queue.count { it == hashCode }
    queue.add(hashCode)

    return count >= threshold
  }
}
