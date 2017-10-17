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
 */
public interface TaskExecutionInterceptor {

  default Stage beforeTaskExecution(Task task, Stage stage) {
    return stage;
  }

  default TaskResult afterTaskExecution(Task task, Stage stage, TaskResult taskResult) {
    return taskResult;
  }
}
