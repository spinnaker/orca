package com.netflix.spinnaker.orca.pipeline.tasks;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.exceptions.DefaultExceptionHandler;
import com.netflix.spinnaker.orca.exceptions.ExceptionHandler;
import com.netflix.spinnaker.orca.locks.LockFailureException;
import com.netflix.spinnaker.orca.locks.LockManager;
import com.netflix.spinnaker.orca.locks.LockContext;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

@Component
public class AcquireLockTask implements Task {

  private final LockManager lockManager;

  @Autowired
  public AcquireLockTask(LockManager lockManager) {
    this.lockManager = lockManager;
  }

  @Nonnull
  @Override
  public TaskResult execute(@Nonnull Stage stage) {
    LockContext lock = stage.mapTo("/lock", LockContext.class);
    String lockHolder = getLockHolder(lock, stage);
    try {
      lockManager.acquireLock(lock.getLockName(), lock.getLockValueApplication(), lock.getLockValueType(), lock.getLockValue(), lockHolder, lock.getTtl());
      return TaskResult.SUCCEEDED;
    } catch (LockFailureException lfe) {
      Map<String, Object> resultContext = new HashMap<>();
      ExceptionHandler.Response exResult = new DefaultExceptionHandler().handle("acquireLock", lfe);
      exResult.getDetails().put("lockName", lfe.getLockName());
      exResult.getDetails().put("currentLockValueApplication", lfe.getCurrentLockValueApplication());
      exResult.getDetails().put("currentLockValueType", lfe.getCurrentLockValueType());
      exResult.getDetails().put("currentLockValue", lfe.getCurrentLockValue());
      resultContext.put("exception", exResult);
      return new TaskResult(ExecutionStatus.TERMINAL, resultContext);
    }
  }

  @Nonnull
  protected String getLockHolder(LockContext lock, @Nonnull Stage stage) {
    //TODO(cfieber):
    //lockHolder - if we are a synthetic stage then use parent stage's id
    // if we are a top level stage, lock with our id
    // the corresponding release lock stage should have an explicit stage id in it..
    return lock.getLockHolder().orElse(stage.getId());
  }
}
