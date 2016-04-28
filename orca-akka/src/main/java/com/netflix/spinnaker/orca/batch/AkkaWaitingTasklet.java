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

import java.util.Map;
import akka.actor.ActorRef;
import akka.cluster.sharding.ClusterSharding;
import akka.pattern.AskTimeoutException;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.actorsystem.task.TaskActor;
import com.netflix.spinnaker.orca.actorsystem.task.TaskMessage.GetTaskStatus;
import com.netflix.spinnaker.orca.actorsystem.task.TaskMessage.RequestTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import static akka.actor.ActorRef.noSender;
import static akka.pattern.Patterns.ask;
import static com.netflix.spinnaker.orca.ExecutionStatus.TERMINAL;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.springframework.batch.repeat.RepeatStatus.CONTINUABLE;

@Component
public class AkkaWaitingTasklet implements Tasklet {

  private final Logger log = LoggerFactory.getLogger(AkkaWaitingTasklet.class);

  private final ClusterSharding cluster;
  private final Class<? extends Task> taskType;

  private boolean started = false;

  @Autowired
  public AkkaWaitingTasklet(ClusterSharding cluster, Class<? extends Task> taskType) {
    this.cluster = cluster;
    this.taskType = taskType;
  }

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
    String executionId = executionId(chunkContext);
    String stageId = stageId(chunkContext);
    String taskId = taskId(chunkContext);

    log.info("Running {} with Akka", taskType.getSimpleName());
    ActorRef region = cluster.shardRegion(TaskActor.class.getSimpleName());

    if (!started) {
      log.info("Requesting {} on {}", taskType.getSimpleName(), region);
      region.tell(new RequestTask(taskType, executionId, stageId, taskId), noSender());
      started = true;
      Thread.sleep(1000);
      return CONTINUABLE;
    } else {
      log.info("Checking status of {}", taskType.getSimpleName());
      Duration timeout = Duration.create(5, SECONDS);
      Future<Object> future = ask(region, new GetTaskStatus(executionId, stageId, taskId), timeout.toMillis());
      try {
        log.info("Waiting for result");
        ExecutionStatus result = (ExecutionStatus) Await.result(future, timeout);
        log.info("Actor responded with task status {}", result);
        if (result.getComplete()) {
          return taskToBatchStatus(contribution, chunkContext, result);
        } else {
          Thread.sleep(1000);
          return taskToBatchStatus(contribution, chunkContext, result);
        }
      } catch (AskTimeoutException e) {
        log.error("Timed out waiting for actor to report task status");
        return taskToBatchStatus(contribution, chunkContext, TERMINAL);
      }
    }
  }

  private static String executionType(ChunkContext chunkContext) {
    Map<String, Object> parameters = chunkContext.getStepContext().getJobParameters();
    return parameters.containsKey("pipeline") ? "pipeline" : "orchestration";
  }

  private static String executionId(ChunkContext chunkContext) {
    Map<String, Object> parameters = chunkContext.getStepContext().getJobParameters();
    return (String) parameters.get(executionType(chunkContext));
  }

  private static String stageId(ChunkContext chunkContext) {
    return chunkContext.getStepContext().getStepName().split("\\.")[0];
  }

  private static String taskId(ChunkContext chunkContext) {
    return chunkContext.getStepContext().getStepName().split("\\.")[3];
  }

  private RepeatStatus taskToBatchStatus(StepContribution contribution, ChunkContext chunkContext, ExecutionStatus taskStatus) {
    BatchStepStatus batchStepStatus = BatchStepStatus.mapResult(taskStatus);
    StepExecution step = chunkContext.getStepContext().getStepExecution();
    step.getExecutionContext().put("orcaTaskStatus", taskStatus);
    step.setStatus(batchStepStatus.getBatchStatus());
    step.getJobExecution().setStatus(batchStepStatus.getBatchStatus());
    contribution.setExitStatus(batchStepStatus.getExitStatus());
    return batchStepStatus.getRepeatStatus();
  }
}
