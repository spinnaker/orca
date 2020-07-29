package com.netflix.spinnaker.orca.remote.stage.messages

import com.netflix.spinnaker.kork.annotations.Alpha

@Alpha
interface RemoteStageMessageHandler<M: RemoteStageMessage> {
  fun supports(message: Class<RemoteStageMessage>): Boolean
  fun handle(message: M)
}
