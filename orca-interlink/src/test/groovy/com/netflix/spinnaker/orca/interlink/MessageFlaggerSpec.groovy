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

import com.netflix.spinnaker.orca.interlink.events.CancelInterlinkEvent
import com.netflix.spinnaker.orca.interlink.events.DeleteInterlinkEvent
import com.netflix.spinnaker.orca.interlink.events.PauseInterlinkEvent
import spock.lang.Specification

import static com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType.ORCHESTRATION

class MessageFlaggerSpec extends Specification {
  def cancel = new CancelInterlinkEvent(ORCHESTRATION, "id", "user", "reason")
  def cancelByOtherUser = new CancelInterlinkEvent(ORCHESTRATION, "id", "otherUser", "reason")
  def pause = new PauseInterlinkEvent(ORCHESTRATION, "id", "user")
  def delete = new DeleteInterlinkEvent(ORCHESTRATION, "id")
  def deleteOtherId = new DeleteInterlinkEvent(ORCHESTRATION, "otherId")

  def 'flagger should flag repeated messages'() {
    given:
    def flagger = new MessageFlagger(32, 2)

    expect:
    !flagger.isFlagged(cancel)
    !flagger.isFlagged(cancel)
    !flagger.isFlagged(pause)

    // 3rd time this event is seen trips the flagger
    flagger.isFlagged(cancel)
  }

  def 'older events are evicted and not tripping the flagger'() {
    given: 'a small flagger that does not allow duplicates'
    def flagger = new MessageFlagger(3, 1)

    expect:
    !flagger.isFlagged(cancel)
    !flagger.isFlagged(delete)
    !flagger.isFlagged(pause)
    !flagger.isFlagged(deleteOtherId)

    // there was already a cancel, but it should have been evicted
    !flagger.isFlagged(cancel)

    // this time, the duplicate should be flagged
    flagger.isFlagged(cancel)
  }
}
