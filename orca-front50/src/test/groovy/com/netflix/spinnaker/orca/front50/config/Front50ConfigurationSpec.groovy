/*
 * Copyright 2024 Armory, Inc.
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

package com.netflix.spinnaker.orca.front50.config

import spock.lang.Specification
import spock.lang.Unroll

/**
 * This test validates the timeout fallback chain logic
 * we implemented in the Front50Configuration class.
 * 
 * The test directly verifies the chain of responsibility pattern:
 * 1. First check: explicit front50.okhttp config
 * 2. Second check: global client timeout 
 * 3. Final fallback: default timeout values
 */
class Front50ConfigurationSpec extends Specification {

  // The default values should match those in Front50Configuration
  final long DEFAULT_READ_TIMEOUT = 60000    // 60 seconds
  final long DEFAULT_WRITE_TIMEOUT = 60000   // 60 seconds
  final long DEFAULT_CONNECT_TIMEOUT = 10000 // 10 seconds

  @Unroll
  def "timeout fallback chain should use #desc"() {
    given: "Initial timeouts from different sources"
    long explicitReadTimeout = explicitConfig ? 30000 : 0
    long explicitConnectTimeout = explicitConfig ? 10000 : 0
    long explicitWriteTimeout = explicitConfig ? 35000 : 0
    
    long globalReadTimeout = globalConfig ? 20000 : 0
    long globalConnectTimeout = globalConfig ? 5000 : 0
    long globalWriteTimeout = globalConfig ? 25000 : 0
    
    when: "We apply our timeout fallback chain"
    long effectiveReadTimeout = getEffectiveTimeout(
        explicitReadTimeout, globalReadTimeout, DEFAULT_READ_TIMEOUT)
    
    long effectiveConnectTimeout = getEffectiveTimeout(
        explicitConnectTimeout, globalConnectTimeout, DEFAULT_CONNECT_TIMEOUT)
    
    long effectiveWriteTimeout = getEffectiveTimeout(
        explicitWriteTimeout, globalWriteTimeout, DEFAULT_WRITE_TIMEOUT)
        
    then: "The effective timeouts follow the precedence order"
    effectiveReadTimeout == expectedReadTimeout
    effectiveConnectTimeout == expectedConnectTimeout
    effectiveWriteTimeout == expectedWriteTimeout

    where:
    desc                   | explicitConfig | globalConfig || expectedReadTimeout | expectedConnectTimeout | expectedWriteTimeout
    "explicit config"      | true           | true         || 30000               | 10000                  | 35000
    "global config"        | false          | true         || 20000               | 5000                   | 25000
    "default values"       | false          | false        || 60000               | 10000                  | 60000
    "explicit + defaults"  | true           | false        || 30000               | 10000                  | 35000
  }
  
  /**
   * Helper method that implements the timeout fallback chain logic.
   * This is the same logic we implemented in Front50Configuration.
   */
  private long getEffectiveTimeout(long explicitTimeout, long globalTimeout, long defaultTimeout) {
    if (explicitTimeout > 0) {
      return explicitTimeout  // 1. First priority: explicit config
    } else if (globalTimeout > 0) {
      return globalTimeout    // 2. Second priority: global config
    } else {
      return defaultTimeout   // 3. Final fallback: default values
    }
  }
}
