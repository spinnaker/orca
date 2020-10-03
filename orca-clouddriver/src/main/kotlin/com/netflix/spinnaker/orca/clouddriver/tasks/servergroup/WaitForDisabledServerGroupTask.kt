package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.retrofit.exceptions.RetrofitExceptionHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Component
class WaitForDisabledServerGroupTask(
  val oortService: OortService,
  val objectMapper: ObjectMapper
) : AbstractCloudProviderAwareTask(), RetryableTask {
  val log: Logger = LoggerFactory.getLogger(javaClass)

  override fun getBackoffPeriod(): Long = 10000

  override fun getTimeout(): Long = 1800000

  override fun execute(stage: StageExecution): TaskResult {
    val serverGroupDescriptor = getServerGroupDescriptor(stage)
    try {
      val response = oortService.getServerGroup(serverGroupDescriptor.account, serverGroupDescriptor.region, serverGroupDescriptor.name)
      val serverGroup: TargetServerGroup = objectMapper.readValue(response.body.`in`(), TargetServerGroup::class.java)
      if (serverGroup.isDisabled) {
        return TaskResult.SUCCEEDED
      }

      return TaskResult.RUNNING
    } catch (e: RetrofitError) {
      val retrofitErrorResponse = RetrofitExceptionHandler().handle(stage.name, e)
      log.error("Unexpected retrofit error {}", retrofitErrorResponse, e)
      return TaskResult
        .builder(ExecutionStatus.RUNNING)
        .context(mapOf("lastRetrofitException" to retrofitErrorResponse))
        .build()
    }
  }
}
