package com.netflix.spinnaker.orca.api.pipeline.models;

public enum ExecutionEngine {
  /**
   * v2 is obsolete, but it is here as a failsafe in case it is present in a pipeline configuration
   * and needs to be deserialized. If v2 is specified, the default execution engine (v3) will be
   * used instead.
   */
  v2,

  /** v3 execution engine is the keiko execution engine. */
  v3,

  /** v4 execution engine does not yet exist, early prototyping is underway. */
  v4;

  public static ExecutionEngine DEFAULT = v3;
}
