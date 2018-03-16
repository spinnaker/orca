package com.netflix.spinnaker.orca.clouddriver.tasks.providers.helm

import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class HelmServerGroupCreator implements ServerGroupCreator {

  boolean katoResultExpected = false
  String cloudProvider = "helm"

  @Override
  List<Map> getOperations(Stage stage) {
    def operation = [:]

    if (stage.context.containsKey("cluster")) {
      operation.putAll(stage.context.cluster as Map)
    } else {
      operation.putAll(stage.context)
    }

    return [[(OPERATION): operation]]
  }

  @Override
  Optional<String> getHealthProviderName() {
    return Optional.empty()
  }
}
