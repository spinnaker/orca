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

package com.netflix.spinnaker.orca.pipeline;

import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import com.netflix.spinnaker.orca.pipeline.TaskNode.TaskDefinition;
import com.netflix.spinnaker.orca.pipeline.model.DefaultTask;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner;
import lombok.extern.slf4j.Slf4j;
import static com.google.common.collect.Lists.reverse;
import static com.netflix.spinnaker.orca.ExecutionStatus.NOT_STARTED;
import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.StageDefinitionBuilderSupport.newStage;
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER;
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

@Slf4j
public abstract class ExecutionRunnerSupport implements ExecutionRunner {
  private final Collection<StageDefinitionBuilder> stageDefinitionBuilders;

  public ExecutionRunnerSupport(Collection<StageDefinitionBuilder> stageDefinitionBuilders) {
    this.stageDefinitionBuilders = stageDefinitionBuilders;
  }

  /**
   * Plans the tasks in a stage including any pre and post stages.
   * Implementations may call this directly before executing an individual stage
   * or all in advance.
   *
   * @param stage    the stage with no tasks currently attached.
   * @param callback callback invoked with the tasks of the stage.
   * @param <T>      the execution type.
   */
  protected <T extends Execution<T>> void planStage(
    Stage<T> stage,
    BiConsumer<Collection<Stage<T>>, TaskNode.TaskGraph> callback
  ) {
    StageDefinitionBuilder builder = findBuilderForStage(stage);
    Map<SyntheticStageOwner, List<Stage<T>>> aroundStages = builder
      .aroundStages(stage)
      .stream()
      .collect(groupingBy(Stage::getSyntheticStageOwner));

    reverse(aroundStages.getOrDefault(STAGE_BEFORE, emptyList()))
      .forEach(preStage -> planBeforeOrAfterStage(stage, preStage, STAGE_BEFORE, callback));

    if (builder instanceof BranchingStageDefinitionBuilder) {
      BranchingStageDefinitionBuilder branchBuilder = (BranchingStageDefinitionBuilder) builder;

      // build tasks that should run before the branch
      TaskNode.TaskGraph beforeGraph = TaskNode.build(preBranch ->
        branchBuilder.preBranchGraph(stage, preBranch)
      );
      callback.accept(singleton(stage), beforeGraph);

      // represent the parallel branches with a synthetic stage for each branch
      Collection<Stage<T>> parallelStages = branchBuilder
        .parallelContexts(stage)
        .stream()
        .map(context ->
          newStage(
            stage.getExecution(),
            context.get("type").toString(),
            context.get("name").toString(),
            context,
            stage,
            STAGE_AFTER
          ))
        .collect(toList());

      // main stage name can be overridden
      stage.setName(branchBuilder.parallelStageName(stage, parallelStages.size() > 1));

      // inject the new parallel branches into the execution model
      parallelStages.forEach(it -> injectStage(stage, it, STAGE_AFTER));

      // TODO: ideally we'd evaluate the parallel branches' own synthetic stages here but no way to pass them to the callback so they'll get added in series
      // need some kind of context so we know what flow we're adding stuff to

      // the parallel branch type may be different (as in deploy stages) so re-evaluate the builder
      StageDefinitionBuilder parallelBranchBuilder = findBuilderForStage(parallelStages.iterator().next());
      // just build task graph once because it's the same for all branches
      TaskNode.TaskGraph taskGraph = parallelBranchBuilder.buildTaskGraph(parallelStages.iterator().next());
      callback.accept(parallelStages, taskGraph);

      // build tasks that run after the branch
      TaskNode.TaskGraph afterGraph = TaskNode.build(postBranch -> {
        branchBuilder.postBranchGraph(stage, postBranch);
      });
      callback.accept(singleton(stage), afterGraph);
    } else {
      TaskNode.TaskGraph taskGraph = builder.buildTaskGraph(stage);
      callback.accept(singleton(stage), taskGraph);
    }

    reverse(aroundStages.getOrDefault(STAGE_AFTER, emptyList()))
      .forEach(postStage -> planBeforeOrAfterStage(stage, postStage, STAGE_AFTER, callback));
  }

  private <T extends Execution<T>> void planBeforeOrAfterStage(
    Stage<T> parent,
    Stage<T> stage,
    SyntheticStageOwner type,
    BiConsumer<Collection<Stage<T>>, TaskNode.TaskGraph> callback
  ) {
    injectStage(parent, stage, type);
    planStage(stage, callback);
  }

  private <T extends Execution<T>> void injectStage(
    Stage<T> parent,
    Stage<T> stage,
    SyntheticStageOwner type
  ) {
    List<Stage<T>> stages = parent.getExecution().getStages();
    int index = stages.indexOf(parent);
    int offset = type == STAGE_BEFORE ? 0 : 1;
    stages.add(index + offset, stage);
    stage.setParentStageId(parent.getId());
  }

  // TODO: change callback type to Consumer<TaskDefinition>
  protected <T extends Execution<T>> void planTasks(Stage<T> stage, TaskNode.TaskGraph taskGraph, boolean isSubGraph, Consumer<com.netflix.spinnaker.orca.pipeline.model.Task> callback) {
    for (ListIterator<TaskNode> itr = taskGraph.listIterator(); itr.hasNext(); ) {
      boolean isStart = !itr.hasPrevious();
      // do this after calling itr.hasPrevious because ListIterator is stupid
      TaskNode taskDef = itr.next();
      boolean isEnd = !itr.hasNext();

      if (taskDef instanceof TaskDefinition) {
        planTask(stage, (TaskDefinition) taskDef, isSubGraph, isStart, isEnd, callback);
      } else if (taskDef instanceof TaskNode.TaskGraph) {
        planTasks(stage, (TaskNode.TaskGraph) taskDef, true, callback);
      } else {
        throw new UnsupportedOperationException(format("Unknown TaskNode type %s", taskDef.getClass().getName()));
      }
    }
  }

  private <T extends Execution<T>> void planTask(Stage<T> stage, TaskDefinition taskDef, boolean isSubGraph, boolean isStart, boolean isEnd, Consumer<com.netflix.spinnaker.orca.pipeline.model.Task> callback) {
    DefaultTask task = new DefaultTask();
    if (isStart) {
      if (isSubGraph) {
        task.setLoopStart(true);
      } else {
        task.setStageStart(true);
      }
    }
    task.setId(String.valueOf((stage.getTasks().size() + 1)));
    task.setName(taskDef.getName());
    task.setStatus(NOT_STARTED);
    task.setImplementingClass(taskDef.getImplementingClass());
    if (isEnd) {
      if (isSubGraph) {
        task.setLoopEnd(true);
      } else {
        task.setStageEnd(true);
      }
    }
    stage.getTasks().add(task);

    callback.accept(task);
  }

  private <T extends Execution<T>> StageDefinitionBuilder findBuilderForStage(Stage<T> stage) {
    return stageDefinitionBuilders
      .stream()
      .filter(builder -> builder.getType().equals(stage.getType()))
      .findFirst()
      .orElseThrow(() -> new NoSuchStageDefinitionBuilder(stage.getType()));
  }
}
