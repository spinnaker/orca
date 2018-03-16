/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.pipeline.window;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.spinnaker.orca.pipeline.RestrictExecutionDuringTimeWindow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalTime;
import java.util.*;

import static java.util.Calendar.*;
import static java.util.Calendar.SECOND;
import static java.util.Collections.singletonList;

@Component
public class TimeWindowCalculator {

  private static final int DAY_START_HOUR = 0;
  private static final int DAY_START_MIN = 0;
  private static final int DAY_END_HOUR = 23;
  private static final int DAY_END_MIN = 59;

  @Value("${tasks.executionWindow.timezone:America/Los_Angeles}")
  private String timeZoneId;

  public Instant getTimeInWindow(RestrictExecutionDuringTimeWindow.ExecutionWindowConfig restrictedExecutionWindow, Instant scheduledTime) {
    // Passing in the current date to allow unit testing
    try {
      List<RestrictExecutionDuringTimeWindow.TimeWindow> whitelistWindows = new ArrayList<>();
      for (RestrictExecutionDuringTimeWindow.TimeWindowConfig timeWindow : restrictedExecutionWindow.getWhitelist()) {
        LocalTime start = LocalTime.of(timeWindow.getStartHour(), timeWindow.getStartMin());
        LocalTime end = LocalTime.of(timeWindow.getEndHour(), timeWindow.getEndMin());

        whitelistWindows.add(new RestrictExecutionDuringTimeWindow.TimeWindow(start, end));
      }
      return calculateScheduledTime(scheduledTime, whitelistWindows, restrictedExecutionWindow.getDays());

    } catch (IncorrectTimeWindowsException ite) {
      throw new RuntimeException("Incorrect time windows specified", ite);
    }
  }

  @VisibleForTesting
  private Instant calculateScheduledTime(Instant scheduledTime, List<RestrictExecutionDuringTimeWindow.TimeWindow> whitelistWindows, List<Integer> whitelistDays) throws IncorrectTimeWindowsException {
    return calculateScheduledTime(scheduledTime, whitelistWindows, whitelistDays, false);
  }

  private Instant calculateScheduledTime(Instant scheduledTime, List<RestrictExecutionDuringTimeWindow.TimeWindow> whitelistWindows, List<Integer> whitelistDays, boolean dayIncremented) throws IncorrectTimeWindowsException {
    if ((whitelistWindows.isEmpty()) && !whitelistDays.isEmpty()) {
      whitelistWindows = singletonList(new RestrictExecutionDuringTimeWindow.TimeWindow(LocalTime.of(0, 0), LocalTime.of(23, 59)));
    }

    boolean inWindow = false;
    Collections.sort(whitelistWindows);
    List<RestrictExecutionDuringTimeWindow.TimeWindow> normalized = normalizeTimeWindows(whitelistWindows);
    Calendar calendar = Calendar.getInstance();
    calendar.setTimeZone(TimeZone.getTimeZone(timeZoneId));
    calendar.setTime(new Date(scheduledTime.toEpochMilli()));
    boolean todayIsValid = true;

    if (!whitelistDays.isEmpty()) {
      int daysIncremented = 0;
      while (daysIncremented < 7) {
        boolean nextDayFound = false;
        if (whitelistDays.contains(calendar.get(DAY_OF_WEEK))) {
          nextDayFound = true;
          todayIsValid = daysIncremented == 0;
        }
        if (nextDayFound) {
          break;
        }
        calendar.add(DAY_OF_MONTH, 1);
        resetToTomorrow(calendar);
        daysIncremented++;
      }
    }
    if (todayIsValid) {
      for (RestrictExecutionDuringTimeWindow.TimeWindow timeWindow : normalized) {
        LocalTime hourMin = LocalTime.of(calendar.get(HOUR_OF_DAY), calendar.get(MINUTE));
        int index = timeWindow.indexOf(hourMin);
        if (index == -1) {
          calendar.set(HOUR_OF_DAY, timeWindow.start.getHour());
          calendar.set(MINUTE, timeWindow.start.getMinute());
          calendar.set(SECOND, 0);
          inWindow = true;
          break;
        } else if (index == 0) {
          inWindow = true;
          break;
        }
      }
    }

    if (!inWindow) {
      if (!dayIncremented) {
        resetToTomorrow(calendar);
        return calculateScheduledTime(calendar.getTime().toInstant(), whitelistWindows, whitelistDays, true);
      } else {
        throw new IncorrectTimeWindowsException("Couldn't calculate a suitable time within given time windows");
      }
    }

    if (dayIncremented) {
      calendar.add(DAY_OF_MONTH, 1);
    }
    return calendar.getTime().toInstant();
  }

  private static void resetToTomorrow(Calendar calendar) {
    calendar.set(HOUR_OF_DAY, DAY_START_HOUR);
    calendar.set(MINUTE, DAY_START_MIN);
    calendar.set(SECOND, 0);
  }

  private static List<RestrictExecutionDuringTimeWindow.TimeWindow> normalizeTimeWindows(List<RestrictExecutionDuringTimeWindow.TimeWindow> timeWindows) {
    List<RestrictExecutionDuringTimeWindow.TimeWindow> normalized = new ArrayList<>();
    for (RestrictExecutionDuringTimeWindow.TimeWindow timeWindow : timeWindows) {
      int startHour = timeWindow.start.getHour();
      int startMin = timeWindow.start.getMinute();
      int endHour = timeWindow.end.getHour();
      int endMin = timeWindow.end.getMinute();

      if (startHour > endHour) {
        LocalTime start1 = LocalTime.of(startHour, startMin);
        LocalTime end1 = LocalTime.of(DAY_END_HOUR, DAY_END_MIN);
        normalized.add(new RestrictExecutionDuringTimeWindow.TimeWindow(start1, end1));

        LocalTime start2 = LocalTime.of(DAY_START_HOUR, DAY_START_MIN);
        LocalTime end2 = LocalTime.of(endHour, endMin);
        normalized.add(new RestrictExecutionDuringTimeWindow.TimeWindow(start2, end2));

      } else {
        LocalTime start = LocalTime.of(startHour, startMin);
        LocalTime end = LocalTime.of(endHour, endMin);
        normalized.add(new RestrictExecutionDuringTimeWindow.TimeWindow(start, end));
      }
    }
    Collections.sort(normalized);
    return normalized;
  }

  public static class IncorrectTimeWindowsException extends Exception {
    public IncorrectTimeWindowsException(String message) {
      super(message);
    }
  }

}
