/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support

import com.netflix.frigga.Names
import com.netflix.spinnaker.orca.kato.pipeline.support.StageData
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.InheritConstructors
import groovy.transform.ToString
import groovy.util.logging.Slf4j

import javax.security.auth.callback.LanguageCallback

/**
 * A TargetServerGroup is a ServerGroup that is dynamically resolved using a target like "current" or "oldest".
 */
@ToString(includeNames = true)
class TargetServerGroup {
  // Delegates all Map interface calls to this object.
  @Delegate Map<String, Object> serverGroup = [:]

  /**
   * All invocations of this method should use the full 'getLocation()' signature, instead of the shorthand dot way
   * (i.e. "serverGroup.location"). Otherwise, the property 'location' is looked for in the serverGroup map, which is
   * very likely not there.
   */
  Location getLocation(Location.Type exactLocationType = null) {
    return Support.locationFromServerGroup(serverGroup, exactLocationType)
  }

  Map toClouddriverOperationPayload(String account) {
    //TODO(cfieber) - add an endpoint on Clouddriver to do provider appropriate conversion of a TargetServerGroup
    def op = [
      credentials: account,
      accountName: account,
      serverGroupName: serverGroup.name,
      asgName: serverGroup.name,
      cloudProvider: serverGroup.type,
      providerType: serverGroup.type
    ]

    def loc = getLocation()
    if (loc.type == Location.Type.REGION) {
      op.region = loc.value
    } else if (loc.type == Location.Type.ZONE) {
      op.zone = loc.value
    } else if (loc.type == Location.Type.NAMESPACE) {
      op.namespace = loc.value
    } else {
      throw new IllegalStateException("unsupported location type $loc.type")
    }
    return op
  }

  public static class Support {
    static Location resolveLocation(String cloudProvider, String zone, String namespace, String region) {
      if (cloudProvider == "gce" && zone) {
        return Location.zone(zone)
      } else if (namespace) {
        return Location.namespace(namespace)
      } else if (region) {
        return Location.region(region)
      } else {
        throw new IllegalArgumentException("No known location type provided. Must be `region`, `zone` or `namespace`.")
      }
     }

    static Location locationFromServerGroup(Map<String, Object> serverGroup, Location.Type exactLocationType) {
      switch (exactLocationType) {
        case (Location.Type.ZONE):
          return Location.zone(serverGroup.zone)
        case (Location.Type.NAMESPACE):
          return Location.namespace(serverGroup.namespace)
        case (Location.Type.REGION):
          return Location.region(serverGroup.region)
      }

      try {
        return resolveLocation(serverGroup.type, serverGroup.zone, serverGroup.namespace, serverGroup.region)
      } catch (e) {
        throw new IllegalArgumentException("Incorrect location specified for ${serverGroup.serverGroupName ?: serverGroup.name}: ${e.message}")
      }
    }

    static Location locationFromOperation(Map<String, Object> operation) {
      if (!operation.targetLocation) {
        return null
      }
      new Location(type: Location.Type.valueOf(operation.targetLocation.type), value: operation.targetLocation.value)
    }

    static Location locationFromStageData(StageData stageData) {
      try {
        List zones = stageData.availabilityZones?.values()?.flatten()?.toArray()
        return resolveLocation(stageData.cloudProvider, zones?.get(0), stageData.namespace, stageData.region)
      } catch (e) {
        throw new IllegalArgumentException("Incorrect location specified for ${stageData}: ${e.message}")
      }
    }
  }
  static boolean isDynamicallyBound(Stage stage) {
    Params.fromStage(stage).target?.isDynamic()
  }

  /**
   * A Params object is used to define the required parameters to resolve a TargetServerGroup.
   */
  @ToString(includeNames = true)
  @Slf4j
  static class Params {
    /**
     * These are all lower case because we expect them to be defined in the pipeline as lowercase.
     */
    enum Target {
      current_asg_dynamic,
      ancestor_asg_dynamic,
      oldest_asg_dynamic,
      @Deprecated current_asg,
      @Deprecated ancestor_asg,

      boolean isDynamic() {
        return this.name().endsWith("dynamic")
      }
    }

    // serverGroupName used when specifically targeting a server group
    // TODO(ttomsu): This feels dirty - consider structuring to enable an 'exact' Target that just specifies the exact
    // server group name to fetch?
    String serverGroupName

    // Alternatively to asgName, the combination of target and cluster can be used.
    Target target
    String cluster

    String credentials
    List<Location> locations
    String cloudProvider = "aws"

    String getApp() {
      Names.parseName(serverGroupName ?: cluster)?.app
    }

    String getCluster() {
      cluster ?: Names.parseName(serverGroupName)?.cluster
    }

    static Params fromStage(Stage stage) {
      Params p = stage.mapTo(Params)

      if (stage.context.region) {
        p.locations = [Location.region(stage.context.region)]
      } else if (stage.context.regions) {
        p.locations = stage.context.regions.collect { String r -> Location.region(r) }
      } else if (stage.context.namespace) {
        p.locations = [Location.namespace(stage.context.namespace)]
      } else if (stage.context.namespaces) {
        p.locations = stage.context.namespaces.collect { String n -> Location.namespace(n) }
      } else if (stage.context.cloudProvider == "gce" && stage.context.zones) {
        p.locations = stage.context.zones.collect { String z -> Location.zone(z) }
      } else {
        p.locations = []
      }
      p
    }
  }

  @InheritConstructors
  static class NotFoundException extends RuntimeException {}
}
