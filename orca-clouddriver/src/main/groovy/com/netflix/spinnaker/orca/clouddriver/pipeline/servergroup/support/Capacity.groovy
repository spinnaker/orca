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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support

import groovy.transform.Canonical

import javax.annotation.Nonnegative

@Canonical
class Capacity {
  @Nonnegative
  int min

  @Nonnegative
  int max

  @Nonnegative
  int desired

  /**
   * @return true if the capacity of this server group is fixed, i.e min, max and desired are all the same
   */
  def Boolean isPinned() {
    return (max == desired) && (desired == min)
  }

  def Map<String, Integer> asMap() {
    return [min: min, max: max, desired: desired]
  }
}
