package com.netflix.spinnaker.orca.locks;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.Task;
import com.netflix.spinnaker.orca.TaskExecutionInterceptor;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.pipeline.AcquireLockStage;
import com.netflix.spinnaker.orca.pipeline.ReleaseLockStage;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.tasks.AcquireLockTask;
import com.netflix.spinnaker.orca.pipeline.tasks.ReleaseLockTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class LockExtendingTaskExecutionInterceptor implements TaskExecutionInterceptor {
  private static final int TTL_EXTEND = 60;
  private final LockManager lockManager;

  @Autowired
  public LockExtendingTaskExecutionInterceptor(LockManager lockManager) {
    this.lockManager = lockManager;
  }

  @Override
  public Stage beforeTaskExecution(Task task, Stage stage) {
    extendLocks(task, stage);
    return stage;
  }

  @Override
  public TaskResult afterTaskExecution(Task task, Stage stage, TaskResult taskResult) {
    extendLocks(task, stage);
    return taskResult;
  }

  private void extendLocks(Task task, Stage stage) {
    if (task instanceof AcquireLockTask || task instanceof ReleaseLockTask) {
      return;
    }

    Map<HeldLock, AtomicInteger> heldLocks = new HashMap<>();
    Set<String> lockTypes = new HashSet<>(Arrays.asList(AcquireLockStage.PIPELINE_TYPE, ReleaseLockStage.PIPELINE_TYPE));
    stage.getExecution().getStages()
      .stream()
      .filter(s -> lockTypes.contains(s.getType()) && s.getStatus() == ExecutionStatus.SUCCEEDED)
      .forEach(s -> {
      LockContext lc = s.mapTo("/lock", LockContext.class);
      AtomicInteger count = heldLocks.computeIfAbsent(new HeldLock(lc.getLockName(), lc.getLockValueApplication(), lc.getLockValueType(), lc.getLockValue()), hl -> new AtomicInteger(0));
      if (AcquireLockStage.PIPELINE_TYPE.equals(s.getType())) {
        count.incrementAndGet();
      } else {
        count.decrementAndGet();
      }
    });

    List<HeldLock> toExtend = heldLocks.entrySet().stream().filter(me -> me.getValue().get() > 0).map(Map.Entry::getKey).collect(Collectors.toList());
    toExtend.forEach(hl -> lockManager.extendLock(hl.lockName, hl.lockValueApplication, hl.lockValueType, hl.lockValue, TTL_EXTEND));
  }

  private static class HeldLock {
    final String lockName;
    final String lockValueApplication;
    final String lockValueType;
    final String lockValue;

    HeldLock(String lockName, String lockValueApplication, String lockValueType, String lockValue) {
      this.lockName = lockName;
      this.lockValueApplication = lockValueApplication;
      this.lockValueType = lockValueType;
      this.lockValue = lockValue;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      HeldLock heldLock = (HeldLock) o;

      if (!lockName.equals(heldLock.lockName)) return false;
      if (!lockValueApplication.equals(heldLock.lockValueApplication)) return false;
      if (!lockValueType.equals(heldLock.lockValueType)) return false;
      return lockValue.equals(heldLock.lockValue);
    }

    @Override
    public int hashCode() {
      int result = lockName.hashCode();
      result = 31 * result + lockValueApplication.hashCode();
      result = 31 * result + lockValueType.hashCode();
      result = 31 * result + lockValue.hashCode();
      return result;
    }

    @Override
    public String toString() {
      return "HeldLock{" +
        "lockName='" + lockName + '\'' +
        ", lockValueApplication='" + lockValueApplication + '\'' +
        ", lockValueType='" + lockValueType + '\'' +
        ", lockValue='" + lockValue + '\'' +
        '}';
    }
  }
}
