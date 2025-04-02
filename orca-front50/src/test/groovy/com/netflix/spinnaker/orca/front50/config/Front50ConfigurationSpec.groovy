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
import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import okhttp3.OkHttpClient
import org.slf4j.Logger
import retrofit.Endpoint
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.lang.reflect.Method
import java.util.concurrent.TimeUnit

/**
 * Tests the timeout fallback chain logic in the Front50Configuration class.
 */
class Front50ConfigurationSpec extends Specification {

  OkHttpClientProvider clientProvider = Mock()
  RequestInterceptor requestInterceptor = Mock()
  Endpoint endpoint = Mock()
  
  @Subject
  Front50Configuration front50Configuration
  
  // Access private getEffectiveTimeout method via reflection
  private Method getEffectiveTimeoutMethod
  
  // Default timeout values from Front50Configuration
  private static final long DEFAULT_READ_TIMEOUT_MS = 60000    // 60 seconds
  private static final long DEFAULT_WRITE_TIMEOUT_MS = 60000   // 60 seconds
  private static final long DEFAULT_CONNECT_TIMEOUT_MS = 10000 // 10 seconds
  
  def setup() {
    front50Configuration = new Front50Configuration()
    front50Configuration.clientProvider = clientProvider
    front50Configuration.spinnakerRequestInterceptor = requestInterceptor
    
    // Use reflection to access the private getEffectiveTimeout method
    getEffectiveTimeoutMethod = Front50Configuration.class.getDeclaredMethod(
        "getEffectiveTimeout", 
        Integer.class, 
        long.class, 
        long.class, 
        String.class)
    getEffectiveTimeoutMethod.setAccessible(true)
  }

  @Unroll
  def "getEffectiveTimeout should use #description when explicitTimeout=#explicitTimeout, globalTimeout=#globalTimeout, timeoutType=#timeoutType"() {
    given:
    when:
    long result = getEffectiveTimeoutMethod.invoke(
        front50Configuration, 
        explicitTimeout, 
        globalTimeout, 
        defaultTimeout,
        timeoutType)
    
    then:
    result == expectedTimeout
    
    where:
    description               | explicitTimeout | globalTimeout | defaultTimeout | timeoutType || expectedTimeout
    "custom explicit timeout" | 30000           | 20000         | 60000          | "read"      || 30000
    "global timeout - read"   | 60000           | 20000         | 60000          | "read"      || 20000  // Default read timeout is ignored
    "global timeout - write"  | 60000           | 20000         | 60000          | "write"     || 20000  // Default write timeout is ignored
    "global timeout - connect"| 10000           | 20000         | 60000          | "connect"   || 20000  // Default connect timeout is ignored
    "default timeout"         | null            | 0             | 60000          | "read"      || 60000
    "zero explicit timeout"   | 0               | 20000         | 60000          | "read"      || 0       // Zero is treated as explicitly configured
  }
  
  @Unroll
  def "configureTimeouts should handle different combinations of configurations"() {
    given:
    Front50ConfigurationProperties props = new Front50ConfigurationProperties()
    if (explicitTimeoutsSet) {
      Front50ConfigurationProperties.OkHttpConfigurationProperties okHttpProps = 
          new Front50ConfigurationProperties.OkHttpConfigurationProperties()
      okHttpProps.setReadTimeoutMs(readTimeoutMs)
      okHttpProps.setWriteTimeoutMs(writeTimeoutMs)
      okHttpProps.setConnectTimeoutMs(connectTimeoutMs)
      props.setOkhttp(okHttpProps)
    }
    
    OkHttpClient baseClient = new OkHttpClient.Builder()
        .readTimeout(globalReadTimeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(globalWriteTimeoutMs, TimeUnit.MILLISECONDS)
        .connectTimeout(globalConnectTimeoutMs, TimeUnit.MILLISECONDS)
        .build()
    
    OkHttpClient configuredClient = new OkHttpClient.Builder().build()
    
    clientProvider.getClient(_ as DefaultServiceEndpoint) >> baseClient
    endpoint.getUrl() >> "http://front50.example.com"
    
    // Use reflection to access the private configureTimeouts method
    Method configureTimeoutsMethod = Front50Configuration.class.getDeclaredMethod(
        "configureTimeouts", 
        OkHttpClient.class, 
        Front50ConfigurationProperties.class)
    configureTimeoutsMethod.setAccessible(true)
    
    when:
    configuredClient = configureTimeoutsMethod.invoke(
        front50Configuration, 
        baseClient, 
        props) as OkHttpClient
    
    then:
    configuredClient.readTimeoutMillis() == expectedReadTimeoutMs
    configuredClient.writeTimeoutMillis() == expectedWriteTimeoutMs
    configuredClient.connectTimeoutMillis() == expectedConnectTimeoutMs
    
    where:
    explicitTimeoutsSet | readTimeoutMs | writeTimeoutMs | connectTimeoutMs | globalReadTimeoutMs | globalWriteTimeoutMs | globalConnectTimeoutMs || expectedReadTimeoutMs | expectedWriteTimeoutMs | expectedConnectTimeoutMs
    true                | 30000         | 35000          | 5000             | 20000               | 25000                | 15000                  || 30000                 | 35000                 | 5000
    true                | null          | 35000          | null             | 20000               | 25000                | 15000                  || 20000                 | 35000                 | 15000
    true                | null          | null           | null             | 20000               | 25000                | 15000                  || 20000                 | 25000                 | 15000
    false               | null          | null           | null             | 20000               | 25000                | 15000                  || 20000                 | 25000                 | 15000
    false               | null          | null           | null             | 0                   | 0                    | 0                      || DEFAULT_READ_TIMEOUT_MS | DEFAULT_WRITE_TIMEOUT_MS | DEFAULT_CONNECT_TIMEOUT_MS
  }
}
