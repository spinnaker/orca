package com.netflix.spinnaker.orca.locks;

public class LockFailureException extends RuntimeException {
  private final String lockName;
  private final String currentLockValueApplication;
  private final String currentLockValueType;
  private final String currentLockValue;

  public LockFailureException(String lockName, String currentLockValueApplication, String currentLockValueType, String currentLockValue) {
    super("Failed to acquire lock " + lockName + " currently held by /" + currentLockValueApplication + "/" + currentLockValueType + "/" + currentLockValue);
    this.lockName = lockName;
    this.currentLockValueApplication = currentLockValueApplication;
    this.currentLockValueType = currentLockValueType;
    this.currentLockValue = currentLockValue;
  }

  public String getLockName() {
    return lockName;
  }

  public String getCurrentLockValue() {
    return currentLockValue;
  }

  public String getCurrentLockValueApplication() {
    return currentLockValueApplication;
  }

  public String getCurrentLockValueType() {
    return currentLockValueType;
  }
}
