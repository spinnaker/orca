package com.netflix.spinnaker.orca.keel.model

data class SubmitDeliveryConfigBody(
  val config: DeliveryConfig,
  val gitMetadata: GitMetadata? = null
)
