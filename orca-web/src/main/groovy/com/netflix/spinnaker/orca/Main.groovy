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

package com.netflix.spinnaker.orca

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.ErrorConfiguration
import com.netflix.spinnaker.config.TomcatConfiguration
import com.netflix.spinnaker.orca.actorsystem.ActorSystemConfiguration
import com.netflix.spinnaker.orca.actorsystem.AkkaClusterConfiguration
import com.netflix.spinnaker.orca.actorsystem.DummyActorConfig
import com.netflix.spinnaker.orca.actorsystem.task.TaskActorFactory
import com.netflix.spinnaker.orca.applications.config.ApplicationConfig
import com.netflix.spinnaker.orca.bakery.config.BakeryConfiguration
import com.netflix.spinnaker.orca.batch.AkkaTaskTaskletAdapter
import com.netflix.spinnaker.orca.clouddriver.config.CloudDriverConfiguration
import com.netflix.spinnaker.orca.config.JesqueConfiguration
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.config.OrcaPersistenceConfiguration
import com.netflix.spinnaker.orca.config.RedisConfiguration
import com.netflix.spinnaker.orca.data.jackson.StageMixins
import com.netflix.spinnaker.orca.echo.config.EchoConfiguration
import com.netflix.spinnaker.orca.eureka.DiscoveryPollingConfiguration
import com.netflix.spinnaker.orca.flex.config.FlexConfiguration
import com.netflix.spinnaker.orca.front50.config.Front50Configuration
import com.netflix.spinnaker.orca.igor.config.IgorConfiguration
import com.netflix.spinnaker.orca.mahe.config.MaheConfiguration
import com.netflix.spinnaker.orca.mine.config.MineConfiguration
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.rush.config.RushConfiguration
import com.netflix.spinnaker.orca.tide.config.TideConfiguration
import com.netflix.spinnaker.orca.web.config.WebConfiguration
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration
import org.springframework.boot.autoconfigure.groovy.template.GroovyTemplateAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.web.SpringBootServletInitializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.scheduling.annotation.EnableAsync

@Configuration
@EnableAsync
@EnableAutoConfiguration(exclude = [BatchAutoConfiguration, GroovyTemplateAutoConfiguration])
@EnableBatchProcessing(modular = true)
@Import([
  WebConfiguration,
  ErrorConfiguration,
  OrcaConfiguration,
  OrcaPersistenceConfiguration,
  RedisConfiguration,
  JesqueConfiguration,
  BakeryConfiguration,
  EchoConfiguration,
  Front50Configuration,
  FlexConfiguration,
  CloudDriverConfiguration,
  RushConfiguration,
  IgorConfiguration,
  DiscoveryPollingConfiguration,
  TomcatConfiguration,
  MineConfiguration,
  MaheConfiguration,
  TideConfiguration,
  ApplicationConfig,
  ActorSystemConfiguration,
  AkkaClusterConfiguration,
  DummyActorConfig,
  TaskActorFactory,
  AkkaTaskTaskletAdapter
])
@ComponentScan([
  "com.netflix.spinnaker.config"
])
class Main extends SpringBootServletInitializer {
  static final Map<String, String> DEFAULT_PROPS = [
    'netflix.environment'    : 'test',
    'netflix.account'        : '${netflix.environment}',
    'netflix.stack'          : 'test',
    'spring.config.location' : '${user.home}/.spinnaker/',
    'spring.application.name': 'orca',
    'spring.config.name'     : 'spinnaker,${spring.application.name}',
    'spring.profiles.active' : '${netflix.environment},local'
  ]

  static void main(String... args) {
    new SpringApplicationBuilder().properties(DEFAULT_PROPS).sources(Main).run(args)
  }

  @Override
  SpringApplicationBuilder configure(SpringApplicationBuilder application) {
    application.properties(DEFAULT_PROPS).sources(Main)
  }

  static class StockMappingJackson2HttpMessageConverter extends MappingJackson2HttpMessageConverter {
  }

  @Bean
  StockMappingJackson2HttpMessageConverter customJacksonConverter(ObjectMapper objectMapper) {
    objectMapper.addMixInAnnotations(PipelineStage, StageMixins)
    new StockMappingJackson2HttpMessageConverter(objectMapper: objectMapper)
  }

}
