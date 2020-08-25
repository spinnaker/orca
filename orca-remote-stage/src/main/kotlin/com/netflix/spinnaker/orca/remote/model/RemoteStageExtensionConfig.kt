package com.netflix.spinnaker.orca.remote.model

import com.netflix.spinnaker.kork.plugins.remote.extension.RemoteExtensionConfigType

data class RemoteStageExtensionConfig(
  val type: String,
  val description: String,
  val label: String,
  val parameters: MutableMap<String, Any> = mutableMapOf()
) : RemoteExtensionConfigType
