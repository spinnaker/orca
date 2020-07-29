package com.netflix.spinnaker.orca.remote.stage

import com.netflix.spinnaker.kork.exceptions.SpinnakerException

class RemoteStageMessageHandlingException : SpinnakerException {
  constructor(message: String) : super(message)
  constructor(message: String, cause: Throwable) : super(message, cause)
  constructor(cause: Throwable) : super(cause)
}

class RemoteStageExecutionParsingException : SpinnakerException {
  constructor(message: String) : super(message)
  constructor(message: String, cause: Throwable) : super(message, cause)
  constructor(cause: Throwable) : super(cause)
}

class StageExecutionNotFound : SpinnakerException {
  constructor(message: String) : super(message)
  constructor(message: String, cause: Throwable) : super(message, cause)
  constructor(cause: Throwable) : super(cause)
}
