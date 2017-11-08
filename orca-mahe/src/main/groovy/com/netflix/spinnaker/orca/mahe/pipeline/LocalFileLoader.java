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

package com.netflix.spinnaker.orca.mahe.pipeline;

import org.springframework.stereotype.Component;
import java.io.File;
import java.io.IOException;
import java.net.URI;

@Component
public class LocalFileLoader implements FileLoader {
  private static final String SUPPORTED_CONTENT_SOURCE = "localFile";

  @Override
  public boolean supports(URI uri, String contentSource) {
    return uri.getScheme().equalsIgnoreCase("file") &&
      SUPPORTED_CONTENT_SOURCE.equals(contentSource);
  }

  @Override
  public File load(URI uri) throws IOException {
    File file = new File(uri);
    if (!file.exists()) {
      throw new IOException("file doest not exist");
    }

    return file;
  }
}
