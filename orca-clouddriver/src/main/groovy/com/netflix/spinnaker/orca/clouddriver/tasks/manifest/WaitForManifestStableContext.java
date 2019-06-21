/*
 * Copyright 2019 Cerner Corporation
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
 *
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;

@Getter
public class WaitForManifestStableContext extends HashMap<String, Object> {

  private final Optional<List<String>> messages;
  private final Optional<Exception> exception;
  private final Optional<List<Map<String, String>>> stableManifests;
  private final Optional<List<Map<String, String>>> failedManifests;
  private final Optional<List> warnings;

  // There does not seem to be a way to auto-generate a constructor using our current version of
  // Lombok (1.16.20) that
  // Jackson can use to deserialize.
  public WaitForManifestStableContext(
      @JsonProperty("messages") Optional<List<String>> messages,
      @JsonProperty("exception") Optional<Exception> exception,
      @JsonProperty("stableManifests") Optional<List<Map<String, String>>> stableManifests,
      @JsonProperty("failedManifests") Optional<List<Map<String, String>>> failedManifests,
      @JsonProperty("warnings") Optional<List> warnings) {
    this.messages = messages;
    this.exception = exception;
    this.stableManifests = stableManifests;
    this.failedManifests = failedManifests;
    this.warnings = warnings;
  }

  public List<Map<String, String>> getCompletedManifests() {
    return Stream.concat(
            stableManifests.orElse(Collections.emptyList()).stream(),
            failedManifests.orElse(Collections.emptyList()).stream())
        .collect(Collectors.toList());
  }

  public Optional<List<String>> getFailureMessages() {
    return getException()
        .flatMap((exception) -> exception.getDetails().map(details -> details.get("errors")));
  }

  @Getter
  private static class Exception {
    private final Optional<Map<String, List<String>>> details;

    public Exception(@JsonProperty("details") Optional<Map<String, List<String>>> details) {
      this.details = details;
    }
  }
}
