package com.netflix.spinnaker.orca.remote

import com.netflix.spinnaker.kork.annotations.Beta
import com.netflix.spinnaker.kork.plugins.remote.extension.RemoteExtensionDefinition
import com.netflix.spinnaker.orca.remote.model.RemoteStageExtensionConfig
import org.springframework.stereotype.Component

@Beta
@Component
class RemoteStageExtensionDefinition : RemoteExtensionDefinition {
  override fun type(): String = "stage"
  override fun configType(): Class<out RemoteStageExtensionConfig> = RemoteStageExtensionConfig::class.java
}
