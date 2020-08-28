package com.netflix.spinnaker.orca.remote.model

import com.netflix.spinnaker.kork.plugins.remote.extension.RemoteExtensionPayload

data class RemoteStageExtensionPayload(
  val type: String,
  val id: String,
  val pipelineExecutionId: String,
  val context: MutableMap<String, Any?>
) : RemoteExtensionPayload
