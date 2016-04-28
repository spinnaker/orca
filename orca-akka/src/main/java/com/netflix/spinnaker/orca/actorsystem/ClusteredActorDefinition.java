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

import akka.actor.Props;
import akka.cluster.sharding.ShardRegion.MessageExtractor;

public class ClusteredActorDefinition {

  public static ClusteredActorDefinition create(Props props, MessageExtractor messageExtractor) {
    return new ClusteredActorDefinition(props, messageExtractor) {
      @Override public Props props() {
        return props;
      }

      @Override public MessageExtractor messageExtractor() {
        return messageExtractor;
      }
    };
  }

  private final Props props;
  private final MessageExtractor messageExtractor;

  private ClusteredActorDefinition(Props props, MessageExtractor messageExtractor) {
    this.props = props;
    this.messageExtractor = messageExtractor;
  }

  public Props props() {
    return props;
  }

  public MessageExtractor messageExtractor() {
    return messageExtractor;
  }

}
