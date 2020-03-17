/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.interlink.events;

import static com.netflix.spinnaker.orca.interlink.events.InterlinkEvent.EventType.CANCEL;

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType;
import com.netflix.spinnaker.orca.pipeline.CompoundExecutionOperator;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CancelInterlinkEvent implements InterlinkEvent {
  final EventType eventType = CANCEL;
  @Nullable String partition;
  @NonNull ExecutionType executionType;
  @NonNull String executionId;
  @Nullable String canceledBy;
  @Nullable String cancellationReason;

  public CancelInterlinkEvent(
      @NonNull ExecutionType executionType,
      @NonNull String executionId,
      @Nullable String canceledBy,
      @Nullable String cancellationReason) {
    this.executionType = executionType;
    this.executionId = executionId;
    this.canceledBy = canceledBy;
    this.cancellationReason = cancellationReason;
  }

  @Override
  public void applyTo(CompoundExecutionOperator executionOperator) {
    executionOperator.cancel(executionType, executionId, canceledBy, cancellationReason);
  }
}
