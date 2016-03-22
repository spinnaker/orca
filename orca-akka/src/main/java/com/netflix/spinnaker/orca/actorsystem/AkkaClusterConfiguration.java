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

import java.util.List;
import javax.annotation.PostConstruct;
import akka.actor.ActorSystem;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AkkaClusterConfiguration {

  private final Logger log = LoggerFactory.getLogger(AkkaClusterConfiguration.class);

  @Autowired ActorSystem actorSystem;
  @Autowired ClusterSharding clusterSharding;
  @Autowired List<ClusteredActorDefinition> actorDefinitions;

  @PostConstruct
  public void initializeCluster() {
    ClusterShardingSettings settings = ClusterShardingSettings.create(actorSystem);
    actorDefinitions.forEach(actorDef ->
      clusterSharding.start(
        actorDef.props().actorClass().getSimpleName(),
        actorDef.props(),
        settings,
        actorDef.messageExtractor()
      )
    );
  }
}
