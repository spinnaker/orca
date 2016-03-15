/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.actorsystem;

import java.util.Collections;
import java.util.List;
import akka.actor.ActorSystem;
import akka.cluster.sharding.ClusterSharding;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ActorSystemConfiguration {

  @Value("${akka.actor.system.name:Orca}") String actorSystemName;
  @Value("${akka.cluster.port:2551}") int clusterPort;

  @Bean(destroyMethod = "terminate") ActorSystem actorSystem(@Qualifier("akkaConfig") Config akkaConfig) {
    return ActorSystem.create(actorSystemName, akkaConfig);
  }

  @Bean ClusterSharding clusterSharding(ActorSystem actorSystem) {
    return ClusterSharding.get(actorSystem);
  }

  @Bean Config akkaConfig() {
    String currentIp = "127.0.0.1";
    List<String> seeds = Collections.singletonList(String.format("akka.tcp://%s@%s:%d", actorSystemName, currentIp, clusterPort));
    return ConfigFactory
      .empty()
      .withValue("akka.cluster.seed-nodes", ConfigValueFactory.fromIterable(seeds))
      .withValue("akka.remote.netty.tcp.port", ConfigValueFactory.fromAnyRef(clusterPort))
      .withValue("akka.remote.netty.tcp.hostname", ConfigValueFactory.fromAnyRef(currentIp))
      .withFallback(ConfigFactory.load());
  }
}
