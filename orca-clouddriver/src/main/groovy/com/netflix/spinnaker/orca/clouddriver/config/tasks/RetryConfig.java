/*
 * Copyright 2022 Salesforce.com, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.config.tasks;

import lombok.Data;

@Data
public class RetryConfig {
  // total number of attempts
  int maxAttempts = 6;

  // time in ms to wait before subsequent retry attempts
  long backOffInMs = 5000;

  // flag to enable exponential backoff
  boolean exponentialBackoffEnabled = false;
}
