package com.netflix.spinnaker.orca.igor.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleCloudBuildRepoSource {
  private final String branchName;
  private final String commitSha;
  private final String tagName;

  @JsonCreator
  public GoogleCloudBuildRepoSource(
      @JsonProperty("branchName") String branchName,
      @JsonProperty("commitSha") String commitSha,
      @JsonProperty("tagName") String tagName) {
    this.branchName = branchName;
    this.commitSha = commitSha;
    this.tagName = tagName;
  }
}
