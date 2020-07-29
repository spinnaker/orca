package com.netflix.spinnaker.orca.remote.stage

data class Callback(
  val http: Http,
  val pubsub: Pubsub
)

data class Http(
  val uri: String,
  val headers: MutableMap<String, Any>
)

data class Pubsub(
  val name: String,
  val provider: String,
  val providerConfig: MutableMap<String, String>,
  val headers: MutableMap<String, Any>
)
