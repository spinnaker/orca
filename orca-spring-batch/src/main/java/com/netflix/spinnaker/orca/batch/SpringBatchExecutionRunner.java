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

package com.netflix.spinnaker.orca.batch;

import java.io.Serializable;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.netflix.spinnaker.orca.pipeline.ExecutionRunnerSupport;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.model.*;
import com.netflix.spinnaker.orca.pipeline.parallel.WaitForRequisiteCompletionStage;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.DuplicateJobException;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.support.ReferenceJobFactory;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.builder.FlowJobBuilder;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.job.flow.support.SimpleFlow;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.batch.core.step.builder.TaskletStepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import static com.google.common.collect.Maps.newHashMap;
import static com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder.StageDefinitionBuilderSupport.newStage;
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_AFTER;
import static com.netflix.spinnaker.orca.pipeline.model.SyntheticStageOwner.STAGE_BEFORE;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;

@Slf4j
public class SpringBatchExecutionRunner extends ExecutionRunnerSupport {

  private static final int MAX_PARALLEL_CONCURRENCY = 25;

  private final ExecutionRepository executionRepository;
  private final JobLauncher jobLauncher;
  private final JobRegistry jobRegistry;
  private final JobBuilderFactory jobs;
  private final StepBuilderFactory steps;
  private final TaskTaskletAdapter taskTaskletAdapter;
  private final Map<Class, com.netflix.spinnaker.orca.Task> tasks;
  private final ExecutionListenerProvider executionListenerProvider;

  public SpringBatchExecutionRunner(
    Collection<StageDefinitionBuilder> stageDefinitionBuilders,
    ExecutionRepository executionRepository,
    JobLauncher jobLauncher,
    JobRegistry jobRegistry,
    JobBuilderFactory jobs,
    StepBuilderFactory steps,
    TaskTaskletAdapter taskTaskletAdapter,
    Collection<com.netflix.spinnaker.orca.Task> tasks,
    ExecutionListenerProvider executionListenerProvider
  ) {
    super(stageDefinitionBuilders);
    this.executionRepository = executionRepository;
    this.jobLauncher = jobLauncher;
    this.jobRegistry = jobRegistry;
    this.jobs = jobs;
    this.steps = steps;
    this.taskTaskletAdapter = taskTaskletAdapter;
    this.tasks = tasks.stream().collect(Collectors.toMap(
      com.netflix.spinnaker.orca.Task::getClass, Function.identity())
    );
    this.executionListenerProvider = executionListenerProvider;
  }

  @Override
  public <T extends Execution<T>> void start(T execution) throws Exception {
    super.start(execution);
    Job job = createJob(execution);

    // TODO-AJ This is hokiepokie
    if (execution instanceof Pipeline) {
      executionRepository.store((Pipeline) execution);
    } else {
      executionRepository.store((Orchestration) execution);
    }

    jobLauncher.run(job, createJobParameters(execution));
  }

  private <E extends Execution<E>> JobParameters createJobParameters(E subject) {
    JobParametersBuilder params = new JobParametersBuilder();
    params.addString("pipeline", subject.getId());
//    params.addString("name", subject.getName());
    params.addString("application", subject.getApplication());
    params.addString("timestamp", String.valueOf(System.currentTimeMillis()));
    return params.toJobParameters();
  }

  private <E extends Execution<E>> Job createJob(E execution) throws NoSuchJobException, DuplicateJobException {
    String jobName = jobNameFor(execution);
    if (!jobRegistry.getJobNames().contains(jobName)) {
      FlowJobBuilder flowJobBuilder = buildStepsForExecution(jobs.get(jobName), execution).build();

      executionListenerProvider.allJobExecutionListeners().forEach(flowJobBuilder::listener);

      Job job = flowJobBuilder.build();
      jobRegistry.register(new ReferenceJobFactory(job));
    }
    return jobRegistry.getJob(jobName);
  }

  private <E extends Execution<E>> FlowBuilder<FlowJobBuilder> buildStepsForExecution(JobBuilder builder, E execution) {
    List<Stage<E>> stages = execution.getStages();
    FlowBuilder<FlowJobBuilder> flow = builder.flow(initStep());
    if (execution.isParallel()) {
      flow = buildStepsForParallelExecution(flow, stages);
    } else {
      flow = buildStepsForLinearExecution(flow, stages);
    }
    return flow;
  }

  private <E extends Execution<E>> FlowBuilder<FlowJobBuilder> buildStepsForParallelExecution(FlowBuilder<FlowJobBuilder> flow, List<Stage<E>> stages) {
    List<Stage<E>> initialStages = stages
      .stream()
      .filter(Stage::isInitialStage)
      .collect(toList());
    Set<Serializable> alreadyBuilt = new HashSet<>();
    for (Stage<E> stage : initialStages) {
      flow = buildStepsForStageAndDownstream(flow, stage, alreadyBuilt);
    }
    return flow;
  }

  private <E extends Execution<E>> FlowBuilder<FlowJobBuilder> buildStepsForLinearExecution(FlowBuilder<FlowJobBuilder> flow, List<Stage<E>> stages) {
    for (Stage<E> stage : stages) {
      flow = flow.next(buildStepsForStage(stage));
    }
    return flow;
  }

  private Step initStep() {
    return steps.get("orca-init-step")
      .tasklet(new PipelineInitializerTasklet())
      .build();
  }

  private <E extends Execution<E>, Q> FlowBuilder<Q> buildStepsForStageAndDownstream(FlowBuilder<Q> flow, Stage<E> stage, Set<Serializable> alreadyBuilt) {
    if (alreadyBuilt.contains(stage.getRefId())) {
      log.info("Already built {}", stage.getRefId());
      return flow;
    } else {
      alreadyBuilt.add(stage.getRefId());
      if (stage.isJoin()) {
        flow.next(buildUpstreamJoin(stage));
      } else {
        flow.next(buildStepsForStage(stage));
      }
      return buildDownstreamStages(flow, stage, alreadyBuilt);
    }
  }

  private <E extends Execution<E>, Q> FlowBuilder<Q> buildDownstreamStages(FlowBuilder<Q> flow, final Stage<E> stage, Set<Serializable> alreadyBuilt) {
    List<Stage<E>> downstreamStages = stage.downstreamStages();
    boolean isFork = downstreamStages.size() > 1;
    if (isFork) {
      return buildDownstreamFork(flow, stage, downstreamStages, alreadyBuilt);
    } else if (downstreamStages.isEmpty()) {
      return flow;
    } else {
      // TODO: loop is misleading as we've already established there is only one
      for (Stage<E> downstreamStage : downstreamStages) {
        flow = buildStepsForStageAndDownstream(flow, downstreamStage, alreadyBuilt);
      }
      return flow;
    }
  }

  private <E extends Execution<E>, Q> FlowBuilder<Q> buildDownstreamFork(FlowBuilder<Q> flow, Stage<E> stage, List<Stage<E>> downstreamStages, Set<Serializable> alreadyBuilt) {
    List<Flow> flows = downstreamStages
      .stream()
      .map(downstreamStage -> {
        FlowBuilder<Flow> flowBuilder = flowBuilder(format("ChildExecution.%s.%s", downstreamStage.getRefId(), downstreamStage.getId()));
        return buildStepsForStageAndDownstream(flowBuilder, downstreamStage, alreadyBuilt).build();
      })
      .collect(toList());
    SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
    executor.setConcurrencyLimit(MAX_PARALLEL_CONCURRENCY);
    // children of a fan-out stage should be executed in parallel
    FlowBuilder<Flow> parallelFlowBuilder = flowBuilder(format("ParallelChildren.%s", stage.getId()));
    parallelFlowBuilder
      .start(new SimpleFlow("NoOp"))
      .split(executor)
      .add(flows.toArray(new Flow[flows.size()]));
    return flow.next(parallelFlowBuilder.build());
  }

  private <E extends Execution<E>> Flow buildUpstreamJoin(Stage<E> stage) {
    if (stage.getRequisiteStageRefIds().size() > 1) {
      // multi parent child, insert an artificial join stage that will wait for all parents to complete
      Stage<E> waitForStage = newStage(
        stage.getExecution(),
        WaitForRequisiteCompletionStage.PIPELINE_CONFIG_TYPE,
        "Wait For Parent Tasks",
        newHashMap(singletonMap("requisiteIds", stage.getRequisiteStageRefIds())),
        null,
        null
      );
      ((AbstractStage) waitForStage).setId(format("%s-waitForRequisite", stage.getId()));
      waitForStage.setRequisiteStageRefIds(emptyList());
      planStage(waitForStage);

      int stageIdx = stage.getExecution().getStages().indexOf(stage);
      stage.getExecution().getStages().add(stageIdx, waitForStage);

      FlowBuilder<Flow> waitForFlow = flowBuilder(format("WaitForRequisite.%s.%s", stage.getRefId(), stage.getId()));

      // child stage should be added after the artificial join stage
      return addStepsToFlow(waitForFlow, waitForStage)
        .next(buildStepsForStage(stage)).build();
    } else {
      return buildStepsForStage(stage);
    }
  }

  private <E extends Execution<E>> Flow buildStepsForStage(Stage<E> stage) {
    List<Stage<E>> aroundStages = stage
      .getExecution()
      .getStages()
      .stream()
      .filter(it -> stage.getId().equals(it.getParentStageId()))
      .collect(toList());
    final FlowBuilder<Flow> subFlow = flowBuilder(format("ChildExecution.%s.%s", stage.getRefId(), stage.getId()));
    aroundStages
      .stream()
      .filter(it -> it.getSyntheticStageOwner() == STAGE_BEFORE)
      .forEach(it -> addStepsToFlow(subFlow, it));
    addStepsToFlow(subFlow, stage);
    aroundStages
      .stream()
      .filter(it -> it.getSyntheticStageOwner() == STAGE_AFTER)
      .forEach(it -> addStepsToFlow(subFlow, it));
    return subFlow.build();
  }

  private <E extends Execution<E>, Q> FlowBuilder<Q> addStepsToFlow(FlowBuilder<Q> flow, Stage<E> stage) {
    for (Task task : stage.getTasks()) {
      flow = flow.next(buildStepForTask(stage, task));
    }
    return flow;
  }

  private <E extends Execution<E>> Step buildStepForTask(Stage<E> stage, Task task) {
    TaskletStepBuilder stepBuilder = steps
      .get(stepName(stage, task))
      .tasklet(buildTaskletForTask(task));

    executionListenerProvider
      .allStepExecutionListeners()
      .forEach(stepBuilder::listener);

    return stepBuilder.build();
  }

  private <E extends Execution<E>> String stepName(Stage<E> stage, Task task) {
    return format("%s.%s.%s.%s", stage.getId(), stage.getType(), task.getName(), task.getId());
  }

  private Tasklet buildTaskletForTask(Task task) {
    return taskTaskletAdapter.decorate(tasks.get(task.getImplementingClass()));
  }

  private <E extends Execution> String jobNameFor(E execution) {
    return format("%s:%s:%s", execution.getClass().getSimpleName(), execution.getApplication(), execution.getId());
  }

  private static <Q> FlowBuilder<Q> flowBuilder(String name) {
    return new FlowBuilderWrapper<>(name);
  }

  /**
   * This just extends {@link FlowBuilder} to allow you to call {@link #next}
   * all the time instead of having to remember to call {@link #from} the first
   * time.
   */
  private static class FlowBuilderWrapper<Q> extends FlowBuilder<Q> {

    private boolean empty = true;

    FlowBuilderWrapper(String name) {
      super(name);
    }

    @Override public FlowBuilderWrapper<Q> next(Flow flow) {
      if (empty) {
        from(flow);
        empty = false;
      } else {
        super.next(flow);
      }
      return this;
    }

    @Override public FlowBuilderWrapper<Q> next(Step step) {
      if (empty) {
        from(step);
        empty = false;
      } else {
        super.next(step);
      }
      return this;
    }
  }
}
