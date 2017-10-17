package com.netflix.spinnaker.orca.clouddriver.utils;

import com.netflix.spinnaker.orca.locks.LockContext;

public class LockNameHelper {
  public static final String CLUSTER_LOCK_TYPE = "cluster";

  public static String buildClusterLockName(String cloudProvider, String account, String cluster, String region) {
    return LockContext.buildLockName(CLUSTER_LOCK_TYPE, cloudProvider, account, cluster, region);
  }


  private LockNameHelper() {}
}
