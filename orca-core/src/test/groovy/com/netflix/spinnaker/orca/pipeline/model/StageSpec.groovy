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

import com.netflix.spinnaker.kork.exceptions.SpinnakerException
import spock.lang.Specification
import spock.lang.Unroll

import static StageExecutionImpl.topologicalSort
import static com.netflix.spinnaker.orca.api.pipeline.SyntheticStageOwner.*
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage
import static java.util.stream.Collectors.toList

class StageSpec extends Specification {

  def "topologicalSort sorts stages with direct relationships"() {
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
    with(topologicalSort(pipeline.stages).collect(toList())) {
      refId == ["1", "2", "3"]
    }
  }

  def "topologicalSort sorts stages with fork join topology"() {
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
    with(topologicalSort(pipeline.stages).collect(toList())) {
      refId.first() == "1"
      refId.last() == "4"
    }
  }

  def "topologicalSort sorts stages with isolated branches"() {
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
    with(topologicalSort(pipeline.stages).collect(toList())) {
      "1" in refId[0..1]
      "3" in refId[0..1]
      "2" in refId[2..3]
      "4" in refId[2..3]
    }
  }

  def "topologicalSort only considers top-level stages"() {
    given:
    def pipeline = pipeline {
      stage {
        refId = "1"
        stage {
          refId = "1<1"
        }
        stage {
          refId = "1<2"
          requisiteStageRefIds = ["1<1"]
        }
        stage {
          refId = "1>1"
          syntheticStageOwner = STAGE_AFTER
        }
      }
      stage {
        refId = "2"
        requisiteStageRefIds = ["1"]
      }
    }

    expect:
    with(topologicalSort(pipeline.stages).collect(toList())) {
      refId == ["1", "2"]
    }
  }

  def "topologicalSort does not go into an infinite loop if given a bad set of stages"() {
    given:
    def pipeline = pipeline {
      stage {
        refId = "1"
        requisiteStageRefIds = ["2"]
      }
      stage {
        refId = "2"
        requisiteStageRefIds = ["1"]
      }
    }

    when:
    topologicalSort(pipeline.stages)

    then:
    def e = thrown(IllegalStateException)
    println e.message
  }

  def "can find all descendent with loop and branch"() {
    given:
    def pipeline = pipeline {
      stage {
        refId = "0"
      }
      stage {
        refId = "1"
        requisiteStageRefIds = ["0"]
      }
      stage {
        refId = "2"
        requisiteStageRefIds = ["1", "3"]
      }
      stage {
        refId = "3"
        requisiteStageRefIds = ["2"]
      }
      stage {
        refId = "4"
        requisiteStageRefIds = ["3"]
      }
      stage {
        refId = "5"
        requisiteStageRefIds = ["3"]
      }
      stage {
        refId = "6"
        requisiteStageRefIds = ["5"]
      }
      stage {
        refId = "7"
        requisiteStageRefIds = ["1"]
      }
    }

    def stage = pipeline.stageByRef("1")

    when:
    def descendants = stage.allDownstreamStages()

    then:
    descendants.size() == 6
    descendants.find {it.refId == "2"} != null
    descendants.find {it.refId == "3"} != null
    descendants.find {it.refId == "4"} != null
    descendants.find {it.refId == "5"} != null
    descendants.find {it.refId == "6"} != null
    descendants.find {it.refId == "7"} != null
  }

  def "should not fail on no descendents"() {
    given:
    def pipeline = pipeline {
      stage {
        refId = "0"
      }
    }

    def stage = pipeline.stageByRef("0")

    when:
    def descendants = stage.allDownstreamStages()

    then:
    descendants.size() == 0
  }

  def "ancestors of a STAGE_AFTER stage should include STAGE_BEFORE siblings"() {
    given:
    def pipeline = pipeline {
      stage {
        refId = "1"
        type = "test"
        stage {
          refId = "1<1"
          syntheticStageOwner = STAGE_BEFORE
        }
        stage {
          refId = "1<2"
          syntheticStageOwner = STAGE_AFTER
        }
      }
    }

    def syntheticBeforeStage = pipeline.stages.find { it.syntheticStageOwner == STAGE_BEFORE }
    def syntheticAfterStage = pipeline.stages.find { it.syntheticStageOwner == STAGE_AFTER }

    when:
    def syntheticBeforeStageAncestors = syntheticBeforeStage.ancestors()
    def syntheticAfterStageAncestors = syntheticAfterStage.ancestors()

    then:
    syntheticBeforeStageAncestors*.refId == ["1<1", "1"]
    syntheticAfterStageAncestors*.refId == ["1<2", "1<1", "1"]
  }

  def "ancestors should not include duplicate stages"() {
    given:
    def pipeline = pipeline {
      stage {
        refId = "1"
        requisiteStageRefIds = []
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

    def stage4 = pipeline.stages.find { it.refId == "4" }

    when:
    def ancestors = stage4.ancestors()

    then:
    ancestors*.refId == ["4", "2", "3", "1"]
  }

  @Unroll
  def "should fetch stageTimeoutMs for a synthetic stage from the closest parent with it overridden"() {
    given:
    def pipeline = pipeline {
      stage {
        id = "1"

        stage {
          id = "2"

          stage {
            id = "3"
          }
        }
      }
    }

    if (stage1TimeoutMs) {
      pipeline.stageById("1").context.stageTimeoutMs = stage1TimeoutMs
    }
    if (stage2TimeoutMs) {
      pipeline.stageById("2").context.stageTimeoutMs = stage2TimeoutMs
    }
    if (stage3TimeoutMs) {
      pipeline.stageById("3").context.stageTimeoutMs = stage3TimeoutMs
    }

    expect:
    pipeline.stageById("3").getParentWithTimeout().orElse(null)?.getTimeout()?.orElse(null) == expectedTimeout

    where:
    stage1TimeoutMs | stage2TimeoutMs | stage3TimeoutMs || expectedTimeout
    null            | null            | null            || null
    100             | null            | null            || 100
    100             | 200             | null            || 200
    100             | 200             | 300             || 300
  }

  def "should set propagateFailuresToParent correctly"() {
    given:
    def pipeline = pipeline {
      stage {
        refId = "parent"

        stage {
          refId = "child"
        }
      }
    }

    def parentStage = pipeline.stageByRef("parent")
    def childStage = pipeline.stageByRef("child")

    when: 'trying to set PropagateFailuresToParent on parent stage'
    parentStage.setAllowSiblingStagesToContinueOnFailure(true)

    then: 'it should fail'
    thrown(SpinnakerException)

    when: 'parent stage erroneously has the setting in context'
    parentStage = pipeline.stageByRef("parent")
    parentStage.context.put("allowSiblingStagesToContinueOnFailure", true)

    then: 'we ignore the value in context'
    childStage.getAllowSiblingStagesToContinueOnFailure() == false

    when: 'trying to set PropagateFailuresToParent on a child stage'
    childStage.setAllowSiblingStagesToContinueOnFailure(true)

    then: 'it should succeed'
    noExceptionThrown()
    childStage.context.allowSiblingStagesToContinueOnFailure == true
    childStage.getAllowSiblingStagesToContinueOnFailure() == true
  }
}
