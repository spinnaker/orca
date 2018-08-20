package com.netflix.spinnaker.orca;

import com.netflix.spinnaker.orca.pipeline.model.Stage;

/**
 * TaskExecutionInterceptor is a hook point to customize the specific execution of a task.
 *
 * Before execute is called on a Task, all TaskExecutionInterceptors will be called. The resulting
 * Stage object from beforeTaskExecution is passed to subsequent invocations of TaskExecutionInterceptor
 * and then used for the invocation of execute on Task.
 *
 * After a Task completes with a TaskResult, all TaskExecutionInterceptors are called. The resulting
 * TaskResult is passed to subsequent invocations ot TaskExecutionInterceptor and the final TaskResult
 * is used as the output of the task.
 *
 * A TaskExecutionInterceptor can specify the maximum backoff that should be allowed. As an example, the
 * LockExtendingTaskExecutionInterceptor needs to ensure that a task doesn't delay longer than the
 * lock extension. The minimum maxTaskBackoff among all registered TaskExecutionInterceptors will be used
 * to constrain the task backoff supplied by a RetryableTask.
 */
public interface TaskExecutionInterceptor {

  default long maxTaskBackoff() { return Long.MAX_VALUE; }

  default Stage beforeTaskExecution(Task task, Stage stage) {
    return stage;
  }

  default TaskResult afterTaskExecution(Task task, Stage stage, TaskResult taskResult) {
    return taskResult;
  }
}
