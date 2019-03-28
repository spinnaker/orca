package com.netflix.spinnaker.orca.clouddriver.exception;

public class PreconfiguredJobNotFoundException extends RuntimeException {
  public PreconfiguredJobNotFoundException(String jobKey) {
    super("Could not find a stage named \'" + jobKey + "\'");
  }
}
