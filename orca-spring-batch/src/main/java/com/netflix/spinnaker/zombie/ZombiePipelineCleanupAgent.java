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

package com.netflix.spinnaker.zombie;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import com.netflix.spinnaker.orca.RetryableTask;
import com.netflix.spinnaker.orca.pipeline.model.Pipeline;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.Task;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rx.Observable;
import static com.netflix.spinnaker.orca.ExecutionStatus.CANCELED;
import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING;
import static java.lang.String.format;
import static java.time.Clock.systemDefaultZone;
import static java.time.Duration.ZERO;
import static java.time.Duration.between;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;
import static rx.Observable.just;

/**
 * This poller cancels any pipelines that appear to be still running but are
 * more than {@link #TOLERANCE} overdue.
 */
@Component
@ConditionalOnExpression("${pollers.zombiePipeline.enabled:true}")
@Slf4j
public class ZombiePipelineCleanupAgent {

  /**
   * How often the agent runs.
   */
  private static final long FREQUENCY_MS = 21600000; // 6 hours

  /**
   * Delay before running the agent the first time.
   */
  private static final long INITIAL_DELAY_MS = 300000; // 5 minutes

  private final ExecutionRepository repository;
  private final Clock clock;
  private final ApplicationContext applicationContext;
  private final Lock lock;
  private final String currentInstanceId;

  /**
   * Duration a task can be overdue before we decide to kill it.
   */
  static final Duration TOLERANCE = Duration.ofMinutes(30);

  /**
   * Duration a non-retryable task can run before being considered overdue.
   * {@link #TOLERANCE} is added on.
   */
  static final Duration DEFAULT_TASK_TIMEOUT = Duration.ofMinutes(2);

  @Autowired
  public ZombiePipelineCleanupAgent(ExecutionRepository repository,
                                    ApplicationContext applicationContext,
                                    Lock lock,
                                    String currentInstanceId) {
    this(
      repository,
      applicationContext,
      lock,
      currentInstanceId,
      systemDefaultZone()
    );
  }

  ZombiePipelineCleanupAgent(ExecutionRepository repository,
                             ApplicationContext applicationContext,
                             Lock lock,
                             String currentInstanceId,
                             Clock clock) {
    this.repository = repository;
    this.clock = clock;
    this.applicationContext = applicationContext;
    this.currentInstanceId = currentInstanceId;
    this.lock = lock;
  }

  @Scheduled(fixedDelay = FREQUENCY_MS, initialDelay = INITIAL_DELAY_MS)
  public void slayZombies() {
    lock
      .withLock(
        format("%s.%d", currentInstanceId, clock.millis()),
        this::findZombies,
        this::slayZombie
      );
  }

  private Observable<Pipeline> findZombies() {
    log.info("Starting sweep for zombie pipelines...");
    return repository
      .retrievePipelines()
      .filter(this::isV2)
      .filter(this::isIncomplete)
      .filter(this::hasZombieTask)
      .doOnCompleted(() -> log.info("Zombie pipeline sweep completed."));
  }

  private void slayZombie(Pipeline pipeline) {
    log.warn(
      "Canceling zombie pipeline {} for {} started at {} with id {}",
      pipeline.getName(),
      pipeline.getApplication(),
      formatStartTime(pipeline),
      pipeline.getId()
    );
    repository.cancel(pipeline.getId(), "Spinnaker", "The pipeline appeared to be stalled.");
    repository.updateStatus(pipeline.getId(), CANCELED);
    Observable
      .from(pipeline.getStages())
      .filter(stage -> stage.getStatus() == RUNNING)
      .subscribe(stage -> {
        stage.setStatus(CANCELED);
        repository.storeStage(stage);
      });
  }

  private String formatStartTime(Pipeline pipeline) {
    if (pipeline.getStartTime() == null) {
      return null;
    } else {
      return ISO_LOCAL_DATE_TIME
        .format(Instant.ofEpochMilli(pipeline.getStartTime())
          .atZone(ZoneId.systemDefault()));
    }
  }

  private boolean hasZombieTask(Pipeline pipeline) {
    return just(pipeline)
      .flatMapIterable(Pipeline::getStages)
      .flatMapIterable(Stage::getTasks)
      .filter(this::isIncomplete)
      .exists((task) -> isZombie(pipeline, task))
      .toBlocking()
      .first();
  }

  private boolean isV2(Pipeline pipeline) {
    return pipeline.getExecutionEngine().equals("v2");
  }

  private boolean isIncomplete(Pipeline pipeline) {
    return !pipeline.getStatus().isComplete();
  }

  private boolean isIncomplete(Task task) {
    return !task.getStatus().isComplete();
  }

  private boolean isZombie(Pipeline pipeline, Task task) {
    if (task.getStatus() == RUNNING) {
      Duration elapsed = between(Instant.ofEpochMilli(task.getStartTime()), clock.instant());
      Duration zombieThreshold = maxElapsedTimeFor(task).plus(TOLERANCE);
      Duration pausedTime = timeSpentPaused(pipeline);
      return elapsed.minus(pausedTime).compareTo(zombieThreshold) > 0;
    } else {
      // We should ignore currently PAUSED tasks or anything already complete
      return false;
    }
  }

  private Duration maxElapsedTimeFor(Task task) {
    com.netflix.spinnaker.orca.Task taskBean = taskBean(task);
    if (taskBean instanceof RetryableTask) {
      return Duration.ofMillis(((RetryableTask) taskBean).getTimeout());
    } else {
      return DEFAULT_TASK_TIMEOUT;
    }
  }

  private Duration timeSpentPaused(Pipeline pipeline) {
    if (pipeline.getPaused() == null) {
      return ZERO;
    }
    return Duration.ofMillis(pipeline.getPaused().getPausedMs());
  }

  private com.netflix.spinnaker.orca.Task taskBean(Task task) {
    return applicationContext.getBean(task.getImplementingClass());
  }
}
