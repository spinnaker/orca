/*
 * Copyright 2023 Netflix, Inc.
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

import static org.mockito.Mockito.*;

import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.lock.LockManager;
import com.netflix.spinnaker.kork.lock.LockManager.AcquireLockResponse;
import com.netflix.spinnaker.kork.lock.LockManager.LockStatus;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class RetriableLockTest {

  private static final String LOCK_NAME = UUID.randomUUID().toString();
  private LockManager lockManager;
  private RetriableLock retriableLock;

  @BeforeEach
  void setup() {
    this.lockManager = mock(LockManager.class);
    this.retriableLock = new RetriableLock(lockManager, new RetrySupport());
  }

  @Test
  @DisplayName("Should attempt to acquire lock as long as max retries is not exceeded")
  public void test1() {
    givenLockCannotBeAcquiredOnAnyAttempt();

    var maxRetries = 3;

    Assertions.assertThrows(
        RetriableLock.FailedToGetLockException.class,
        () -> retriableLock.execute(LOCK_NAME, () -> {}, maxRetries, Duration.ofMillis(100)));

    assertLockAcquireAttempts(maxRetries);
  }

  @Test
  @DisplayName("Should attempt to acquire lock only once, when the lock is available")
  void test2() {
    givenLockIsAcquired();

    retriableLock.execute(LOCK_NAME, () -> {}, 1, Duration.ofMillis(100));

    assertLockAcquireAttempts(1);
  }

  void givenLockCannotBeAcquiredOnAnyAttempt() {
    when(lockManager.acquireLock(ArgumentMatchers.any(), ArgumentMatchers.any(Runnable.class)))
        .thenReturn(getResponseWithLockStatus(LockStatus.TAKEN));
  }

  void givenLockIsAcquired() {
    when(lockManager.acquireLock(ArgumentMatchers.any(), ArgumentMatchers.any(Runnable.class)))
        .thenReturn(getResponseWithLockStatus(LockStatus.ACQUIRED));
  }

  void assertLockAcquireAttempts(int times) {
    verify(lockManager, times(times))
        .acquireLock(ArgumentMatchers.any(), ArgumentMatchers.any(Runnable.class));
  }

  AcquireLockResponse getResponseWithLockStatus(LockStatus status) {
    return new AcquireLockResponse<>(null, null, status, null, false);
  }
}
