package com.netflix.spinnaker.orca.pipeline.tasks;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.locks.LockContext;
import com.netflix.spinnaker.orca.pipeline.AcquireLockStage;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.StageNavigator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@Component
public class DetermineLockTask implements Task {

  private final StageNavigator stageNavigator;

  @Autowired
  public DetermineLockTask(StageNavigator stageNavigator) {
    this.stageNavigator = stageNavigator;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    Map<String, Object> lock = (Map<String, Object>) stage.getContext().get("lock");
    if (lock != null && lock.containsKey("lockValue")) {
      return TaskResult.SUCCEEDED;
    }

    Optional<StageNavigator.Result> lockStageResult = stageNavigator.ancestors(stage).stream().filter(r -> r.getStageBuilder() instanceof AcquireLockStage).findFirst();

    if (lockStageResult.isPresent()) {
      final Stage lockStage = lockStageResult.get().getStage();
      LockContext lockContext = lockStage.mapTo("/lock", LockContext.LockContextBuilder.class).withStage(lockStage).build();
      return new TaskResult(ExecutionStatus.SUCCEEDED, Collections.singletonMap("lock", lockContext));
    }

    throw new IllegalStateException("Unable to determine parent lock stage");
  }
}
