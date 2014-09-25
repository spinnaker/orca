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



package com.netflix.spinnaker.orca.mort.config

import static retrofit.Endpoints.newFixedEndpoint

import com.google.gson.Gson
import com.netflix.spinnaker.orca.mort.MortService
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import retrofit.Endpoint
import retrofit.RestAdapter
import retrofit.client.Client
import retrofit.converter.GsonConverter

@Configuration
@Import(RetrofitConfiguration)
@CompileStatic
class MortConfiguration {

  @Autowired Client retrofitClient
  @Autowired RestAdapter.LogLevel retrofitLogLevel

  @Bean Endpoint mortEndpoint(@Value('${mort.baseUrl:http://mort.prod.netflix.net}') String mortBaseUrl) {
    newFixedEndpoint(mortBaseUrl)
  }

  @Bean MortService mortDeployService(Endpoint mortEndpoint, Gson gson) {
    new RestAdapter.Builder()
      .setEndpoint(mortEndpoint)
      .setClient(retrofitClient)
      .setLogLevel(retrofitLogLevel)
      .setConverter(new GsonConverter(gson))
      .build()
      .create(MortService)
  }
}
