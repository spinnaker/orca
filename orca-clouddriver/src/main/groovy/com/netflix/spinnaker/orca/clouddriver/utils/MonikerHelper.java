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
  public String getAppNameFromStage(Stage stage, String fallbackServerGroupName) {
    Names names = Names.parseName(fallbackServerGroupName);
    Moniker moniker = (Moniker) stage.getContext().get("moniker");
    String appName;
    if (moniker != null && moniker.getApp() != null) {
      appName = moniker.getApp();
    } else {
      appName = names.getApp();
    }
    return appName;
  }

  public String getClusterNameFromStage(Stage stage, String fallbackServerGroupName) {
    Names names = Names.parseName(fallbackServerGroupName);
    Moniker moniker = (Moniker) stage.getContext().get("moniker");
    String clusterName;
    if (moniker != null && moniker.getCluster() != null) {
      clusterName = moniker.getCluster();
    } else {
      clusterName = names.getCluster();
    }
    return clusterName;
  }
}
