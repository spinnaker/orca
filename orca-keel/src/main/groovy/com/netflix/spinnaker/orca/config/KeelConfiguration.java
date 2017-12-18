/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.KeelModule;
import com.netflix.spinnaker.orca.KeelService;
import com.netflix.spinnaker.orca.retrofit.logging.RetrofitSlf4jLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import retrofit.Endpoint;
import retrofit.Endpoints;
import retrofit.RestAdapter;
import retrofit.client.Client;
import retrofit.converter.JacksonConverter;

@ConditionalOnExpression("${keel.enabled:true}")
@ComponentScan(
  basePackageClasses = KeelModule.class,
  basePackages = {
    "com.netflix.spinnaker.orca.keel",
    "com.netflix.spinnaker.orca.keel.task",
    "com.netflix.spinnaker.orca.keel.pipeline"
  }
)
public class KeelConfiguration {
  @Bean
  public Endpoint keelEndpoint(@Value("${keel.baseUrl}") String keelBaseUrl) {
    return Endpoints.newFixedEndpoint(keelBaseUrl);
  }

  @Bean
  public KeelService keelService(Endpoint keelEndpoint) {
    return new RestAdapter.Builder()
      .setEndpoint(keelEndpoint)
      .setClient(retrofitClient)
      .setLogLevel(retrofitLogLevel)
      .setLog(new RetrofitSlf4jLog(KeelService.class))
      .setConverter(new JacksonConverter())
      .build()
      .create(KeelService.class);
  }

  @Bean
  public ObjectMapper keelObjectMapper() {
    return new ObjectMapper()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  public RestAdapter.LogLevel getRetrofitLogLevel() {
    return retrofitLogLevel;
  }

  public void setRetrofitLogLevel(RestAdapter.LogLevel retrofitLogLevel) {
    this.retrofitLogLevel = retrofitLogLevel;
  }

  public Client getRetrofitClient() {
    return retrofitClient;
  }

  public void setRetrofitClient(Client retrofitClient) {
    this.retrofitClient = retrofitClient;
  }

  @Autowired
  private RestAdapter.LogLevel retrofitLogLevel;
  
  @Autowired
  private Client retrofitClient;
}
