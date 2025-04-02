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

package com.netflix.spinnaker.orca.front50.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.jakewharton.retrofit.Ok3Client
import com.netflix.spinnaker.config.DefaultServiceEndpoint
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerRetrofitErrorHandler
import com.netflix.spinnaker.orca.events.ExecutionEvent
import com.netflix.spinnaker.orca.events.ExecutionListenerAdapter
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.spring.DependentPipelineExecutionListener
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import com.netflix.spinnaker.orca.retrofit.logging.RetrofitSlf4jLog
import groovy.transform.CompileStatic
import okhttp3.OkHttpClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import retrofit.Endpoint
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.converter.JacksonConverter

import java.util.concurrent.TimeUnit

import static retrofit.Endpoints.newFixedEndpoint

@Configuration
@Import(RetrofitConfiguration)
@ComponentScan([
  "com.netflix.spinnaker.orca.front50.pipeline",
  "com.netflix.spinnaker.orca.front50.tasks",
  "com.netflix.spinnaker.orca.front50"
])
@EnableConfigurationProperties(Front50ConfigurationProperties)
@CompileStatic
@ConditionalOnExpression('${front50.enabled:true}')
class Front50Configuration {

  private static final Logger log = LoggerFactory.getLogger(Front50Configuration.class)
  
  // Default timeout values if no other configuration is provided
  private static final long DEFAULT_READ_TIMEOUT_MS = 60000    // 60 seconds
  private static final long DEFAULT_WRITE_TIMEOUT_MS = 60000   // 60 seconds
  private static final long DEFAULT_CONNECT_TIMEOUT_MS = 10000 // 10 seconds

  @Autowired
  OkHttpClientProvider clientProvider

  @Autowired
  RestAdapter.LogLevel retrofitLogLevel

  @Autowired
  RequestInterceptor spinnakerRequestInterceptor

  @Bean
  Endpoint front50Endpoint(Front50ConfigurationProperties front50ConfigurationProperties) {
    newFixedEndpoint(front50ConfigurationProperties.getBaseUrl())
  }

  @Bean
  Front50Service front50Service(Endpoint front50Endpoint, ObjectMapper mapper, Front50ConfigurationProperties front50ConfigurationProperties) {
    // Get base client with global configuration
    OkHttpClient baseClient = clientProvider.getClient(new DefaultServiceEndpoint("front50", front50Endpoint.getUrl()))
    
    // Configure client with appropriate timeouts
    OkHttpClient configuredClient = configureTimeouts(baseClient, front50ConfigurationProperties)
    
    // Create and return the service
    new RestAdapter.Builder()
      .setRequestInterceptor(spinnakerRequestInterceptor)
      .setEndpoint(front50Endpoint)
      .setClient(new Ok3Client(configuredClient))
      .setLogLevel(retrofitLogLevel)
      .setLog(new RetrofitSlf4jLog(Front50Service))
      .setConverter(new JacksonConverter(mapper))
      .setErrorHandler(SpinnakerRetrofitErrorHandler.getInstance())
      .build()
      .create(Front50Service)
  }
  
  /**
   * Configures an OkHttpClient with appropriate timeout settings using fallback chain:
   * 1. Use explicit Front50 timeouts if configured
   * 2. Fall back to global client timeouts
   * 3. Fall back to default timeout values
   */
  private OkHttpClient configureTimeouts(OkHttpClient baseClient, Front50ConfigurationProperties props) {
    OkHttpClient.Builder builder = baseClient.newBuilder()
    
    // Check if any explicit Front50 timeouts are configured
    boolean hasCustomTimeouts = props.okhttp?.hasCustomTimeouts()
    if (hasCustomTimeouts) {
      log.info("Using custom Front50 timeout configuration")
    }
    
    // Apply the timeouts following the fallback chain
    long readTimeout = getEffectiveTimeout(
        props.okhttp?.readTimeoutMs, 
        baseClient.readTimeoutMillis(), 
        DEFAULT_READ_TIMEOUT_MS,
        "read")
        
    long writeTimeout = getEffectiveTimeout(
        props.okhttp?.writeTimeoutMs, 
        baseClient.writeTimeoutMillis(), 
        DEFAULT_WRITE_TIMEOUT_MS,
        "write")
        
    long connectTimeout = getEffectiveTimeout(
        props.okhttp?.connectTimeoutMs, 
        baseClient.connectTimeoutMillis(), 
        DEFAULT_CONNECT_TIMEOUT_MS,
        "connect")
    
    // Apply effective timeouts to builder
    builder.readTimeout(readTimeout, TimeUnit.MILLISECONDS)
           .writeTimeout(writeTimeout, TimeUnit.MILLISECONDS)
           .connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
    
    return builder.build()
  }
  
  /**
   * Returns the effective timeout by following the fallback chain:
   * 1. Explicit config from Front50ConfigProperties
   * 2. Global client config 
   * 3. Default value
   */
  private long getEffectiveTimeout(Integer explicitTimeout, long globalTimeout, long defaultTimeout, String timeoutType) {
    if (explicitTimeout != null && explicitTimeout > 0) {
      log.debug("Using explicit Front50 {} timeout: {}ms", timeoutType, explicitTimeout)
      return explicitTimeout
    } else if (globalTimeout > 0) {
      log.debug("Using global {} timeout: {}ms", timeoutType, globalTimeout)
      return globalTimeout 
    } else {
      log.debug("Using default {} timeout: {}ms", timeoutType, defaultTimeout)
      return defaultTimeout
    }
  }

  @Bean
  ApplicationListener<ExecutionEvent> dependentPipelineExecutionListenerAdapter(DependentPipelineExecutionListener delegate, ExecutionRepository repository) {
    return new ExecutionListenerAdapter(delegate, repository)
  }
}
