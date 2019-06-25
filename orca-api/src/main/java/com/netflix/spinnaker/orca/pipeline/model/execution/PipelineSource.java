package com.netflix.spinnaker.orca.pipeline.model.execution;

import java.io.Serializable;
import javax.annotation.Nonnull;

public class PipelineSource implements Serializable {
  private String id;

  public @Nonnull String getId() {
    return id;
  }

  public void setId(@Nonnull String id) {
    this.id = id;
  }

  private String type;

  public @Nonnull String getType() {
    return type;
  }

  public void setType(@Nonnull String type) {
    this.type = type;
  }
}
