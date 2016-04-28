/*
 * Copyright 2016 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.pipeline.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification
import spock.lang.Unroll

class PackageInfoSpec extends Specification {

  @Autowired
  ObjectMapper mapper

  @Unroll
  def "All the matching packages get replaced with the build ones, while others just pass-through"() {
    given:
      Stage bakeStage = new PipelineStage()
      PackageType packageType = PackageType.DEB
      boolean extractBuildDetails = false
      PackageInfo packageInfo = new PackageInfo(bakeStage,
        packageType.packageType,
        packageType.versionDelimiter,
        extractBuildDetails,
        false,
        mapper)

      Map trigger = ["buildInfo": ["artifacts": filename ]]
      Map buildInfo = ["artifacts": []]
      Map request = ["package": requestPackage]

    when:
      Map requestMap = packageInfo.createAugmentedRequest(trigger, buildInfo, request)

    then:
      requestMap.package == result

    where:
      filename                                      | requestPackage                                 | result
      [["fileName": "test-package_1.0.0.deb"]]      | "test-package"                                 | "test-package_1.0.0"
      [["fileName": "test-package_1.0.0.deb"]]      | "another-package"                              | "another-package"
      [["fileName": "test-package_1.0.0.deb"]]      | "another-package test-package"                 | "another-package test-package_1.0.0"

      [["fileName": "first-package_1.0.1.deb"],
       ["fileName": "second-package_2.3.42.deb"]]   | "first-package another-package second-package" |  "first-package_1.0.1 another-package second-package_2.3.42"


  }

}
