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

package com.netflix.spinnaker.orca.pipeline.graph;

import static com.netflix.spinnaker.orca.api.pipeline.SyntheticStageOwner.STAGE_AFTER;
import static com.netflix.spinnaker.orca.api.pipeline.SyntheticStageOwner.STAGE_BEFORE;
import static java.lang.String.format;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.MutableGraph;
import com.netflix.spinnaker.orca.api.pipeline.SyntheticStageOwner;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class StageGraphBuilder {

  private final StageExecutionImpl parent;
  private final SyntheticStageOwner type;
  private final MutableGraph<StageExecutionImpl> graph =
      GraphBuilder.directed().build(); // TODO: is this actually useful?
  private final Optional<StageExecutionImpl> requiredPrefix;
  private @Nullable StageExecutionImpl lastAdded = null;

  private StageGraphBuilder(
      StageExecutionImpl parent,
      SyntheticStageOwner type,
      Optional<StageExecutionImpl> requiredPrefix) {
    this.parent = parent;
    this.type = type;
    this.requiredPrefix = requiredPrefix;
    this.requiredPrefix.ifPresent(this::add);
  }

  /** Create a new builder for the before stages of {@code parent}. */
  public static @Nonnull StageGraphBuilder beforeStages(@Nonnull StageExecutionImpl parent) {
    return new StageGraphBuilder(parent, STAGE_BEFORE, Optional.empty());
  }

  /** Create a new builder for the before stages of {@code parent}. */
  public static @Nonnull StageGraphBuilder beforeStages(
      @Nonnull StageExecutionImpl parent, @Nullable StageExecutionImpl requiredPrefix) {
    return new StageGraphBuilder(parent, STAGE_BEFORE, Optional.ofNullable(requiredPrefix));
  }

  /** Create a new builder for the after stages of {@code parent}. */
  public static @Nonnull StageGraphBuilder afterStages(@Nonnull StageExecutionImpl parent) {
    return new StageGraphBuilder(parent, STAGE_AFTER, Optional.empty());
  }

  /**
   * Adds a new stage to the graph. By default the new stage is not dependent on any others. Use
   * {@link #connect(StageExecutionImpl, StageExecutionImpl)} to make it depend on other stages or
   * have other stages depend on it.
   *
   * @param init builder for setting up the stage. You do not need to configure {@link
   *     StageExecutionImpl#execution}, {@link StageExecutionImpl#parentStageId}, {@link
   *     StageExecutionImpl#syntheticStageOwner} or {@link StageExecutionImpl#refId} as this method
   *     will do that automatically.
   * @return the newly created stage.
   */
  public @Nonnull StageExecutionImpl add(@Nonnull Consumer<StageExecutionImpl> init) {
    StageExecutionImpl stage = newStage(init);
    add(stage);
    return stage;
  }

  /**
   * Adds a new stage to the graph. By default the new stage is not dependent on any others. Use
   * {@link #connect(StageExecutionImpl, StageExecutionImpl)} to make it depend on other stages or
   * have other stages depend on it.
   */
  public void add(@Nonnull StageExecutionImpl stage) {
    stage.setExecution(parent.getExecution());
    stage.setParentStageId(parent.getId());
    stage.setSyntheticStageOwner(type);
    if (graph.addNode(stage)) {
      stage.setRefId(generateRefId());
    }
    lastAdded = stage;
  }

  /**
   * Adds a new stage to the graph and makes it depend on {@code previous} via its {@link
   * StageExecutionImpl#requisiteStageRefIds}.
   *
   * @param previous The stage the new stage will depend on. If {@code previous} does not already
   *     exist in the graph, this method will add it.
   * @param init See {@link #add(Consumer)}
   * @return the newly created stage.
   */
  public @Nonnull StageExecutionImpl connect(
      @Nonnull StageExecutionImpl previous, @Nonnull Consumer<StageExecutionImpl> init) {
    StageExecutionImpl stage = add(init);
    connect(previous, stage);
    return stage;
  }

  /**
   * Makes {@code next} depend on {@code previous} via its {@link
   * StageExecutionImpl#requisiteStageRefIds}. If either {@code next} or {@code previous} are not
   * yet present in the graph this method will add them.
   */
  public void connect(@Nonnull StageExecutionImpl previous, @Nonnull StageExecutionImpl next) {
    add(previous);
    add(next);
    Set<String> requisiteStageRefIds = new HashSet<>(next.getRequisiteStageRefIds());
    requisiteStageRefIds.add(previous.getRefId());
    next.setRequisiteStageRefIds(requisiteStageRefIds);
    graph.putEdge(previous, next);
  }

  /**
   * Adds a new stage to the graph and makes it depend on the last stage that was added if any. This
   * is convenient for straightforward stage graphs to avoid having to pass around references to
   * stages in order to use {@link #connect(StageExecutionImpl, Consumer)}.
   *
   * <p>If no stages have been added so far, this is synonymous with calling {@link #add(Consumer)}.
   *
   * @param init See {@link #add(Consumer)}
   * @return the newly created stage.
   */
  public @Nonnull StageExecutionImpl append(@Nonnull Consumer<StageExecutionImpl> init) {
    if (lastAdded == null) {
      return add(init);
    } else {
      return connect(lastAdded, init);
    }
  }

  public void append(@Nonnull StageExecutionImpl stage) {
    if (lastAdded == null) {
      add(stage);
    } else {
      connect(lastAdded, stage);
    }
  }

  /**
   * Builds and returns the stages represented in the graph. This method is not typically useful to
   * implementors of {@link StageDefinitionBuilder}, it's used internally and by tests.
   */
  public @Nonnull Iterable<StageExecutionImpl> build() {
    requiredPrefix.ifPresent(
        prefix ->
            graph
                .nodes()
                .forEach(
                    it -> {
                      if (it != prefix && it.getRequisiteStageRefIds().isEmpty()) {
                        connect(prefix, it);
                      }
                    }));
    return graph.nodes();
  }

  private String generateRefId() {
    long offset =
        parent.getExecution().getStages().stream()
            .filter(
                i ->
                    parent.getId().equals(i.getParentStageId())
                        && type == i.getSyntheticStageOwner())
            .count();

    return format(
        "%s%s%d",
        parent.getRefId(), type == STAGE_BEFORE ? "<" : ">", offset + graph.nodes().size());
  }

  private StageExecutionImpl newStage(Consumer<StageExecutionImpl> init) {
    StageExecutionImpl stage = new StageExecutionImpl();
    init.accept(stage);
    return stage;
  }
}
