/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.pipeline.tasks;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING;
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED;
import static java.util.Collections.singletonMap;

@Component
public class WaitTask implements RetryableTask {

  private final Clock clock;

  @Autowired
  public WaitTask(Clock clock) {this.clock = clock;}

  @Override
  public @Nonnull TaskResult execute(@Nonnull Stage stage) {
    WaitStageContext context = stage.mapTo(WaitStageContext.class);

    if (context.getWaitTime() == null) {
      return new TaskResult(SUCCEEDED);
    }

    Instant now = clock.instant();

    if (context.isSkipRemainingWait()) {
      return new TaskResult(SUCCEEDED);
    } else if (context.getStartTime() == null) {
      return new TaskResult(RUNNING, singletonMap("startTime", now));
    } else if (context.getStartTime().plus(context.getWaitDuration()).isBefore(now)) {
      return new TaskResult(SUCCEEDED);
    } else {
      return new TaskResult(RUNNING);
    }
  }

  @Override public long getBackoffPeriod() {
    return 15_000;
  }

  @Override public long getTimeout() {
    return Integer.MAX_VALUE;
  }

  private static final class WaitStageContext {
    private final Long waitTime;
    private final boolean skipRemainingWait;
    private final Instant startTime;

    @JsonCreator
    private WaitStageContext(
      @JsonProperty("waitTime") @Nullable Long waitTime,
      @JsonProperty("skipRemainingWait") @Nullable Boolean skipRemainingWait,
      @JsonProperty("startTime") @Nullable Instant startTime
    ) {
      this.waitTime = waitTime;
      this.skipRemainingWait = skipRemainingWait == null ? false : skipRemainingWait;
      this.startTime = startTime;
    }

    @Nullable Long getWaitTime() {
      return waitTime;
    }

    @JsonIgnore
    @Nullable Duration getWaitDuration() {
      return waitTime == null ? null : Duration.ofSeconds(waitTime);
    }

    boolean isSkipRemainingWait() {
      return skipRemainingWait;
    }

    @Nullable Instant getStartTime() {
      return startTime;
    }
  }
}
