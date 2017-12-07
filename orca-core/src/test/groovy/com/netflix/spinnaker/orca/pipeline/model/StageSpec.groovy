/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.model

import spock.lang.Specification
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage
import static java.util.stream.Collectors.toList

class StageSpec extends Specification {

  def "DAG_ORDER sorts stages with direct relationships"() {
    given:
    def pipeline = pipeline {
      stage {
        refId = "1"
      }
      stage {
        refId = "2"
        requisiteStageRefIds = ["1"]
      }
      stage {
        refId = "3"
        requisiteStageRefIds = ["2"]
      }
    }

    expect:
    with(Stage.topologicalSort(pipeline.stages).collect(toList())) {
      refId == ["1", "2", "3"]
    }
  }

  def "DAG_ORDER sorts stages with fork join topology"() {
    given:
    def pipeline = pipeline {
      stage {
        refId = "1"
      }
      stage {
        refId = "2"
        requisiteStageRefIds = ["1"]
      }
      stage {
        refId = "3"
        requisiteStageRefIds = ["1"]
      }
      stage {
        refId = "4"
        requisiteStageRefIds = ["2", "3"]
      }
    }

    expect:
    with(Stage.topologicalSort(pipeline.stages).collect(toList())) {
      refId.first() == "1"
      refId.last() == "4"
    }
  }

  def "DAG_ORDER sorts stages with isolated branches"() {
    given:
    def pipeline = pipeline {
      stage {
        refId = "1"
      }
      stage {
        refId = "2"
        requisiteStageRefIds = ["1"]
      }
      stage {
        refId = "3"
      }
      stage {
        refId = "4"
        requisiteStageRefIds = ["3"]
      }
    }

    expect:
    with(Stage.topologicalSort(pipeline.stages).collect(toList())) {
      "1" in refId[0..1]
      "3" in refId[0..1]
      "2" in refId[2..3]
      "4" in refId[2..3]
    }
  }

}
