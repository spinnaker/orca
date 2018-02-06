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


package com.netflix.spinnaker.orca.retrofit

import com.netflix.spinnaker.config.OkHttpClientConfiguration
import com.netflix.spinnaker.orca.retrofit.exceptions.RetrofitExceptionHandler
import com.squareup.okhttp.ConnectionPool
import com.squareup.okhttp.Interceptor
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Scope
import org.springframework.core.Ordered.HIGHEST_PRECEDENCE
import org.springframework.core.annotation.Order
import retrofit.RestAdapter.LogLevel
import retrofit.client.OkClient

@Configuration
@Import(OkHttpClientConfiguration::class)
@EnableConfigurationProperties
class RetrofitConfiguration {

  @Value("\${okHttpClient.connectionPool.maxIdleConnections:5}")
  var maxIdleConnections: Int = 5

  @Value("\${okHttpClient.connectionPool.keepAliveDurationMs:300000}")
  var keepAliveDurationMs: Long = 300_000

  @Value("\${okHttpClient.retryOnConnectionFailure:true}")
  var retryOnConnectionFailure: Boolean = true

  @Bean(name = ["retrofitClient", "okClient"])
  @Scope(SCOPE_PROTOTYPE)
  fun retrofitClient(@Qualifier("okHttpClientConfiguration") okHttpClientConfig: OkHttpClientConfiguration): OkClient {
    val app = System.getProperty("spring.application.name", "unknown")
    val version = javaClass.`package`.implementationVersion ?: "1.0"
    val userAgent = "Spinnaker-$app/$version"
    val cfg = okHttpClientConfig.create()
    cfg.networkInterceptors().add(Interceptor { chain ->
      val req = chain.request().newBuilder().header("User-Agent", userAgent).build()
      chain.proceed(req)
    })
    cfg.connectionPool = ConnectionPool(maxIdleConnections, keepAliveDurationMs)
    cfg.retryOnConnectionFailure = retryOnConnectionFailure

    return OkClient(cfg)
  }

  @Bean
  fun retrofitLogLevel(@Value("\${retrofit.logLevel:BASIC}") retrofitLogLevel: String) =
    LogLevel.valueOf(retrofitLogLevel)

  @Bean
  @Order(HIGHEST_PRECEDENCE)
  fun retrofitExceptionHandler() = RetrofitExceptionHandler()
}
