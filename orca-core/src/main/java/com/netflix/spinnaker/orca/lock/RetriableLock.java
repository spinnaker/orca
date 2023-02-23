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
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import com.netflix.spinnaker.kork.lock.LockManager;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class RetriableLock {

  private final LockManager lockManager;
  private final RetrySupport retrySupport;

  public void execute(String lockName, Runnable action, int maxRetries, Duration interval) {
    var lockOptions =
        new LockManager.LockOptions()
            .withLockName(lockName)
            .withMaximumLockDuration(Duration.ofSeconds(2L));

    retrySupport.retry(
        () -> {
          log.debug("Attempt to acquire lock: {}", lockName);
          var response = lockManager.acquireLock(lockOptions, action);
          var lockAcquired = ACQUIRED.equals(response.getLockStatus());
          if (lockAcquired) {
            log.debug("Successfully acquired lock: {}", lockName);
          }
          // This exception is caught inside the retrySupport.retry method
          log.debug("Failed to acquired lock: {}", lockName);
          throw new FailedToGetLockException(lockName);
        },
        maxRetries,
        interval,
        false);
  }

  class FailedToGetLockException extends SpinnakerException {
    public FailedToGetLockException(String lockName) {
      super("Failed to acquire lock: " + lockName);
    }
  }
}
