/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.pipeline.expressions

import com.netflix.spinnaker.orca.ExecutionStatus
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage;

class ExpressionsSupportSpec extends Specification {
  @Shared
  def pipeline = pipeline {
    stage {
      id = "1"
      name = "My First Stage"
      context = [
        "region": "us-east-1",
      ]
    }

    stage {
      id = "2"
      name = "My Second Stage"
      context = [
        "region": "us-west-1",
      ]
    }
  }

  @Unroll
  def "stage() should match on #matchedAttribute"() {
    expect:
    ExpressionsSupport.stage(pipeline, stageCriteria).context.region == expectedRegion

    where:
    stageCriteria     || matchedAttribute || expectedRegion
    "My Second Stage" || "name"           || "us-west-1"
    "1"               || "id"             || "us-east-1"
    "2"               || "id"             || "us-west-1"
  }

  def "stage() should raise exception if stage not found"() {
    when:
    ExpressionsSupport.stage(pipeline, "does_not_exist")

    then:
    thrown(SpelHelperFunctionException)

    when:
    ExpressionsSupport.stage("not_an_expression", "does_not_matter")

    then:
    // raise exception when not passed an Execution
    thrown(SpelHelperFunctionException)
  }
}
