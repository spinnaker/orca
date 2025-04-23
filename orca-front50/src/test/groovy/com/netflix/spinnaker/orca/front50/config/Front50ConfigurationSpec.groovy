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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties
import retrofit.Endpoint
import spock.lang.Specification
import spock.lang.Subject
import java.util.concurrent.TimeUnit

/**
 * Tests for Front50 timeout configuration
 */
class Front50ConfigurationSpec extends Specification {

  @Subject
  Front50Configuration front50Configuration = new Front50Configuration()

  /**
   * Verifies that the default timeout values in Front50ConfigurationProperties match
   * those in OkHttpClientConfigurationProperties to ensure backward compatibility
   */
  def "default timeout values should match OkHttpClientConfigurationProperties"() {
    given:
    OkHttpClientConfigurationProperties globalProps = new OkHttpClientConfigurationProperties()
    Front50ConfigurationProperties.OkHttpConfigurationProperties front50Props = 
      new Front50ConfigurationProperties.OkHttpConfigurationProperties()
    
    expect:
    front50Props.connectTimeoutMs == globalProps.connectTimeoutMs
    front50Props.readTimeoutMs == globalProps.readTimeoutMs
  }
  
  /**
   * Verifies that the front50Service method accepts OkHttpClientConfigurationProperties
   * as a parameter, which is necessary for the timeout fallback mechanism to work
   */
  def "front50Service method should accept OkHttpClientConfigurationProperties parameter"() {
    given:
    def method = Front50Configuration.class.getDeclaredMethod(
        "front50Service", 
        Endpoint.class, 
        ObjectMapper.class, 
        Front50ConfigurationProperties.class,
        OkHttpClientConfigurationProperties.class)
    
    expect:
    method != null
  }
  
  /**
   * Verifies that hasCustomTimeouts correctly identifies when custom timeouts exist
   */
  def "hasCustomTimeouts should return #expected when #description"() {
    given:
    def props = new Front50ConfigurationProperties.OkHttpConfigurationProperties()
    if (connectTimeout != null) {
      props.connectTimeoutMs = connectTimeout
    }
    if (readTimeout != null) {
      props.readTimeoutMs = readTimeout
    }
    
    expect:
    props.hasCustomTimeouts() == expected
    
    where:
    description                     | connectTimeout | readTimeout || expected
    "using default values"          | 5000L          | 120000L     || false
    "only connect timeout changed"  | 10000L         | 120000L     || true
    "only read timeout changed"     | 5000L          | 30000L      || true
    "both timeouts changed"         | 10000L         | 30000L      || true
  }
  
  /**
   * This test examines the source code of Front50Configuration to verify that
   * the timeout fallback pattern is correctly implemented. This method was chosen
   * because OkHttpClient.Builder is a final class and cannot be easily mocked.
   */
  def "front50Service uses the correct timeout fallback pattern"() {
    given:
    def sourceFile = new File("/Users/shlomodaari/armory/spinnaker-oss-services/orca/orca-front50/src/main/groovy/com/netflix/spinnaker/orca/front50/config/Front50Configuration.groovy")
    def sourceCode = sourceFile.exists() ? sourceFile.text : null
    
    expect:
    sourceCode != null
    
    // First apply global timeouts
    sourceCode.contains("builder.connectTimeout(okHttpClientConfigurationProperties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)")
    sourceCode.contains("builder.readTimeout(okHttpClientConfigurationProperties.getReadTimeoutMs(), TimeUnit.MILLISECONDS)")
    
    // Then conditionally override with Front50-specific timeouts if defined
    sourceCode.contains("if (front50ConfigurationProperties.okhttp?.connectTimeoutMs != null) {")
    sourceCode.contains("builder.connectTimeout(front50ConfigurationProperties.okhttp.connectTimeoutMs, TimeUnit.MILLISECONDS)")
    sourceCode.contains("if (front50ConfigurationProperties.okhttp?.readTimeoutMs != null) {")
    sourceCode.contains("builder.readTimeout(front50ConfigurationProperties.okhttp.readTimeoutMs, TimeUnit.MILLISECONDS)")
  }
}
