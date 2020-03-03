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
import com.netflix.spinnaker.orca.api.StageExecution;
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

  private final StageExecution parent;
  private final SyntheticStageOwner type;
  private final MutableGraph<StageExecution> graph =
      GraphBuilder.directed().build(); // TODO: is this actually useful?
  private final Optional<StageExecution> requiredPrefix;
  private @Nullable StageExecution lastAdded = null;

  private StageGraphBuilder(
      StageExecution parent, SyntheticStageOwner type, Optional<StageExecution> requiredPrefix) {
    this.parent = parent;
    this.type = type;
    this.requiredPrefix = requiredPrefix;
    this.requiredPrefix.ifPresent(this::add);
  }

  /**
   * Create a new builder for the before stages of {@code parent}.
   *
   * @param parent
   */
  public static @Nonnull StageGraphBuilder beforeStages(@Nonnull StageExecution parent) {
    return new StageGraphBuilder(parent, STAGE_BEFORE, Optional.empty());
  }

  /**
   * Create a new builder for the before stages of {@code parent}.
   *
   * @param parent
   * @param requiredPrefix
   */
  public static @Nonnull StageGraphBuilder beforeStages(
      @Nonnull StageExecution parent, @Nullable StageExecution requiredPrefix) {
    return new StageGraphBuilder(parent, STAGE_BEFORE, Optional.ofNullable(requiredPrefix));
  }

  /**
   * Create a new builder for the after stages of {@code parent}.
   *
   * @param parent
   */
  public static @Nonnull StageGraphBuilder afterStages(@Nonnull StageExecution parent) {
    return new StageGraphBuilder(parent, STAGE_AFTER, Optional.empty());
  }

  /**
   * Adds a new stage to the graph. By default the new stage is not dependent on any others. Use
   * {@link #connect(StageExecution, StageExecution)} to make it depend on other stages or have
   * other stages depend on it.
   *
   * @param init builder for setting up the stage. You do not need to configure {@link
   *     StageExecution#execution}, {@link StageExecution#parentStageId}, {@link
   *     StageExecution#syntheticStageOwner} or {@link StageExecution#refId} as this method will do
   *     that automatically.
   * @return the newly created stage.
   */
  public @Nonnull StageExecution add(@Nonnull Consumer<StageExecution> init) {
    StageExecution stage = newStage(init);
    add(stage);
    return stage;
  }

  /**
   * Adds a new stage to the graph. By default the new stage is not dependent on any others. Use
   * {@link #connect(StageExecution, StageExecution)} to make it depend on other stages or have
   * other stages depend on it.
   */
  public void add(@Nonnull StageExecution stage) {
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
   * StageExecution#requisiteStageRefIds}.
   *
   * @param previous The stage the new stage will depend on. If {@code previous} does not already
   *     exist in the graph, this method will add it.
   * @param init See {@link #add(Consumer)}
   * @return the newly created stage.
   */
  public @Nonnull StageExecution connect(
      @Nonnull StageExecution previous, @Nonnull Consumer<StageExecution> init) {
    StageExecution stage = add(init);
    connect(previous, stage);
    return stage;
  }

  /**
   * Makes {@code next} depend on {@code previous} via its {@link
   * StageExecution#requisiteStageRefIds}. If either {@code next} or {@code previous} are not yet
   * present in the graph this method will add them.
   */
  public void connect(@Nonnull StageExecution previous, @Nonnull StageExecution next) {
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
   * stages in order to use {@link #connect(StageExecution, Consumer)}.
   *
   * <p>If no stages have been added so far, this is synonymous with calling {@link #add(Consumer)}.
   *
   * @param init See {@link #add(Consumer)}
   * @return the newly created stage.
   */
  public @Nonnull StageExecution append(@Nonnull Consumer<StageExecution> init) {
    if (lastAdded == null) {
      return add(init);
    } else {
      return connect(lastAdded, init);
    }
  }

  public void append(@Nonnull StageExecution stage) {
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
  public @Nonnull Iterable<StageExecution> build() {
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

  private StageExecution newStage(Consumer<StageExecution> init) {
    StageExecution stage = new StageExecutionImpl();
    init.accept(stage);
    return stage;
  }
}
