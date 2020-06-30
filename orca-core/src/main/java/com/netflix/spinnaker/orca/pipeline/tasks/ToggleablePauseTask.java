package com.netflix.spinnaker.orca.pipeline.tasks;

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import java.time.Duration;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * A task that just waits indefinitely based on a feature toggle specified in the `pauseToggle`
 * property of the stage context. Useful for pausing stages as part of migrations, for instance.
 */
@Component
public class ToggleablePauseTask implements RetryableTask {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private DynamicConfigService dynamicConfigService;

  @Autowired
  public ToggleablePauseTask(DynamicConfigService dynamicConfigService) {
    this.dynamicConfigService = dynamicConfigService;
  }

  @Override
  @Nonnull
  public TaskResult execute(@Nonnull final StageExecution stage) {
    final String pauseToggle =
        stage.getContext().containsKey("pauseToggle")
            ? stage.getContext().get("pauseToggle").toString()
            : null;

    if (pauseToggle != null && dynamicConfigService.isEnabled(pauseToggle, false)) {
      log.debug(
          "{} stage currently paused based on {} toggle. Waiting...", stage.getName(), pauseToggle);
      return TaskResult.RUNNING;
    } else {
      log.debug(
          "{} stage currently unpaused based on {} toggle. Carrying on...",
          stage.getName(),
          pauseToggle);
      return TaskResult.SUCCEEDED;
    }
  }

  @Override
  public long getBackoffPeriod() {
    return Duration.ofMinutes(1).toMillis();
  }

  @Override
  public long getTimeout() {
    return Long.MAX_VALUE;
  }
}
