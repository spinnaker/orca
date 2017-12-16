/*
 * Copyright 2017 Netflix, Inc.
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
import javax.annotation.Nonnull;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("manual")
public class ManualTrigger extends Trigger {

  private final List<Map<String, Object>> notifications;

  @JsonCreator
  public ManualTrigger(
    @Nonnull @JsonProperty("user") String user,
    @Nonnull @JsonProperty("parameters") Map<String, Object> parameters,
    @Nonnull @JsonProperty("notifications")
      List<Map<String, Object>> notifications
  ) {
    super(user, parameters);
    this.notifications = notifications;
  }

  public @Nonnull List<Map<String, Object>> getNotifications() {
    return notifications;
  }
}
