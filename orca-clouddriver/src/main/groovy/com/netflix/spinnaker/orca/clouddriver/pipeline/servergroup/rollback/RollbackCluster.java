package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.rollback;

public class RollbackCluster {
  private final String cloudProvider;
  private final String credentials;
  private final String cluster;
  private final String region;

  RollbackCluster(String cloudProvider, String credentials, String cluster, String region) {
    this.cloudProvider = cloudProvider;
    this.credentials = credentials;
    this.cluster = cluster;
    this.region = region;
  }

  public String getCloudProvider() {
    return cloudProvider;
  }

  public String getCredentials() {
    return credentials;
  }

  public String getCluster() {
    return cluster;
  }

  public String getRegion() {
    return region;
  }
}
