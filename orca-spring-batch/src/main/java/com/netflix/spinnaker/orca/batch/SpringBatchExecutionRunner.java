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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import com.netflix.spinnaker.orca.pipeline.ExecutionRunnerSupport;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.Task;
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
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.batch.core.step.tasklet.Tasklet;
import static java.lang.String.format;

public class SpringBatchExecutionRunner extends ExecutionRunnerSupport {

  private final JobLauncher jobLauncher;
  private final JobRegistry jobRegistry;
  private final JobBuilderFactory jobs;
  private final StepBuilderFactory steps;
  private final TaskTaskletAdapter taskTaskletAdapter;
  private final Map<String, com.netflix.spinnaker.orca.Task> tasks;

  public SpringBatchExecutionRunner(
    Collection<StageDefinitionBuilder> stageDefinitionBuilders,
    JobLauncher jobLauncher,
    JobRegistry jobRegistry,
    JobBuilderFactory jobs,
    StepBuilderFactory steps,
    TaskTaskletAdapter taskTaskletAdapter,
    Map<String, com.netflix.spinnaker.orca.Task> tasks) {
    super(stageDefinitionBuilders);
    this.jobLauncher = jobLauncher;
    this.jobRegistry = jobRegistry;
    this.jobs = jobs;
    this.steps = steps;
    this.taskTaskletAdapter = taskTaskletAdapter;
    this.tasks = tasks;
  }

  @Override public <T extends Execution> void start(T execution) throws Exception {
    super.start(execution);

    Job job = createJob(execution);
    jobLauncher.run(job, createJobParameters(execution));
  }

  private <E extends Execution> JobParameters createJobParameters(E subject) {
    JobParametersBuilder params = new JobParametersBuilder();
    params.addString("pipeline", subject.getId());
//    params.addString("name", subject.getName());
    params.addString("application", subject.getApplication());
    params.addString("timestamp", String.valueOf(System.currentTimeMillis()));
    return params.toJobParameters();
  }

  private <E extends Execution> Job createJob(E execution) throws NoSuchJobException, DuplicateJobException {
    String jobName = jobNameFor(execution);
    if (!jobRegistry.getJobNames().contains(jobName)) {
      Job job = buildStepsForExecution(jobs.get(jobName), execution)
        .build()
        .build();
      jobRegistry.register(new ReferenceJobFactory(job));
    }
    return jobRegistry.getJob(jobName);
  }

  private <E extends Execution> FlowBuilder<FlowJobBuilder> buildStepsForExecution(JobBuilder builder, E execution) {
    @SuppressWarnings("unchecked") List<Stage<E>> stages = execution.getStages();
    FlowBuilder<FlowJobBuilder> flow = builder.flow(initStep());
    for (Stage<E> stage : stages) {
      flow = buildStepsForStage(flow, stage);
    }
    return flow;
  }

  private Step initStep() {
    return steps.get("orca-init-step")
      .tasklet(new PipelineInitializerTasklet())
      .build();
  }

  private <E extends Execution> FlowBuilder<FlowJobBuilder> buildStepsForStage(FlowBuilder<FlowJobBuilder> flow, Stage<E> stage) {
    for (Task task : stage.getTasks()) {
      flow = buildStepForTask(flow, stage, task);
    }
    return flow;
  }

  private <E extends Execution> FlowBuilder<FlowJobBuilder> buildStepForTask(FlowBuilder<FlowJobBuilder> flow, Stage<E> stage, Task task) {
    Step step = steps.get(stepName(stage, task)).tasklet(buildTaskletForTask(task)).build();
    return flow.next(step);
  }

  private <E extends Execution> String stepName(Stage<E> stage, Task task) {
    return format("%s.%s.%s.%s", stage.getId(), stage.getType(), task.getName(), task.getId());
  }

  private Tasklet buildTaskletForTask(Task task) {
    return taskTaskletAdapter.decorate(tasks.get(task.getName()));
  }

  private <E extends Execution> String jobNameFor(E execution) {
    return format("%s:%s:%s", execution.getClass().getSimpleName(), execution.getApplication(), execution.getId());
  }
}
