package com.netflix.spinnaker.orca.remote.model

import com.netflix.spinnaker.kork.plugins.remote.extension.RemoteExtensionPointConfig

data class RemoteStageExtensionPointConfig(
  val type: String,
  val description: String,
  val label: String,
  val parameters: MutableMap<String, Any> = mutableMapOf()
) : RemoteExtensionPointConfig
