/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.model

import com.netflix.spinnaker.orca.ExecutionStatus

class DefaultTask implements Task, Serializable {
  String id
  Class implementingClass
  String name
  Long startTime
  Long endTime
  ExecutionStatus status = ExecutionStatus.NOT_STARTED

  static boolean isBookend(Task task) {
    return task.name == "stageEnd" || task.name == "stageStart"
  }
}
