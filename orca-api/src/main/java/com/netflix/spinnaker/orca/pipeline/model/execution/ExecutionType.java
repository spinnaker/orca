package com.netflix.spinnaker.orca.pipeline.model.execution;

public enum ExecutionType {
  PIPELINE,
  ORCHESTRATION;

  @Override
  public String toString() {
    return name().toLowerCase();
  }
}
