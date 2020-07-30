package com.netflix.spinnaker.orca.remote.stage.messages

import com.netflix.spinnaker.kork.annotations.Alpha
import com.netflix.spinnaker.orca.api.pipeline.remote.messages.RemoteStageMessage

/**
 * Implementations handle a specific [RemoteStageMessage].
 */
@Alpha
interface RemoteStageMessageHandler<M: RemoteStageMessage> {

  /**
   * Determine if the implementation handler supports the [RemoteStageMessage] type.
   */
  fun supports(message: Class<RemoteStageMessage>): Boolean

  /**
   * Handle the specific [RemoteStageMessage] type.
   */
  fun handle(message: M)
}
