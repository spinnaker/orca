/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.model;

// adapted from clouddriver's com.netflix.spinnaker.clouddriver.model.HealthState
public enum HealthState {
  Failed,
  Down,
  OutOfService,
  Unknown,
  Starting,
  Succeeded,
  Up;

  // HealthState.valueOf does:
  // - a case-sensitive match
  // - and throws an IllegalArgumentException if there is no match
  // This does:
  // - a case-insensitive match
  // - and returns Unknown if there is no match
  // Note that maybe we should be careful around that since Unknown essentially means
  // "healthy" for some providers like Amazon)
  public static HealthState fromString(String name) {
    for (HealthState state : values()) {
      if (state.name().equalsIgnoreCase(name)) {
        return state;
      }
    }

    return Unknown;
  }
}
