/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.utils;

import com.netflix.frigga.NameBuilder;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.orca.pipeline.model.Stage;

import java.util.HashMap;
import java.util.Map;

public class ClusterNameValidator extends NameBuilder {

  /**
   * Map defining the length of the cluster name that is allowed for each cloud provider
   */
  Map<String, ClusterNameConstraint> cloudProviderClusterNameConstraints = new HashMap<String, ClusterNameConstraint>() {
    {
      put("aws", new ClusterNameConstraint(63));
    }
  };

  /**
   * Validate cluster name
   *
   * @param stage
   * @param cloudProvider
   * @return validation error string. If non-empty, signals validation failure the caller should exit.
   */
  public String validateClusterName(Stage stage, String cloudProvider) {
    Moniker moniker = MonikerHelper.monikerFromStage(stage);
    if (moniker == null
        || moniker.getApp() == null
        || moniker.getDetail() == null
        || moniker.getStack() == null
        || !cloudProviderClusterNameConstraints.containsKey(cloudProvider)) {
      return null;
    }
    String clusterName = combineAppStackDetail(moniker.getApp(), moniker.getStack(), moniker.getDetail());
    ClusterNameConstraint constraint = cloudProviderClusterNameConstraints.get(cloudProvider);
    if (clusterName.length() > constraint.getMaxLength()) {
      return String.format("Cluster name '%s' must be less than '%s' for '%s' cloud provider", clusterName, constraint.getMaxLength(), cloudProvider);
    } else {
      return null;
    }
  }
}
