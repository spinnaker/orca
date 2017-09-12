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

package com.netflix.spinnaker.spinq.metrics

import com.netflix.spinnaker.spinq.Message
import com.netflix.spinnaker.spinq.Queue
import com.netflix.spinnaker.time.toInstant
import org.springframework.context.ApplicationEvent

sealed class QueueEvent(source: Queue) : ApplicationEvent(source) {
  val instant
    get() = timestamp.toInstant()
}

sealed class PayloadQueueEvent(
  source: Queue,
  val payload: Message
) : QueueEvent(source)

class QueuePolled(source: Queue) : QueueEvent(source)
class RetryPolled(source: Queue) : QueueEvent(source)
class MessagePushed(source: Queue, payload: Message) : PayloadQueueEvent(source, payload)
class MessageAcknowledged(source: Queue) : QueueEvent(source)
class MessageRetried(source: Queue) : QueueEvent(source)
class MessageDead(source: Queue) : QueueEvent(source)
class MessageDuplicate(source: Queue, payload: Message) : PayloadQueueEvent(source, payload)
class LockFailed(source: Queue) : QueueEvent(source)

