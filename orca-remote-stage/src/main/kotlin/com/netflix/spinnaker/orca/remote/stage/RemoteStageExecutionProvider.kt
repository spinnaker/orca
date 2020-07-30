package com.netflix.spinnaker.orca.remote.stage

import com.netflix.spinnaker.config.CallbackConfigurationProperties
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.remote.execution.RemoteStageCallback
import com.netflix.spinnaker.orca.api.pipeline.remote.execution.RemoteStageExecution
import org.springframework.stereotype.Component

@Component
class RemoteStageExecutionProvider(
  private val callbackConfigurationProperties: CallbackConfigurationProperties
) {

  fun get(stageExecution: StageExecution): RemoteStageExecution {
    val callback = RemoteStageCallback.builder()
      .http(RemoteStageCallback.Http.builder()
        .url(callbackConfigurationProperties.http.url)
        .headers(mapOf(Pair("X-SPINNAKER-EXECUTION-ID", stageExecution.execution.id)))
        .build())
      .pubsub(RemoteStageCallback.Pubsub.builder()
        .id(callbackConfigurationProperties.pubsub.id)
        .provider(callbackConfigurationProperties.pubsub.provider)
        .providerConfig(callbackConfigurationProperties.pubsub.providerConfig)
        .headers(mapOf(Pair("X-SPINNAKER-EXECUTION-ID", stageExecution.execution.id)))
        .build())
      .build()

    return RemoteStageExecution.builder()
      .type(stageExecution.type)
      .id(stageExecution.id)
      .pipelineExecutionId(stageExecution.execution.id)
      .context(stageExecution.context)
      .upstreamStageOutputs(stageExecution.ancestors().map { Pair(it.refId, it.outputs) }.toMap())
      .callback(callback)
      .build()
  }
}
