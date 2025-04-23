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

package com.netflix.spinnaker.orca.front50.config;

import static retrofit.Endpoints.newFixedEndpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jakewharton.retrofit.Ok3Client;
import com.netflix.spinnaker.config.DefaultServiceEndpoint;
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerRetrofitErrorHandler;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import com.netflix.spinnaker.orca.events.ExecutionEvent;
import com.netflix.spinnaker.orca.events.ExecutionListenerAdapter;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.front50.spring.DependentPipelineExecutionListener;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration;
import com.netflix.spinnaker.orca.retrofit.logging.RetrofitSlf4jLog;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import retrofit.Endpoint;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.converter.JacksonConverter;

@Configuration
@Import(RetrofitConfiguration.class)
@ComponentScan({
  "com.netflix.spinnaker.orca.front50.pipeline",
  "com.netflix.spinnaker.orca.front50.tasks",
  "com.netflix.spinnaker.orca.front50"
})
@EnableConfigurationProperties(Front50ConfigurationProperties.class)
@ConditionalOnExpression("${front50.enabled:true}")
public class Front50Configuration {

  private static final Logger log = LoggerFactory.getLogger(Front50Configuration.class);

  @Autowired private OkHttpClientProvider clientProvider;

  @Autowired private RestAdapter.LogLevel retrofitLogLevel;

  @Autowired private RequestInterceptor spinnakerRequestInterceptor;

  @Bean
  public Endpoint front50Endpoint(Front50ConfigurationProperties front50ConfigurationProperties) {
    return newFixedEndpoint(front50ConfigurationProperties.getBaseUrl());
  }

  @Bean
  public Front50Service front50Service(
      Endpoint front50Endpoint,
      ObjectMapper mapper,
      Front50ConfigurationProperties front50ConfigurationProperties,
      OkHttpClientConfigurationProperties okHttpClientConfigurationProperties) {

    // Get base client with global configuration
    OkHttpClient baseClient =
        clientProvider.getClient(new DefaultServiceEndpoint("front50", front50Endpoint.getUrl()));
    OkHttpClient.Builder builder = baseClient.newBuilder();

    // Apply global timeouts first
    builder.connectTimeout(
        okHttpClientConfigurationProperties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS);
    builder.readTimeout(
        okHttpClientConfigurationProperties.getReadTimeoutMs(), TimeUnit.MILLISECONDS);

    // Override with Front50-specific timeouts if explicitly defined
    if (front50ConfigurationProperties.getOkhttp() != null
        && front50ConfigurationProperties.getOkhttp().getConnectTimeoutMs() != null) {
      log.debug(
          "Using front50-specific connect timeout: {}ms",
          front50ConfigurationProperties.getOkhttp().getConnectTimeoutMs());
      builder.connectTimeout(
          front50ConfigurationProperties.getOkhttp().getConnectTimeoutMs(), TimeUnit.MILLISECONDS);
    }

    if (front50ConfigurationProperties.getOkhttp() != null
        && front50ConfigurationProperties.getOkhttp().getReadTimeoutMs() != null) {
      log.debug(
          "Using front50-specific read timeout: {}ms",
          front50ConfigurationProperties.getOkhttp().getReadTimeoutMs());
      builder.readTimeout(
          front50ConfigurationProperties.getOkhttp().getReadTimeoutMs(), TimeUnit.MILLISECONDS);
    }

    if (front50ConfigurationProperties.getOkhttp() != null
        && front50ConfigurationProperties.getOkhttp().getWriteTimeoutMs() != null) {
      log.debug(
          "Using front50-specific write timeout: {}ms",
          front50ConfigurationProperties.getOkhttp().getWriteTimeoutMs());
      builder.writeTimeout(
          front50ConfigurationProperties.getOkhttp().getWriteTimeoutMs(), TimeUnit.MILLISECONDS);
    }

    // Create and return the service
    return new RestAdapter.Builder()
        .setRequestInterceptor(spinnakerRequestInterceptor)
        .setEndpoint(front50Endpoint)
        .setClient(new Ok3Client(builder.build()))
        .setLogLevel(retrofitLogLevel)
        .setLog(new RetrofitSlf4jLog(Front50Service.class))
        .setConverter(new JacksonConverter(mapper))
        .setErrorHandler(SpinnakerRetrofitErrorHandler.getInstance())
        .build()
        .create(Front50Service.class);
  }

  @Bean
  public ApplicationListener<ExecutionEvent> dependentPipelineExecutionListenerAdapter(
      DependentPipelineExecutionListener delegate, ExecutionRepository repository) {
    return new ExecutionListenerAdapter(delegate, repository);
  }
}
