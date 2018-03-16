/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline;

import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.tasks.WaitTask;
import com.netflix.spinnaker.orca.pipeline.window.TimeWindowCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.netflix.spinnaker.orca.ExecutionStatus.*;
import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;

/**
 * A stage that suspends execution of pipeline if the current stage is restricted to run during a time window and
 * current time is within that window.
 */
@Component
public class RestrictExecutionDuringTimeWindow implements StageDefinitionBuilder {

  public static final String TYPE = "restrictExecutionDuringTimeWindow";

  @Override
  public void taskGraph(
    @Nonnull Stage stage, @Nonnull TaskNode.Builder builder) {
    builder.withTask("suspendExecutionDuringTimeWindow", SuspendExecutionDuringTimeWindowTask.class);

    try {
      JitterConfig jitter = stage.mapTo("/restrictedExecutionWindow/jitter", JitterConfig.class);
      if (jitter.enabled && jitter.maxDelay > 0) {
        if (jitter.skipManual && stage.getExecution().getTrigger().getType().equals("manual")) {
          return;
        }

        long waitTime = ThreadLocalRandom.current().nextLong(jitter.minDelay, jitter.maxDelay + 1);

        stage.setContext(contextWithWait(stage.getContext(), waitTime));
        builder.withTask("waitForJitter", WaitTask.class);
      }
    } catch (IllegalArgumentException e) {
      // Do nothing
    }
  }

  private Map<String, Object> contextWithWait(Map<String, Object> context, long waitTime) {
    context.putIfAbsent("waitTime", waitTime);
    return context;
  }

  private static class JitterConfig {
    private boolean enabled;
    private boolean skipManual;
    private long minDelay;
    private long maxDelay;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public boolean isSkipManual() {
      return skipManual;
    }

    public void setSkipManual(boolean skipManual) {
      this.skipManual = skipManual;
    }

    public long getMinDelay() {
      return minDelay;
    }

    public void setMinDelay(long minDelay) {
      this.minDelay = minDelay;
    }

    public long getMaxDelay() {
      return maxDelay;
    }

    public void setMaxDelay(long maxDelay) {
      this.maxDelay = maxDelay;
    }
  }

  @Component
  public static class SuspendExecutionDuringTimeWindowTask implements RetryableTask {

    @Autowired
    TimeWindowCalculator timeWindowCalculator;

    @Override public long getBackoffPeriod() {
      return TimeUnit.SECONDS.toMillis(30);
    }

    @Override public long getTimeout() {
      return TimeUnit.DAYS.toMillis(7);
    }

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override public @Nonnull TaskResult execute(@Nonnull Stage stage) {
      Instant now = Instant.now();
      Instant scheduledTime;
      try {
        scheduledTime = getTimeInWindowForStage(stage, now);
      } catch (Exception e) {
        return new TaskResult(TERMINAL, Collections.singletonMap("failureReason", "Exception occurred while calculating time window: " + e.getMessage()));
      }
      if (now.equals(scheduledTime) || now.isAfter(scheduledTime)) {
        return new TaskResult(SUCCEEDED);
      } else if (parseBoolean(stage.getContext().getOrDefault("skipRemainingWait", "false").toString())) {
        return new TaskResult(SUCCEEDED);
      } else {
        stage.setScheduledTime(scheduledTime.toEpochMilli());
        return new TaskResult(RUNNING);
      }
    }

    /**
     * Calculates a time which is within the whitelist of time windows allowed for execution
     *
     * @param stage
     * @param scheduledTime
     * @return the next available time
     */
    public Instant getTimeInWindowForStage(Stage stage, Instant scheduledTime) {
      ExecutionWindowConfig restrictedExecutionWindow = stage.mapTo(RestrictedExecutionWindowConfig.class).restrictedExecutionWindow;
      log.info("Calculating scheduled time for {}; {}", stage.getId(), restrictedExecutionWindow);
      return timeWindowCalculator.getTimeInWindow(restrictedExecutionWindow, scheduledTime);
    }

  }

  public static class RestrictedExecutionWindowConfig {
    private ExecutionWindowConfig restrictedExecutionWindow;

    public ExecutionWindowConfig getRestrictedExecutionWindow() {
      return restrictedExecutionWindow;
    }

    public void setRestrictedExecutionWindow(ExecutionWindowConfig restrictedExecutionWindow) {
      this.restrictedExecutionWindow = restrictedExecutionWindow;
    }
  }

  public static class TimeWindowConfig {
    private int startHour;
    private int startMin;
    private int endHour;
    private int endMin;

    public int getStartHour() {
      return startHour;
    }

    public void setStartHour(int startHour) {
      this.startHour = startHour;
    }

    public int getStartMin() {
      return startMin;
    }

    public void setStartMin(int startMin) {
      this.startMin = startMin;
    }

    public int getEndHour() {
      return endHour;
    }

    public void setEndHour(int endHour) {
      this.endHour = endHour;
    }

    public int getEndMin() {
      return endMin;
    }

    public void setEndMin(int endMin) {
      this.endMin = endMin;
    }

    @Override public String toString() {
      return format("[ start: %d:%s, end: %d:%d ]", startHour, startMin, endHour, endMin);
    }
  }

  public static class TimeWindow implements Comparable {
    public final LocalTime start;
    public final LocalTime end;

    public TimeWindow(LocalTime start, LocalTime end) {
      this.start = start;
      this.end = end;
    }

    @Override public int compareTo(Object o) {
      TimeWindow rhs = (TimeWindow) o;
      return this.start.compareTo(rhs.start);
    }

    public int indexOf(LocalTime current) {
      if (current.isBefore(this.start)) {
        return -1;
      } else if ((current.isAfter(this.start) || current.equals(this.start)) && (current.isBefore(this.end) || current.equals(this.end))) {
        return 0;
      } else {
        return 1;
      }
    }

    LocalTime getStart() {
      return start;
    }

    LocalTime getEnd() {
      return end;
    }

    private static
    final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    @Override public String toString() {
      return format("{%s to %s}", start.format(FORMAT), end.format(FORMAT));
    }
  }

  public static class ExecutionWindowConfig {
    private List<TimeWindowConfig> whitelist = new ArrayList<>();
    private List<Integer> days = new ArrayList<>();

    public List<TimeWindowConfig> getWhitelist() {
      return whitelist;
    }

    public void setWhitelist(List<TimeWindowConfig> whitelist) {
      this.whitelist = whitelist;
    }

    public List<Integer> getDays() {
      return days;
    }

    public void setDays(List<Integer> days) {
      this.days = days;
    }

    @Override public String toString() {
      return format("[ whitelist: %s, days: %s ]", whitelist, days);
    }
  }

}
