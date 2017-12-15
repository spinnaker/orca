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

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName("git")
public class GitTrigger extends Trigger {

  private final String project;
  private final String source;
  private final String master;
  private final String job;
  private final String branch;
  private final String slug;
  private final String hash;

  @JsonCreator
  public GitTrigger(
    @JsonProperty("user") String user,
    @JsonProperty("parameters") Map<String, Object> parameters,
    @JsonProperty("enabled") boolean enabled,
    @JsonProperty("project") String project,
    @JsonProperty("source") String source,
    @JsonProperty("master") String master,
    @JsonProperty("job") String job,
    @JsonProperty("branch") String branch,
    @JsonProperty("slug") String slug,
    @JsonProperty("hash") String hash
  ) {
    super(user, parameters, enabled);
    this.project = project;
    this.source = source;
    this.master = master;
    this.job = job;
    this.branch = branch;
    this.slug = slug;
    this.hash = hash;
  }

  public String getProject() {
    return project;
  }

  public String getSource() {
    return source;
  }

  public String getMaster() {
    return master;
  }

  public String getJob() {
    return job;
  }

  public String getBranch() {
    return branch;
  }

  public String getSlug() {
    return slug;
  }

  public String getHash() {
    return hash;
  }
}
