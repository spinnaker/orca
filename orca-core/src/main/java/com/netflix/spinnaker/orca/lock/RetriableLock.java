/*
 * Copyright 2023 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.lock;

import static com.netflix.spinnaker.kork.lock.LockManager.LockStatus.ACQUIRED;

import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.lock.LockManager;
import com.netflix.spinnaker.kork.lock.LockManager.LockOptions;
import java.time.Duration;
import java.util.function.Supplier;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class RetriableLock {

  private final LockManager lockManager;
  private final RetrySupport retrySupport;

  public Boolean lock(RetriableLockOptions rlOptions, Runnable action) {
    try {
      retrySupport.retry(
          new LockAndRun(rlOptions, action, lockManager),
          rlOptions.getMaxRetries(),
          rlOptions.getInterval(),
          rlOptions.isExponential());
      return true;

    } catch (FailedToGetLockException e) {
      log.error(
          "Tried {} times to acquire the lock {} and failed.",
          rlOptions.maxRetries,
          rlOptions.lockName);
      if (rlOptions.isThrowOnAcquireFailure()) {
        throw e;
      }
      return false;
    }
  }

  public static class FailedToGetLockException extends RuntimeException {
    public FailedToGetLockException(String lockName) {
      super("Failed to acquire lock: " + lockName);
    }
  }

  /***
   * Wrapper class to store retriable options prepopulated with reasonable default values,
   * Feel free to create constructor that overwrites those values
   */

  @Getter
  @AllArgsConstructor
  public static class RetriableLockOptions {
    private String lockName;
    private int maxRetries;
    private Duration interval;
    private boolean exponential;
    private boolean throwOnAcquireFailure;

    public RetriableLockOptions(String lockName) {
      this.lockName = lockName;
      this.maxRetries = 5;
      this.interval = Duration.ofMillis(500);
      this.exponential = false;
      this.throwOnAcquireFailure = false;
    }
  }

  /***
   *   Wrapper class for Supplier<Boolean> required by the RetrySupplier::retry method
   */
  @RequiredArgsConstructor
  private static final class LockAndRun implements Supplier<Boolean> {

    private static final Duration MAX_LOCK_DURATION = Duration.ofSeconds(2L);

    private final RetriableLockOptions options;
    private final Runnable action;
    private final LockManager lockManager;

    /***
     * Method tries to acquire lock via {@code lockManager} and execute an action once lock is acquired,
     * Throws {@code FailedToGetLockException} when failed to acquire lock in specified number of times,
     * It is up to client to handle the exception.
     *
     * @return true, when lock was successfully acquired
     * @throws FailedToGetLockException when failed to acquire lock in maxRetries times
     */
    @Override
    public Boolean get() {
      var options =
          new LockOptions()
              .withLockName(this.options.getLockName())
              .withMaximumLockDuration(MAX_LOCK_DURATION);

      var lockName = options.getLockName();
      log.debug("Attempt to acquire lock: {}", lockName);
      var response = lockManager.acquireLock(options, action);
      var lockAcquired = ACQUIRED.equals(response.getLockStatus());
      if (lockAcquired) {
        log.debug("Successfully acquired lock: {}", lockName);
        // The result of this method is nowhere used - we need it to satisfy RetrySupport contract
        return true;
      } else {
        // This exception is caught inside the retrySupport.retry method $maxRetries times.
        log.debug("Failed to acquired lock: {}", lockName);
        throw new FailedToGetLockException(lockName);
      }
    }
  }
}
