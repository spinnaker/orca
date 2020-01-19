/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.orca.q.admin.web

import com.netflix.spinnaker.orca.q.StartWaitingExecutions
import com.netflix.spinnaker.orca.q.admin.HydrateQueueCommand
import com.netflix.spinnaker.orca.q.admin.HydrateQueueInput
import com.netflix.spinnaker.orca.q.admin.HydrateQueueOutput
import com.netflix.spinnaker.q.Queue
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import javax.ws.rs.QueryParam

@RestController
@RequestMapping("/admin/queue")
class QueueAdminController(
  private val hydrateCommand: HydrateQueueCommand,
  private val queue: Queue
) {

  @PostMapping(value = ["/hydrate"])
  fun hydrateQueue(
    @QueryParam("dryRun") dryRun: Boolean?,
    @QueryParam("executionId") executionId: String?,
    @QueryParam("startMs") startMs: Long?,
    @QueryParam("endMs") endMs: Long?
  ): HydrateQueueOutput =
    hydrateCommand(HydrateQueueInput(
      executionId,
      if (startMs != null) Instant.ofEpochMilli(startMs) else null,
      if (endMs != null) Instant.ofEpochMilli(endMs) else null,
      dryRun ?: true
    ))

  @PostMapping(value = ["kickPending"])
  fun kickPendingExecutions(
    @QueryParam("pipelineConfigId") pipelineConfigId: String,
    @QueryParam("purge") purge: Boolean?
  ) {

    queue.push(StartWaitingExecutions(pipelineConfigId, purge ?: false))
  }
}
