/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.pipeline.model;

import java.net.URI;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("jenkins")
public class JenkinsTrigger extends Trigger {

  private final String master;
  private final String job;
  private final int buildNumber;
  private final String propertyFile;
  private final BuildInfo buildInfo;

  @JsonCreator
  public JenkinsTrigger(
    @JsonProperty("master") @Nonnull String master,
    @JsonProperty("job") @Nonnull String job,
    @JsonProperty("buildNumber") int buildNumber,
    @JsonProperty("propertyFile") @Nullable String propertyFile,
    @JsonProperty("buildInfo") @Nonnull BuildInfo buildInfo,
    @JsonProperty("user") @Nullable String user,
    @JsonProperty("parameters") @Nullable Map<String, Object> parameters
  ) {
    super(user, parameters);
    this.master = master;
    this.job = job;
    this.buildNumber = buildNumber;
    this.propertyFile = propertyFile;
    this.buildInfo = buildInfo;
  }

  public @Nonnull String getMaster() {
    return master;
  }

  public @Nonnull String getJob() {
    return job;
  }

  public int getBuildNumber() {
    return buildNumber;
  }

  public @Nullable String getPropertyFile() {
    return propertyFile;
  }

  public @Nonnull BuildInfo getBuildInfo() {
    return buildInfo;
  }

  private static class BuildInfo {
    private final String name;
    private final int number;
    private final URI url;
    private final List<Artifact> artifacts;
    private final List<SourceControl> scm;
    private final String fullDisplayName;
    private final boolean building;
    private final String result;

    @JsonCreator
    private BuildInfo(
      @JsonProperty("name") @Nonnull String name,
      @JsonProperty("number") int number,
      @JsonProperty("url") @Nonnull URI url,
      @JsonProperty("artifacts") @Nonnull List<Artifact> artifacts,
      @JsonProperty("scm") @Nonnull List<SourceControl> scm,
      @JsonProperty("fullDisplayName") @Nonnull String fullDisplayName,
      @JsonProperty("building") boolean building,
      @JsonProperty("result") @Nullable String result
    ) {
      this.name = name;
      this.number = number;
      this.url = url;
      this.artifacts = artifacts;
      this.scm = scm;
      this.fullDisplayName = fullDisplayName;
      this.building = building;
      this.result = result;
    }

    public @Nonnull String getName() {
      return name;
    }

    public int getNumber() {
      return number;
    }

    public @Nonnull URI getUrl() {
      return url;
    }

    public @Nonnull List<Artifact> getArtifacts() {
      return artifacts;
    }

    public @Nonnull List<SourceControl> getScm() {
      return scm;
    }

    public @Nonnull String getFullDisplayName() {
      return fullDisplayName;
    }

    public boolean isBuilding() {
      return building;
    }

    public @Nullable String getResult() {
      return result;
    }
  }

  private static class SourceControl {
    private final String name;
    private final String branch;
    private final String sha1;

    @JsonCreator
    private SourceControl(
      @JsonProperty("name") @Nonnull String name,
      @JsonProperty("branch") @Nonnull String branch,
      @JsonProperty("sha1") @Nonnull String sha1
    ) {
      this.name = name;
      this.branch = branch;
      this.sha1 = sha1;
    }

    public @Nonnull String getName() {
      return name;
    }

    public @Nonnull String getBranch() {
      return branch;
    }

    public @Nonnull String getSha1() {
      return sha1;
    }
  }

  private static class Artifact {
    private final String fileName;
    private final String relativePath;

    @JsonCreator
    private Artifact(
      @JsonProperty("fileName") @Nonnull String fileName,
      @JsonProperty("relativePath") @Nonnull String relativePath
    ) {
      this.fileName = fileName;
      this.relativePath = relativePath;
    }

    public @Nonnull String getFileName() {
      return fileName;
    }

    public @Nonnull String getRelativePath() {
      return relativePath;
    }
  }
}
