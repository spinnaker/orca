package com.netflix.spinnaker.orca.remote.service

import com.netflix.spinnaker.kork.plugins.remote.RemotePluginsProvider
import com.netflix.spinnaker.kork.plugins.remote.extension.RemoteExtension
import com.netflix.spinnaker.orca.remote.RemoteStageExtensionDefinition
import com.netflix.spinnaker.orca.remote.model.RemoteStageExtensionConfig

class RemoteStageExtensionService(
  private val remotePluginsProvider: RemotePluginsProvider,
  private val remoteStageExtensionDefinition: RemoteStageExtensionDefinition
) {

  fun getByStageType(stageType: String): RemoteExtension {
    val remoteExtensions = remotePluginsProvider
      .getExtensionsByType(remoteStageExtensionDefinition.type())
    var remoteStageExtension: RemoteExtension? = null

    val extension = remoteExtensions
      .find { it.type == remoteStageExtensionDefinition.type() }
      ?: throw RemoteStageNotFoundException()

    val config = extension.getTypedConfig<RemoteStageExtensionConfig>()

    if (remoteStageExtension == null && config.type == stageType) {
      remoteStageExtension = extension
    }

    if (remoteStageExtension != null && config.type == stageType) {
      throw DuplicateRemoteStageTypeException(stageType, extension.id, remoteStageExtension.id)
    }

    return remoteStageExtension ?: throw RemoteStageTypeNotFoundException(stageType)
  }
}
