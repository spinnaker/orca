/*
 * Copyright 2017 Armory, Inc.
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
package com.netflix.spinnaker.orca.clouddriver.utils;


import com.netflix.frigga.Names;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.stereotype.Component;


/**
 * Helper methods for getting the app, cluster, etc from a moniker. When a moniker is not available use frigga.
 */
@Component
public class MonikerHelper {
  public String getAppNameFromStage(Stage stage, String fallbackFriggaName) {
    Names names = Names.parseName(fallbackFriggaName);
    Moniker moniker = monikerFromStage(stage);
    String appName;
    if (moniker != null && moniker.getApp() != null) {
      appName = moniker.getApp();
    } else {
      appName = names.getApp();
    }
    return appName;
  }

  public String getClusterNameFromStage(Stage stage, String fallbackFriggaName) {
    Names names = Names.parseName(fallbackFriggaName);
    Moniker moniker = monikerFromStage(stage);
    String clusterName;
    if (moniker != null && moniker.getCluster() != null) {
      clusterName = moniker.getCluster();
    } else {
      clusterName = names.getCluster();
    }
    return clusterName;
  }

  static public Moniker monikerFromStage(Stage stage) {
    if (stage.getContext().containsKey("moniker")) {
      return (Moniker) stage.mapTo("/moniker", Moniker.class);
    } else {
      return null;
    }
  }
}
