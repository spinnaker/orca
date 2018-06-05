/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.pipeline.persistence;

import static java.util.stream.Collectors.toList;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import rx.Observable;

public interface ExecutionRepository {
  void store(@Nonnull Execution orchestration);

  void storeStage(@Nonnull Stage stage);

  void updateStageContext(@Nonnull Stage stage);

  void removeStage(@Nonnull Execution execution, @Nonnull String stageId);

  void addStage(@Nonnull Stage stage);

  void cancel(@Nonnull ExecutionType type, @Nonnull String id);

  void cancel(@Nonnull ExecutionType type, @Nonnull String id, @Nullable String user, @Nullable String reason);

  void pause(@Nonnull ExecutionType type, @Nonnull String id, @Nullable String user);

  void resume(@Nonnull ExecutionType type, @Nonnull String id, @Nullable String user);

  void resume(@Nonnull ExecutionType type, @Nonnull String id, @Nullable String user, boolean ignoreCurrentStatus);

  boolean isCanceled(ExecutionType type, @Nonnull String id);

  void updateStatus(ExecutionType type, @Nonnull String id, @Nonnull ExecutionStatus status);

  @Nonnull
  Execution retrieve(@Nonnull ExecutionType type, @Nonnull String id) throws ExecutionNotFoundException;

  void delete(@Nonnull ExecutionType type, @Nonnull String id);

  @Nonnull
  Observable<Execution> retrieve(@Nonnull ExecutionType type);

  @Nonnull
  Observable<Execution> retrieve(@Nonnull ExecutionType type, @Nonnull ExecutionCriteria criteria);

  @Nonnull
  Observable<Execution> retrievePipelinesForApplication(@Nonnull String application);

  @Nonnull
  Observable<Execution> retrievePipelinesForPipelineConfigId(@Nonnull String pipelineConfigId,
                                                             @Nonnull ExecutionCriteria criteria);

  @Nonnull
  Observable<Execution> retrievePipelinesForPipelineConfigIdWithBuildTimeBoundary(@Nonnull String pipelineConfigId,
                                                                                  @Nonnull BuildTimeBoundaryExecutionCriteria criteria);

  @Nonnull
  Observable<Execution> retrieveOrchestrationsForApplication(@Nonnull String application,
                                                             @Nonnull ExecutionCriteria criteria);

  @Nonnull
  Execution retrieveOrchestrationForCorrelationId(@Nonnull String correlationId) throws ExecutionNotFoundException;

  @Nonnull
  List<Execution> retrieveBufferedExecutions();

  /**
   * Default criteria options of Executions to query Redis for.
   */
  final class ExecutionCriteria {
    public int getLimit() {
      return limit;
    }

    public @Nonnull ExecutionCriteria setLimit(int limit) {
      this.limit = limit;
      return this;
    }

    public @Nonnull Collection<ExecutionStatus> getStatuses() {
      return statuses;
    }

    public @Nonnull ExecutionCriteria setStatuses(Collection<String> statuses) {
      return setStatuses(
        statuses
          .stream()
          .map(ExecutionStatus::valueOf)
          .collect(toList())
          .toArray(new ExecutionStatus[statuses.size()])
      );
    }

    public @Nonnull ExecutionCriteria setStatuses(ExecutionStatus... statuses) {
      this.statuses = Arrays.asList(statuses);
      return this;
    }

    public int getPage() {
      return Math.max(page, 1);
    }

    public ExecutionCriteria setPage(int page) {
      this.page = page;
      return this;
    }

    private int limit;
    private Collection<ExecutionStatus> statuses = new ArrayList<>();
    private int page;

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      ExecutionCriteria that = (ExecutionCriteria) o;
      return limit == that.limit &&
        Objects.equals(statuses, that.statuses) &&
        page == that.page;
    }

    @Override public int hashCode() {
      return Objects.hash(limit, statuses, page);
    }
  }

  /**
   * Build time boundary criteria options of Executions to query Redis for.
   */
  final class BuildTimeBoundaryExecutionCriteria {

    public long getBuildTimeStartBoundary() {
      return buildTimeStartBoundary;
    }

    public @Nonnull BuildTimeBoundaryExecutionCriteria setBuildTimeStartBoundary(long buildTimeStartBoundary) {
      this.buildTimeStartBoundary = buildTimeStartBoundary;
      return this;
    }

    public long getBuildTimeEndBoundary() {
      return buildTimeEndBoundary;
    }

    public @Nonnull BuildTimeBoundaryExecutionCriteria setBuildTimeEndBoundary(long buildTimeEndBoundary) {
      this.buildTimeEndBoundary = buildTimeEndBoundary;
      return this;
    }

    public @Nonnull Collection<ExecutionStatus> getStatuses() {
      return statuses;
    }

    public @Nonnull BuildTimeBoundaryExecutionCriteria setStatuses(Collection<String> statuses) {
      return setStatuses(
        statuses
          .stream()
          .map(ExecutionStatus::valueOf)
          .collect(toList())
          .toArray(new ExecutionStatus[statuses.size()])
      );
    }

    public @Nonnull BuildTimeBoundaryExecutionCriteria setStatuses(ExecutionStatus... statuses) {
      this.statuses = Arrays.asList(statuses);
      return this;
    }

    private long buildTimeStartBoundary = 0;
    private long buildTimeEndBoundary = Long.MAX_VALUE;
    private Collection<ExecutionStatus> statuses = new ArrayList<>();

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      BuildTimeBoundaryExecutionCriteria that = (BuildTimeBoundaryExecutionCriteria) o;
      return buildTimeStartBoundary == that.buildTimeStartBoundary &&
        buildTimeEndBoundary == that.buildTimeEndBoundary &&
        Objects.equals(statuses, that.statuses);
    }

    @Override public int hashCode() {
      return Objects.hash(buildTimeStartBoundary, buildTimeEndBoundary, statuses);
    }
  }
}
