package com.netflix.spinnaker.q

/**
 * Strategy that enables the [Queue] to be enabled and disabled.
 */
abstract class Activator {

  protected abstract val enabled: Boolean

  /**
   * Execute [block] if enabled otherwise no-op.
   */
  fun ifEnabled(block: () -> Unit) {
    if (enabled) {
      block.invoke()
    }
  }
}