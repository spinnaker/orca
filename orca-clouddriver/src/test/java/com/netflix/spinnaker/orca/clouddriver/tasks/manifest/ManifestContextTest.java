/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.clouddriver.tasks.manifest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

class ManifestContextTest {
  @Test
  void deserialize() throws IOException {
    String json =
        "{\n"
            + "  \"source\": \"text\",\n"
            + "  \"manifestArtifact\": {\n"
            + "  },\n"
            + "  \"manifestArtifactId\": \"123\",\n"
            + "  \"manifestArtifactAccount\": \"account\",\n"
            + "  \"requiredArtifactIds\": [\n"
            + "    \"456\"\n"
            + "  ],\n"
            + "  \"requiredArtifacts\": [\n"
            + "    {\n"
            + "      \"artifact\": {\n"
            + "        \"artifactAccount\": \"docker-registry\",\n"
            + "        \"customKind\": true,\n"
            + "        \"id\": \"5f22ecc4-f223-4b0e-afe1-c53468643861\",\n"
            + "        \"name\": \"gcr.io/project/myimage\",\n"
            + "        \"reference\": \"gcr.io/project/myimage\",\n"
            + "        \"type\": \"docker/image\"\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";

    TestManifestContext context = new ObjectMapper().readValue(json, TestManifestContext.class);
    assertThat(context.getSource()).isEqualTo(ManifestContext.Source.Text);
  }

  private static class TestManifestContext extends ManifestContext {
    @Nullable
    @Override
    public List<Map<Object, Object>> getManifest() {
      return null;
    }
  }
}
