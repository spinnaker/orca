package com.netflix.spinnaker.orca.restart

import com.netflix.spinnaker.orca.listeners.ExecutionListener
import com.netflix.spinnaker.orca.listeners.Persister
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import com.netflix.appinfo.InstanceInfo
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Orchestration
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Slf4j
@Order(0)
@Component
@CompileStatic
class ExecutionTracker implements ExecutionListener {

  private final ExecutionRepository executionRepository
  private final InstanceInfo currentInstance

  @Autowired
  ExecutionTracker(ExecutionRepository executionRepository, @Qualifier("instanceInfo") InstanceInfo currentInstance) {
    this.executionRepository = executionRepository
    log.info "current instance: ${currentInstance.appName} ${currentInstance.id}"
    this.currentInstance = currentInstance
  }

  @Override
  void beforeExecution(Persister persister, Execution execution) {
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
}
