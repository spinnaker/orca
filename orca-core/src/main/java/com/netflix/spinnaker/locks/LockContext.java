package com.netflix.spinnaker.orca.locks;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class LockContext {
  public static String buildLockName(String type, String... scope) {
    return type + ":" + String.join("/", scope);
  }

  public static final int DEFAULT_TTL = (int) TimeUnit.MINUTES.toSeconds(1);
  private final String lockName;
  private final String lockValueApplication;
  private final String lockValueType;
  private final String lockValue;
  private final int ttl;
  private final String lockHolder;

  public LockContext(String lockName, String lockValueApplication, String lockValueType, String lockValue) {
    this(lockName, lockValueApplication, lockValueType, lockValue, DEFAULT_TTL);
  }

  public LockContext(String lockName, String lockValueApplication, String lockValueType, String lockValue, int ttl) {
    this(lockName, lockValueApplication, lockValueType, lockValue, ttl, null);
  }

  public LockContext(String lockName, String lockValueApplication, String lockValueType, String lockValue, String lockHolder) {
    this(lockName, lockValueApplication, lockValueType, lockValue, DEFAULT_TTL, lockHolder);
  }

  @JsonCreator
  public LockContext(
    @JsonProperty("lockName") String lockName,
    @JsonProperty("lockValueApplication") String lockValueApplication,
    @JsonProperty("lockValueType") String lockValueType,
    @JsonProperty("lockValue") String lockValue,
    @JsonProperty("ttl") int ttl,
    @JsonProperty("lockHolder") String lockHolder) {
    this.lockName = Objects.requireNonNull(lockName);
    this.lockValueApplication = Objects.requireNonNull(lockValueApplication);
    this.lockValueType = Objects.requireNonNull(lockValueType);
    this.lockValue = Objects.requireNonNull(lockValue);
    this.ttl = ttl;
    this.lockHolder = lockHolder;
  }

  public String getLockName() {
    return lockName;
  }

  public String getLockValueApplication() {
    return lockValueApplication;
  }

  public String getLockValueType() {
    return lockValueType;
  }

  public String getLockValue() {
    return lockValue;
  }

  public int getTtl() {
    return ttl;
  }

  public Optional<String> getLockHolder() {
    return Optional.ofNullable(lockHolder);
  }
}
