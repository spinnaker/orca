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

package com.netflix.spinnaker.orca.actorsystem.task;

import java.util.function.Function;
import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.Props;
import com.netflix.spinnaker.orca.actorsystem.ClusteredActorDefinition;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class TaskActorFactory extends AbstractFactoryBean<ClusteredActorDefinition> {

  private final ApplicationContext applicationContext;
  private final ExecutionRepository repository;
  private final Function<ActorContext, ActorRef> stageActor;

  @Autowired
  public TaskActorFactory(
    ApplicationContext applicationContext,
    ExecutionRepository repository,
    @Qualifier("stageActor") Function<ActorContext, ActorRef> stageActor) {
    this.applicationContext = applicationContext;
    this.repository = repository;
    this.stageActor = stageActor;
  }

  @Override
  public Class<?> getObjectType() {
    return ClusteredActorDefinition.class;
  }

  @Override
  protected ClusteredActorDefinition createInstance() {
    return ClusteredActorDefinition.create(
      Props.create(TaskActor.class, applicationContext, repository, stageActor),
      new TaskMessageExtractor()
    );
  }
}
