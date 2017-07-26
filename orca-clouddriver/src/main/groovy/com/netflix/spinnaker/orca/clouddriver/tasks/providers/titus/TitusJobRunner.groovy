/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.titus;

import com.netflix.spinnaker.orca.clouddriver.tasks.job.JobRunner
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class TitusJobRunner implements JobRunner {

  boolean katoResultExpected = false
  String cloudProvider = "titus"

  @Override
  List<Map> getOperations(Stage stage) {
    def operation = [:]
    operation.putAll(stage.context)
    if (stage.context.containsKey("cluster")) {
      operation.putAll(stage.context.cluster as Map)
    }
    operation.put('cloudProvider', cloudProvider)

    if (stage.execution.authentication?.user) {
      operation.put('user', stage.execution.authentication?.user)
    }

    return [[(OPERATION): operation]]
  }
}

