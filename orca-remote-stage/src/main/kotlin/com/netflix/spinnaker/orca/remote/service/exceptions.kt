package com.netflix.spinnaker.orca.remote.service

import com.netflix.spinnaker.kork.exceptions.IntegrationException
import com.netflix.spinnaker.kork.exceptions.SystemException
import com.netflix.spinnaker.kork.plugins.remote.extension.RemoteExtension

class RemoteStageTypeNotFoundException(stageType: String) : IntegrationException(
  "Remote stage type $stageType not found.  Check if stage type exists in plugin info configuration."
)

class DuplicateRemoteStageTypeException(stageType: String, remoteExtensions: List<RemoteExtension>) : SystemException(
  "Duplicate stage type $stageType found.  Multiple plugins define the same stage type: ${remoteExtensions.map { it.pluginId }}"
)
