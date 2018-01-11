/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;

@JsonTypeName("pubsub")
public class PubSubTrigger extends Trigger {

  @Nonnull private final Map<String, Object> attributeConstraints;
  @Nonnull private final Map<String, Object> payloadConstraints;
  @Nonnull private final List<String> expectedArtifactIds;
  @Nonnull private final String pubsubSystem;
  @Nonnull private final String subscriptionName;

  @JsonCreator
  public PubSubTrigger(
    @Nonnull @JsonProperty("attributeConstraints")
      Map<String, Object> attributeConstraints,
    @Nonnull @JsonProperty("payloadConstraints")
      Map<String, Object> payloadConstraints,
    @Nonnull @JsonProperty("expectedArtifactIds")
      List<String> expectedArtifactIds,
    @Nonnull @JsonProperty("pubsubSystem") String pubsubSystem,
    @Nonnull @JsonProperty("subscriptionName") String subscriptionName,
    @Nullable @JsonProperty("user") String user,
    @Nullable @JsonProperty("parameters") Map<String, Object> parameters,
    @Nullable @JsonProperty("artifacts") List<Artifact> artifacts,
    @JsonProperty("rebake") boolean rebake
  ) {
    super(user, parameters, artifacts, rebake);
    this.attributeConstraints = attributeConstraints;
    this.payloadConstraints = payloadConstraints;
    this.expectedArtifactIds = expectedArtifactIds;
    this.pubsubSystem = pubsubSystem;
    this.subscriptionName = subscriptionName;
  }

  @Nonnull public Map<String, Object> getAttributeConstraints() {
    return attributeConstraints;
  }

  @Nonnull public Map<String, Object> getPayloadConstraints() {
    return payloadConstraints;
  }

  @Nonnull public List<String> getExpectedArtifactIds() {
    return expectedArtifactIds;
  }

  @Nonnull public String getPubsubSystem() {
    return pubsubSystem;
  }

  @Nonnull public String getSubscriptionName() {
    return subscriptionName;
  }

  @Override public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    PubSubTrigger that = (PubSubTrigger) o;
    return Objects.equals(attributeConstraints, that.attributeConstraints) &&
      Objects.equals(payloadConstraints, that.payloadConstraints) &&
      Objects.equals(expectedArtifactIds, that.expectedArtifactIds) &&
      Objects.equals(pubsubSystem, that.pubsubSystem) &&
      Objects.equals(subscriptionName, that.subscriptionName);
  }

  @Override public int hashCode() {

    return Objects.hash(
      super.hashCode(),
      attributeConstraints,
      payloadConstraints,
      expectedArtifactIds,
      pubsubSystem,
      subscriptionName
    );
  }
}
