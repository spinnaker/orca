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

package com.netflix.spinnaker.orca.igor.model;

import java.net.URI;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.netflix.spinnaker.orca.pipeline.model.Trigger;

@JsonTypeName("jenkins")
public class JenkinsTrigger implements Trigger {

  private final String master;
  private final String job;
  private final int buildNumber;
  private final String propertyFile;
  private final BuildInfo buildInfo;
  private final String user;
  private final Map<String, Object> parameters;

  @JsonCreator
  public JenkinsTrigger(
    @JsonProperty("master") String master,
    @JsonProperty("job") String job,
    @JsonProperty("buildNumber") int buildNumber,
    @JsonProperty("propertyFile") String propertyFile,
    @JsonProperty("buildInfo") BuildInfo buildInfo,
    @JsonProperty("user") String user,
    @JsonProperty("parameters") Map<String, Object> parameters
  ) {
    this.master = master;
    this.job = job;
    this.buildNumber = buildNumber;
    this.propertyFile = propertyFile;
    this.buildInfo = buildInfo;
    this.user = user;
    this.parameters = parameters;
  }

  public String getMaster() {
    return master;
  }

  public String getJob() {
    return job;
  }

  public int getBuildNumber() {
    return buildNumber;
  }

  public String getPropertyFile() {
    return propertyFile;
  }

  public BuildInfo getBuildInfo() {
    return buildInfo;
  }

  public String getUser() {
    return user;
  }

  public Map<String, Object> getParameters() {
    return parameters;
  }

  @Nonnull @Override public String getType() {
    return "jenkins";
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
      @JsonProperty("name") String name,
      @JsonProperty("number") int number,
      @JsonProperty("url") URI url,
      @JsonProperty("artifacts") List<Artifact> artifacts,
      @JsonProperty("scm") List<SourceControl> scm,
      @JsonProperty("fullDisplayName") String fullDisplayName,
      @JsonProperty("building") boolean building,
      @JsonProperty("result") String result
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

    public String getName() {
      return name;
    }

    public int getNumber() {
      return number;
    }

    public URI getUrl() {
      return url;
    }

    public List<Artifact> getArtifacts() {
      return artifacts;
    }

    public List<SourceControl> getScm() {
      return scm;
    }

    public String getFullDisplayName() {
      return fullDisplayName;
    }

    public boolean isBuilding() {
      return building;
    }

    public String getResult() {
      return result;
    }
  }

  private static class SourceControl {
    private final String name;
    private final String branch;
    private final String sha1;

    @JsonCreator
    private SourceControl(
      @JsonProperty("name") String name,
      @JsonProperty("branch") String branch,
      @JsonProperty("sha1") String sha1
    ) {
      this.name = name;
      this.branch = branch;
      this.sha1 = sha1;
    }

    public String getName() {
      return name;
    }

    public String getBranch() {
      return branch;
    }

    public String getSha1() {
      return sha1;
    }
  }

  private static class Artifact {
    private final String fileName;
    private final String relativePath;

    @JsonCreator
    private Artifact(
      @JsonProperty("fileName") String fileName,
      @JsonProperty("relativePath") String relativePath
    ) {
      this.fileName = fileName;
      this.relativePath = relativePath;
    }

    public String getFileName() {
      return fileName;
    }

    public String getRelativePath() {
      return relativePath;
    }
  }
}
