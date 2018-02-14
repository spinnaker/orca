package com.netflix.spinnaker.orca.locks;

/**
 * Manages acquisition / release of locks.
 *
 * The lock is held with a value which is the combination of lockValueApplication, lockValueType, and lockValue. This
 * would typically be the execution application, type (pipeline or orchestration) and execution id.
 *
 * A named lock can be acquired if either it is not currently locked, or if the current value of the
 * lock matches the supplied lockValue fields.
 *
 * A lock has a TTL to ensure that in the event of an unexpected failure or JVM exit that eventually locks
 * are released - the general expectation is that a call to acquireLock should be accompanied by a call to
 * releaseLock.
 *
 * Multiple holders can acquire the same lock by supplying the same lockValue fields, and a lock continues to be
 * held until all holders have issued a releaseLock.
 *
 * A stage that operates on a particular cluster should acquire the lock for that cluster, for example:
 *
 * Deploy with Red/Black would go through
 * createServerGroup
 *   acquireLock(clusterName, execution.id, createServerGroup.stage.id, ttl)
 *   deploy
 *   waitForUpInstances
 *
 *   disableCluster
 *     acquireLock(clusterName, execution.id, disableCluster.stage.id, ttl)
 *     disableCluster
 *     waitForDisableCluster
 *     releaseLock(clusterName, execution.id, disableCluster.stage.id)
 *
 *   scaleDownCluster
 *     acquireLock(clusterName, execution.id, scaleDownCluster.stage.id, ttl)
 *     scaleDownCluster
 *     waitForScaleDown
 *     releaseLock(clusterName, execution.id, scaleDownCluster.stage.id)
 *
 *   releaseLock(clusterName, execution.id, createServerGroup.stage.id)
 *
 *
 * The lock would be held by execution.id, so any stages within a particular execution could obtain a lock. The
 * lifespan of the lock being held would be the entire duration of the createServerGroup stage
 */
public interface LockManager {

  /**
   * Acquires a named lock.
   * @param lockName The name of the lock.
   * @param lockValueApplication The application for the value of the lock - if the lock is already held with this value, acquisition is successful otherwise lock acquisition fails.
   * @param lockValueType The type for the value of the lock - if the lock is already held with this value, acquisition is successful otherwise lock acquisition fails.
   * @param lockValue The value of the lock - if the lock is already held with this value, acquisition is successful otherwise lock acquisition fails.
   * @param lockHolder The holder of the lock.
   * @param ttlSeconds How long to acquire for or extend an existing lock for.
   * @throws LockFailureException if the lock is currently held with a different lockValueApplication/lockValueType/lockValue
   */
  void acquireLock(String lockName, String lockValueApplication, String lockValueType, String lockValue, String lockHolder, int ttlSeconds) throws LockFailureException;

  /**
   * Extends a named lock ttl.
   * @param lockName The name of the lock.
   * @param lockValueApplication The application for the value of the lock - if the lock is already held with this value, the ttl extension is successful otherwise lock extension fails.
   * @param lockValueType The type for the value of the lock - if the lock is already held with this value, the ttl extension is successful otherwise lock extension fails.
   * @param lockValue The value of the lock - if the lock is already held with this value, the ttl extension is successful otherwise lock extension fails.
   * @param ttlSeconds How long to extend the lock for.
   * @throws LockFailureException if the lock is currently held with a different lockValueApplication/lockValueType/lockValue
   */
  void extendLock(String lockName, String lockValueApplication, String lockValueType, String lockValue, int ttlSeconds) throws LockFailureException;

  /**
   * Releases a named lock for a specific lockHolder.
   *
   * After release, if there are no additional lock holders the lock itself is freed.
   *
   * @param lockName The name of the lock.
   * @param lockValueApplication The application for the value of the lock - An existing lock must be held with this value for release to succeed.
   * @param lockValueType The type for the value of the lock - An existing lock must be held with this value for release to succeed.
   * @param lockValue The value of the lock - An existing lock must be held with this value for release to succeed.
   * @param lockHolder The holder of the lock.
   */
  void releaseLock(String lockName, String lockValueApplication, String lockValueType, String lockValue, String lockHolder);
}
