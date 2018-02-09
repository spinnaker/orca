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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

@JsonTypeName("jenkins")
public class JenkinsTrigger extends Trigger {

  private final String master;
  private final String job;
  private final int buildNumber;
  private final String propertyFile;

  private Map<String, Object> properties;
  private BuildInfo buildInfo;

  @JsonCreator
  public JenkinsTrigger(
    @JsonProperty("master") @Nonnull String master,
    @JsonProperty("job") @Nonnull String job,
    @JsonProperty("buildNumber") int buildNumber,
    @JsonProperty("propertyFile") @Nullable String propertyFile,
    @JsonProperty("user") @Nullable String user,
    @JsonProperty("parameters") @Nullable Map<String, Object> parameters,
    @JsonProperty("artifacts") @Nullable List<Artifact> artifacts
  ) {
    super(user, parameters, artifacts);
    this.master = master;
    this.job = job;
    this.buildNumber = buildNumber;
    this.propertyFile = propertyFile;
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

  @JsonProperty("properties")
  public @Nonnull Map<String, Object> getProperties() {
    return properties == null ? emptyMap() : properties;
  }

  public void setProperties(Map<String, Object> properties) {
    this.properties = properties;
  }

  @JsonProperty("buildInfo")
  public @Nullable BuildInfo getBuildInfo() {
    return buildInfo;
  }

  public void setBuildInfo(@Nonnull BuildInfo buildInfo) {
    this.buildInfo = buildInfo;
  }

  @Override public boolean equals(Object o) {
    if (!super.equals(o)) return false;
    JenkinsTrigger that = (JenkinsTrigger) o;
    return buildNumber == that.buildNumber &&
      Objects.equals(master, that.master) &&
      Objects.equals(job, that.job) &&
      Objects.equals(propertyFile, that.propertyFile);
  }

  @Override public int hashCode() {
    return Objects.hash(super.hashCode(), master, job, buildNumber, propertyFile);
  }

  @Override public String toString() {
    return "JenkinsTrigger{" +
      super.toString() +
      ", master='" + master + '\'' +
      ", job='" + job + '\'' +
      ", buildNumber=" + buildNumber +
      ", propertyFile='" + propertyFile + '\'' +
      ", buildInfo=" + buildInfo +
      '}';
  }

  public static class BuildInfo {
    private final String name;
    private final int number;
    private final String url;
    private final List<JenkinsArtifact> artifacts;
    private final List<SourceControl> scm;
    private final boolean building;
    private final String result;

    @JsonCreator
    public BuildInfo(
      @JsonProperty("name") @Nonnull String name,
      @JsonProperty("number") int number,
      @JsonProperty("url") @Nonnull String url,
      @JsonProperty("artifacts") @Nullable List<JenkinsArtifact> artifacts,
      @JsonProperty("scm") @Nullable List<SourceControl> scm,
      @JsonProperty("building") boolean building,
      @JsonProperty("result") @Nullable String result
    ) {
      this.name = name;
      this.number = number;
      this.url = url;
      this.artifacts = artifacts == null ? emptyList() : artifacts;
      this.scm = scm == null ? emptyList() : scm;
      this.building = building;
      this.result = result;
    }

    public @Nonnull String getName() {
      return name;
    }

    public int getNumber() {
      return number;
    }

    public @Nonnull String getUrl() {
      return url;
    }

    public @Nonnull List<JenkinsArtifact> getArtifacts() {
      return artifacts;
    }

    public @Nonnull List<SourceControl> getScm() {
      return scm;
    }

    public @Nonnull String getFullDisplayName() {
      return name + " #" + number;
    }

    public boolean isBuilding() {
      return building;
    }

    public @Nullable String getResult() {
      return result;
    }

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      BuildInfo buildInfo = (BuildInfo) o;
      return number == buildInfo.number &&
        building == buildInfo.building &&
        Objects.equals(name, buildInfo.name) &&
        Objects.equals(url, buildInfo.url) &&
        Objects.equals(artifacts, buildInfo.artifacts) &&
        Objects.equals(scm, buildInfo.scm) &&
        Objects.equals(result, buildInfo.result);
    }

    @Override public int hashCode() {
      return Objects.hash(name, number, url, artifacts, scm, building, result);
    }
  }

  public static class SourceControl {
    private final String name;
    private final String branch;
    private final String sha1;

    @JsonCreator
    public SourceControl(
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

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      SourceControl that = (SourceControl) o;
      return Objects.equals(name, that.name) &&
        Objects.equals(branch, that.branch) &&
        Objects.equals(sha1, that.sha1);
    }

    @Override public int hashCode() {
      return Objects.hash(name, branch, sha1);
    }
  }

  public static class JenkinsArtifact {
    private final String fileName;
    private final String relativePath;
    private final String displayPath;

    @JsonCreator
    public JenkinsArtifact(
      @JsonProperty("fileName") @Nonnull String fileName,
      @JsonProperty("relativePath") @Nonnull String relativePath,
      @JsonProperty("displayPath") String diplayPath
    ) {
      this.fileName = fileName;
      this.relativePath = relativePath;
      this.displayPath = diplayPath;
    }

    public @Nonnull String getFileName() {
      return fileName;
    }

    public @Nonnull String getRelativePath() {
      return relativePath;
    }

    public String getDisplayPath() {
      return displayPath;
    }

    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      JenkinsArtifact that = (JenkinsArtifact) o;
      return Objects.equals(fileName, that.fileName) &&
        Objects.equals(relativePath, that.relativePath) &&
        Objects.equals(displayPath, that.displayPath);
    }

    @Override public int hashCode() {
      return Objects.hash(fileName, relativePath, displayPath);
    }
  }
}
