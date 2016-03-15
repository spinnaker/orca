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

package com.netflix.spinnaker.orca.actorsystem.task;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.SupervisorStrategy.Stop$;
import akka.cluster.sharding.ShardRegion.Passivate;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.japi.pf.ReceiveBuilder;
import akka.persistence.AbstractPersistentActor;
import akka.persistence.RecoveryCompleted;
import com.netflix.spinnaker.orca.*;
import com.netflix.spinnaker.orca.actorsystem.stage.StageMessage;
import com.netflix.spinnaker.orca.actorsystem.stage.StageMessage.TaskComplete;
import com.netflix.spinnaker.orca.actorsystem.stage.StageMessage.TaskIncomplete;
import com.netflix.spinnaker.orca.actorsystem.task.TaskMessage.ExecuteTask;
import com.netflix.spinnaker.orca.actorsystem.task.TaskMessage.GetTaskStatus;
import com.netflix.spinnaker.orca.actorsystem.task.TaskMessage.RequestTask;
import com.netflix.spinnaker.orca.actorsystem.task.TaskMessage.ResumeTask;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import org.springframework.context.ApplicationContext;
import scala.PartialFunction;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;
import scala.runtime.BoxedUnit;
import static akka.actor.ActorRef.noSender;
import static com.netflix.spinnaker.orca.ExecutionStatus.*;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class TaskActor extends AbstractPersistentActor {

  private final LoggingAdapter log = Logging.getLogger(getContext().system(), this);

  public static final FiniteDuration DEFAULT_BACKOFF_PERIOD = Duration.create(100, MILLISECONDS);

  private final ApplicationContext applicationContext;
  private final ExecutionRepository executionRepository;
  private final Function<ActorContext, ActorRef> stageActor;

  private TaskId identifier;
  private Task task;
  private ExecutionStatus lastStatus;

  public TaskActor(
    ApplicationContext applicationContext,
    ExecutionRepository executionRepository,
    Function<ActorContext, ActorRef> stageActor) {
    this.applicationContext = applicationContext;
    this.executionRepository = executionRepository;
    this.stageActor = stageActor;
  }

  @Override
  public String persistenceId() {
    return format("Task-%s", self().path().name());
  }

  @Override
  public PartialFunction<Object, BoxedUnit> receiveCommand() {
    return ReceiveBuilder
      .match(GetTaskStatus.class, this::getTaskStatus)
      .match(RequestTask.class, this::onTaskRequested)
      .match(ExecuteTask.class, command -> runTask())
      .match(ResumeTask.class, command -> log.debug("Waking up"))
      .match(Stop$.class, this::onShutdown)
      .build();
  }

  @Override
  public PartialFunction<Object, BoxedUnit> receiveRecover() {
    return ReceiveBuilder
      .match(TaskRequested.class, this::updateState)
      .match(TaskStatusUpdated.class, this::updateStatus)
      .match(RecoveryCompleted.class, this::onRecoveryCompleted)
      .build();
  }

  private void getTaskStatus(GetTaskStatus command) {
    log.info("I was asked for the task status and it is {}", lastStatus);
    sender().tell(lastStatus, noSender());
    if (lastStatus.isComplete()) {
      log.info("I was probably woken up just to do this, dying again");
      shutdown();
    }
  }

  private void runTask() {
    Stage stage = getStage();
    com.netflix.spinnaker.orca.pipeline.model.Task taskModel = getTask(stage);
    if (stage.getExecution().isCanceled()) {
      onExecutionCanceled();
    } else if (taskModel.getStatus().isComplete() || taskModel.getStatus().isHalt()) {
      skipTask(taskModel.getStatus());
    } else {
      try {
        TaskResult result = task.execute(stage);
        result = applyStageStatusOverrides(stage, result);
        if (result.getStatus() == TERMINAL) {
          executionRepository.cancel(identifier.executionId);
        }
        log.debug("Task {} executed with result {}", taskModel.getName(), result.getStatus());
        stage.getContext().putAll(result.getStageOutputs());
        if (result.getStatus().isComplete()) {
          onTaskComplete(result);
        } else {
          onTaskIncomplete(result);
        }
      } finally {
        log.info("Updating stage ");
        executionRepository.storeStage(stage);
      }
    }
  }

  private void onRecoveryCompleted(RecoveryCompleted event) {
    if (identifier != null) {
      log.debug("Recovery completed with task id {}", identifier.taskId);
      // TODO: only if appropriate
      context().parent().tell(new ExecuteTask(identifier), noSender());
    } else {
      log.debug("Recovery completed but no task id");
      shutdown();
    }
  }

  private void onTaskRequested(RequestTask command) {
    if (identifier == null) {
      persist(new TaskRequested(command.id(), command.type()), event -> {
        updateState(event);
        runTask();
      });
    } else {
      log.info("Task {} was already requested", identifier);
    }
  }

  private void updateState(TaskRequested event) {
    identifier = event.id;
    task = applicationContext.getBean(event.type);
    lastStatus = NOT_STARTED;
  }

  private void updateStatus(TaskStatusUpdated event) {
    log.info("Updating last status to {}", event.status);
    lastStatus = event.status;
  }

  private void onTaskIncomplete(TaskResult result) {
    persist(new TaskStatusUpdated(result.getStatus()), event -> {
      updateStatus(event);
      ActorRef actorRef = stageActor();
      log.info("Notifying {} that task is not yet complete", actorRef);
      actorRef.tell(new TaskIncomplete(identifier.stage(), result.getStatus()), noSender());
      retry();
    });
  }

  private void onTaskComplete(TaskResult result) {
    persist(new TaskStatusUpdated(result.getStatus()), event -> {
      updateStatus(event);
      ActorRef actorRef = stageActor();
      log.info("Notifying {} that task is complete", actorRef);
      actorRef.tell(new TaskComplete(identifier.stage(), result.getStatus()), noSender());
      shutdown();
    });
  }

  private void skipTask(ExecutionStatus taskStatus) {
    persist(new TaskStatusUpdated(taskStatus), event -> {
      updateStatus(event);
      ActorRef actorRef = stageActor();
      log.info("Notifying {} that task is being skipped", actorRef);
      actorRef.tell(new StageMessage.TaskSkipped(identifier.stage(), taskStatus), noSender());
      shutdown();
    });
  }

  private void onExecutionCanceled() {
    persist(new TaskStatusUpdated(CANCELED), event -> {
      updateStatus(event);
      ActorRef actorRef = stageActor();
      log.info("Notifying {} that task is canceled", actorRef);
      actorRef.tell(new StageMessage.TaskCancelled(identifier.stage()), noSender());
      shutdown();
    });
  }

  private void retry() {
    context()
      .system()
      .scheduler()
      .scheduleOnce(backoffPeriod(), context().parent(), new ExecuteTask(identifier), context().dispatcher(), sender());
  }

  private void shutdown() {
    log.debug("Stopping {}", self().path().name());
    context().parent().tell(new Passivate(Stop$.MODULE$), self());
  }

  private void onShutdown(Stop$ command) {
    log.debug("Stopped {}", self().path().name());
    context().stop(self());
  }

  private FiniteDuration backoffPeriod() {
    return task instanceof RetryableTask
      ? Duration.create(((RetryableTask) task).getBackoffPeriod(), MILLISECONDS)
      : DEFAULT_BACKOFF_PERIOD;
  }

  private TaskResult applyStageStatusOverrides(Stage stage, TaskResult result) {
    if (result.getStatus() == TERMINAL) {
      Boolean shouldFailPipeline = Boolean.valueOf(stage.getContext().getOrDefault("failPipeline", "true").toString());
      if (!shouldFailPipeline) {
        return new DefaultTaskResult(STOPPED, result.getStageOutputs(), result.getGlobalOutputs());
      } else {
        return result;
      }
    }
    return result;
  }

  private Execution<? extends Execution> getExecution() {
    try {
      return executionRepository
        .retrievePipeline(identifier.executionId);
    } catch (ExecutionNotFoundException e) { // TODO: not this
      return executionRepository
        .retrieveOrchestration(identifier.executionId);
    }
  }

  private Stage getStage() {
    return getExecution()
      .getStages()
      .stream()
      .filter(stage -> stage.getId().equals(identifier.stageId))
      .findFirst()
      .orElseThrow(this::noSuchStage);
  }

  private com.netflix.spinnaker.orca.pipeline.model.Task getTask(Stage stage) {
    return getTasks(stage)
      .stream()
      .filter((com.netflix.spinnaker.orca.pipeline.model.Task it) -> it.getId().equals(identifier.taskId))
      .findFirst()
      .orElseThrow(this::noSuchTask);
  }

  @SuppressWarnings("unchecked")
  private List<com.netflix.spinnaker.orca.pipeline.model.Task> getTasks(Stage stage) {
    return stage.getTasks();
  }

  private ActorRef stageActor() {
    return stageActor.apply(context());
  }

  private RuntimeException noSuchStage() {
    return new IllegalArgumentException(
      format("Stage %s not found in pipeline %s", identifier.stageId, identifier.executionId)
    );
  }

  private RuntimeException noSuchTask() {
    return new IllegalStateException(
      format("Task with id %s not found in stage %s of execution %s", identifier.taskId, identifier.stageId, identifier.executionId)
    );
  }

  private interface Event extends Serializable {
  }

  private static class TaskRequested implements Event {
    private final TaskId id;
    private final Class<? extends Task> type;

    private TaskRequested(TaskId id, Class<? extends Task> type) {
      this.id = id;
      this.type = type;
    }

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      TaskRequested that = (TaskRequested) o;
      return Objects.equals(id, that.id) &&
        Objects.equals(type, that.type);
    }

    @Override public int hashCode() {
      return Objects.hash(id, type);
    }
  }

  private static class TaskStatusUpdated implements Event {
    private final ExecutionStatus status;

    private TaskStatusUpdated(ExecutionStatus status) {
      this.status = status;
    }

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      TaskStatusUpdated that = (TaskStatusUpdated) o;
      return status == that.status;
    }

    @Override public int hashCode() {
      return Objects.hash(status);
    }
  }
}
