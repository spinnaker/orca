package com.netflix.spinnaker.orca.remote.stage

import com.netflix.spinnaker.config.CallbackConfigurationProperties
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import org.springframework.stereotype.Component

@Component
class RemoteStageExecutionProvider(
  private val callbackConfigurationProperties: CallbackConfigurationProperties
) {

  fun get(stageExecution: StageExecution): RemoteStageExecution {
    val callback = Callback(
      http = Http(
        uri = callbackConfigurationProperties.http.uri,
        headers = mutableMapOf(Pair("X-SPINNAKER-EXECUTION-ID", stageExecution.execution.id))
      ),
      pubsub = Pubsub(
        name = callbackConfigurationProperties.pubsub.name,
        provider = callbackConfigurationProperties.pubsub.provider,
        providerConfig = callbackConfigurationProperties.pubsub.providerConfig,
        headers = mutableMapOf(Pair("X-SPINNAKER-EXECUTION-ID", stageExecution.execution.id))
      )
    )

    return RemoteStageExecution(
      stageExecution.type,
      stageExecution.id,
      stageExecution.execution.id,
      stageExecution.context,
      stageExecution.outputs,
      callback
    )
  }
}
