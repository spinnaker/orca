package com.netflix.spinnaker.orca.pipeline.model.execution;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import javax.annotation.Nullable;

public class PausedDetails implements Serializable {
  String pausedBy;

  public @Nullable String getPausedBy() {
    return pausedBy;
  }

  public void setPausedBy(@Nullable String pausedBy) {
    this.pausedBy = pausedBy;
  }

  String resumedBy;

  public @Nullable String getResumedBy() {
    return resumedBy;
  }

  public void setResumedBy(@Nullable String resumedBy) {
    this.resumedBy = resumedBy;
  }

  Long pauseTime;

  public @Nullable Long getPauseTime() {
    return pauseTime;
  }

  public void setPauseTime(@Nullable Long pauseTime) {
    this.pauseTime = pauseTime;
  }

  Long resumeTime;

  public @Nullable Long getResumeTime() {
    return resumeTime;
  }

  public void setResumeTime(@Nullable Long resumeTime) {
    this.resumeTime = resumeTime;
  }

  @JsonIgnore
  public boolean isPaused() {
    return pauseTime != null && resumeTime == null;
  }

  @JsonIgnore
  public long getPausedMs() {
    return (pauseTime != null && resumeTime != null) ? resumeTime - pauseTime : 0;
  }
}
