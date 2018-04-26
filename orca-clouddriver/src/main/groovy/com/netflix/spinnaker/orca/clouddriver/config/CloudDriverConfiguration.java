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

package com.netflix.spinnaker.orca.clouddriver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.clouddriver.*;
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper;
import com.netflix.spinnaker.orca.qos.BufferStateSupplier;
import com.netflix.spinnaker.orca.qos.retrofit.*;
import com.netflix.spinnaker.orca.retrofit.RetrofitConfiguration;
import com.netflix.spinnaker.orca.retrofit.logging.RetrofitSlf4jLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import retrofit.Profiler;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.client.Client;
import retrofit.converter.JacksonConverter;

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.Optional;

import static java.util.stream.Collectors.toList;
import static retrofit.Endpoints.newFixedEndpoint;

@Configuration
@Import(RetrofitConfiguration.class)
@ComponentScan({
  "com.netflix.spinnaker.orca.clouddriver",
  "com.netflix.spinnaker.orca.kato.pipeline",
  "com.netflix.spinnaker.orca.kato.tasks"
})
@EnableConfigurationProperties(CloudDriverConfigurationProperties.class)
class CloudDriverConfiguration {

  @ConditionalOnMissingBean(ObjectMapper.class)
  @Bean
  ObjectMapper mapper() {
    return OrcaObjectMapper.newInstance();
  }

  @Bean
  WritableRequestMetrics cloudDriverRequestMetrics(
    @Value("${cloudDriver.qos.metrics.window.seconds:300}")
      long qosMetricsWindowSeconds) {
    return new WindowedRequestMetrics("CloudDriver", Duration.ofSeconds(qosMetricsWindowSeconds));
  }

  @Bean BufferStateSupplier cloudDriverRequestMetricsBufferStateSupplier(
    RequestMetrics cloudDriverRequestMetrics,
    @Value("${cloudDriver.qos.thresholds.averageRequestDurationMillis:10000}")
      long averageRequestDurationThresholdMillis,
    @Value("${cloudDriver.qos.thresholds.errorPercentage:50}")
      int errorPercentageThreshold
  ) {
    return new RequestMetricsBufferStateSupplier(
      cloudDriverRequestMetrics,
      Duration.ofMillis(averageRequestDurationThresholdMillis),
      errorPercentageThreshold
    );
  }

  @Bean
  Profiler<?> cloudDriverRequestProfiler(WritableRequestMetrics cloudDriverRequestMetrics) {
    return new RequestMetricsProfiler(cloudDriverRequestMetrics);
  }

  @Bean
  ClouddriverRetrofitBuilder clouddriverRetrofitBuilder(
    ObjectMapper objectMapper,
    Client retrofitClient,
    RestAdapter.LogLevel retrofitLogLevel,
    RequestInterceptor spinnakerRequestInterceptor,
    CloudDriverConfigurationProperties cloudDriverConfigurationProperties,
    WritableRequestMetrics cloudDriverRequestMetrics
  ) {
    return new ClouddriverRetrofitBuilder(
      objectMapper,
      retrofitClient,
      retrofitLogLevel,
      spinnakerRequestInterceptor,
      cloudDriverConfigurationProperties,
      cloudDriverRequestMetrics
    );
  }

  static class ClouddriverRetrofitBuilder {
    private final ObjectMapper objectMapper;
    private final Client retrofitClient;
    private final RestAdapter.LogLevel retrofitLogLevel;
    private final RequestInterceptor spinnakerRequestInterceptor;
    private final CloudDriverConfigurationProperties cloudDriverConfigurationProperties;
    private final WritableRequestMetrics cloudDriverRequestMetrics;

    private final Logger log = LoggerFactory.getLogger(getClass());

    ClouddriverRetrofitBuilder(
      ObjectMapper objectMapper,
      Client retrofitClient,
      RestAdapter.LogLevel retrofitLogLevel,
      RequestInterceptor spinnakerRequestInterceptor,
      CloudDriverConfigurationProperties cloudDriverConfigurationProperties,
      WritableRequestMetrics cloudDriverRequestMetrics
    ) {
      this.objectMapper = objectMapper;
      this.retrofitClient = retrofitClient;
      this.retrofitLogLevel = retrofitLogLevel;
      this.spinnakerRequestInterceptor = spinnakerRequestInterceptor;
      this.cloudDriverConfigurationProperties = cloudDriverConfigurationProperties;
      this.cloudDriverRequestMetrics = cloudDriverRequestMetrics;
    }

    public <T> T buildWriteableService(Class<T> type) {
      return buildService(type, cloudDriverConfigurationProperties.getCloudDriverBaseUrl());
    }

    private <T> T buildService(Class<T> type, String url) {
      return new RestAdapter.Builder()
        .setRequestInterceptor(spinnakerRequestInterceptor)
        .setEndpoint(newFixedEndpoint(url))
        .setClient(retrofitClient)
        .setLogLevel(retrofitLogLevel)
        .setLog(new RetrofitSlf4jLog(type))
        .setConverter(new JacksonConverter(objectMapper))
        .setProfiler(new RequestMetricsProfiler(cloudDriverRequestMetrics))
        .build()
        .create(type);
    }

    private <T> SelectableService buildReadOnlyService(Class<T> type) {
      if (!cloudDriverConfigurationProperties.hasReadOnlyBaseUrl()) {
        log.info(
          "readonly URL not configured for clouddriver, using writeable clouddriver {} for {}",
          cloudDriverConfigurationProperties.getCloudDriverBaseUrl(),
          type.getSimpleName()
        );
      }

      return new SelectableService(
        cloudDriverConfigurationProperties
          .getCloudDriverReadOnlyBaseUrls()
          .stream()
          .map(it -> {
            ServiceSelector selector = new DefaultServiceSelector(buildService(type, it.getBaseUrl()), it.getPriority(), it.getConfig());

            Class<ServiceSelector> selectorClass = (Class<ServiceSelector>) Optional.ofNullable(it.getConfig())
              .map(cfg -> cfg.get("selectorClass"))
              .map(Object::toString)
              .map(clz -> {
                try {
                  return Class.forName(clz);
                } catch (ClassNotFoundException e) {
                  throw new IllegalStateException("ServiceSelector implementation not found", e);
                }
              })
              .orElse(null);
            if (selectorClass != null) {
              try {
                selector = (ServiceSelector) selectorClass.getConstructors()[0].newInstance(
                  selector.getService(), it.getPriority(), it.getConfig()
                );
              } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Could not construct ServiceSelector implementation", e);
              }
            }

            return selector;
          })
          .collect(toList())
      );
    }
  }

  @Bean
  public MortService mortDeployService(ClouddriverRetrofitBuilder builder) {
    return new DelegatingMortService(builder.buildReadOnlyService(MortService.class));
  }

  @Bean
  public CloudDriverCacheService clouddriverCacheService(ClouddriverRetrofitBuilder builder) {
    return builder.buildWriteableService(CloudDriverCacheService.class);
  }

  @Bean
  public CloudDriverCacheStatusService cloudDriverCacheStatusService(ClouddriverRetrofitBuilder builder) {
    return new DelegatingCloudDriverCacheStatusService(builder.buildReadOnlyService(CloudDriverCacheStatusService.class));
  }

  @Bean
  public OortService oortDeployService(ClouddriverRetrofitBuilder builder) {
    return new DelegatingOortService(builder.buildReadOnlyService(OortService.class));
  }

  @Bean
  public KatoRestService katoDeployService(ClouddriverRetrofitBuilder builder) {
    return builder.buildWriteableService(KatoRestService.class);
  }

  @Bean
  public CloudDriverTaskStatusService cloudDriverTaskStatusService(ClouddriverRetrofitBuilder builder) {
    return new DelegatingCloudDriverTaskStatusService(builder.buildReadOnlyService(CloudDriverTaskStatusService.class));
  }

  @Bean
  public FeaturesRestService featuresRestService(ClouddriverRetrofitBuilder builder) {
    return builder.buildWriteableService(FeaturesRestService.class);
  }
}
