/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.q

/**
 * Implementations handle a single message type from the queue.
 */
interface MessageHandler<M : Message> : (Message) -> Unit {

  val messageType: Class<M>
  val queue: Queue

  override fun invoke(message: Message): Unit =
    if (messageType.isAssignableFrom(message.javaClass)) {
      @Suppress("UNCHECKED_CAST")
      handle(message as M)
    } else {
      throw IllegalArgumentException("Unsupported message type ${message.javaClass.simpleName}")
    }

  fun handle(message: M): Unit
}
