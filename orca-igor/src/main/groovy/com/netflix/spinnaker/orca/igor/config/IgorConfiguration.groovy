/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.igor.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.igor.IgorService
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import com.netflix.spinnaker.orca.retrofit.logging.RetrofitSlf4jLog
import com.squareup.okhttp.OkHttpClient
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import retrofit.Endpoint
import retrofit.RestAdapter
import retrofit.client.OkClient
import retrofit.converter.JacksonConverter

import java.util.concurrent.TimeUnit

import static retrofit.Endpoints.newFixedEndpoint

@Configuration
@Import(RetrofitConfiguration)
@ConditionalOnProperty(value = 'igor.baseUrl')
@CompileStatic
@ComponentScan("com.netflix.spinnaker.orca.igor")
class IgorConfiguration {

  @Autowired RestAdapter.LogLevel retrofitLogLevel

  @Value('${client.connectTimeout:20000}')
  int clientConnectTimeout

  @Value('${client.readTimeout:30000}')
  int clientReadTimeout

  @Bean
  Endpoint igorEndpoint(
    @Value('${igor.baseUrl}') String igorBaseUrl) {
    newFixedEndpoint(igorBaseUrl)
  }

  @Bean
  IgorService igorService(Endpoint igorEndpoint, ObjectMapper mapper) {
    OkHttpClient httpClient = new OkHttpClient()
    httpClient.setReadTimeout(clientReadTimeout, TimeUnit.MILLISECONDS)
    httpClient.setConnectTimeout(clientConnectTimeout,TimeUnit.MILLISECONDS)

    new RestAdapter.Builder()
      .setEndpoint(igorEndpoint)
      .setClient(new OkClient(httpClient))
      .setLogLevel(retrofitLogLevel)
      .setLog(new RetrofitSlf4jLog(IgorService))
      .setConverter(new JacksonConverter(mapper))
      .build()
      .create(IgorService)
  }

}
