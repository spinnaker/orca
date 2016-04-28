package com.netflix.spinnaker.orca.restart

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import com.netflix.appinfo.InstanceInfo
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobExecutionListener
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Slf4j
@Order(0)
@Component
@CompileStatic
class ExecutionTracker implements JobExecutionListener {

  private final ExecutionRepository executionRepository
  private final InstanceInfo currentInstance

  @Autowired
  ExecutionTracker(ExecutionRepository executionRepository, @Qualifier("instanceInfo") InstanceInfo currentInstance) {
    this.executionRepository = executionRepository
    log.info "current instance: ${currentInstance.appName} ${currentInstance.id}"
    this.currentInstance = currentInstance
  }

  void beforeExecution(Execution<? extends Execution> execution) {
    execution.executingInstance = currentInstance.id
    // I really don't want to do this but the craziness of the repository API is too much to deal with today
    switch (execution) {
      case Pipeline:
        executionRepository.store((Pipeline) execution)
        break
      case Orchestration:
        executionRepository.store((Orchestration) execution)
        break
    }
  }

  void afterExecution(Execution<? extends Execution> execution) {
  }

  @Override
  final void beforeJob(JobExecution jobExecution) {
    beforeExecution(currentExecution(jobExecution))
  }

  @Override
  final void afterJob(JobExecution jobExecution) {
    afterExecution(currentExecution(jobExecution))
  }

  private Execution currentExecution(JobExecution jobExecution) {
    if (jobExecution.jobParameters.parameters.containsKey("pipeline")) {
      String id = jobExecution.jobParameters.getString("pipeline")
      executionRepository.retrievePipeline(id)
    } else {
      String id = jobExecution.jobParameters.getString("orchestration")
      executionRepository.retrieveOrchestration(id)
    }
  }
}
