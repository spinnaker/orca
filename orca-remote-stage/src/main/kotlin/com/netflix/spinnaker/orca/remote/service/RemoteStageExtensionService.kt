package com.netflix.spinnaker.orca.remote.service

import com.netflix.spinnaker.kork.plugins.remote.RemotePluginsProvider
import com.netflix.spinnaker.kork.plugins.remote.extension.RemoteExtension
import com.netflix.spinnaker.orca.remote.RemoteStageExtensionPointDefinition
import com.netflix.spinnaker.orca.remote.model.RemoteStageExtensionPointConfig

class RemoteStageExtensionService(
  private val remotePluginsProvider: RemotePluginsProvider,
  private val remoteStageExtensionDefinition: RemoteStageExtensionPointDefinition
) {

  fun getByStageType(stageType: String): RemoteExtension {
    val remoteExtensions = remotePluginsProvider
      .getExtensionsByType(remoteStageExtensionDefinition.type())
    val remoteStageExtensions: MutableList<RemoteExtension> = mutableListOf()

    remoteExtensions.forEach { extension ->
      val config = extension.getTypedConfig<RemoteStageExtensionPointConfig>()

      if (config.type == stageType) {
        remoteStageExtensions.add(extension)
      }
    }

    if (remoteStageExtensions.size > 1) throw DuplicateRemoteStageTypeException(stageType, remoteExtensions)
    return if (remoteStageExtensions.isNotEmpty()) remoteStageExtensions.first() else throw RemoteStageTypeNotFoundException(stageType)
  }
}
