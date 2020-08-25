package com.netflix.spinnaker.orca.remote.service

import com.netflix.spinnaker.kork.exceptions.IntegrationException
import com.netflix.spinnaker.kork.exceptions.SystemException

class RemoteStageNotFoundException : IntegrationException(
  "No remote stage found.  Have you published a plugin with a remote extension stage configuration?"
)

class RemoteStageTypeNotFoundException(stageType: String) : IntegrationException(
  "Remote stage type $stageType not found.  Check if stage type exists in plugin info configuration."
)

class DuplicateRemoteStageTypeException(stageType: String, pluginIdA: String, pluginIdB: String) : SystemException(
  "Duplicate stage type $stageType found.  Multiple plugins define the same stage type: $pluginIdA and $pluginIdB"
)
