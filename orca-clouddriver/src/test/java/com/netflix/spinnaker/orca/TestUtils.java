/*
 * Copyright 2021 Salesforce.com, Inc.
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

package com.netflix.spinnaker.orca;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import java.io.*;
import java.nio.charset.StandardCharsets;
import retrofit.converter.Converter;
import retrofit.mime.TypedInput;

public class TestUtils {

  public static String getResource(String name) {
    try {
      return Resources.toString(TestUtils.class.getResource(name), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static <T> T getResource(ObjectMapper objectMapper, String name, Class<T> valueType) {
    try {
      return objectMapper.readValue(TestUtils.class.getResourceAsStream(name), valueType);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static InputStream getResourceAsStream(String name) {
    return TestUtils.class.getResourceAsStream(name);
  }

  public static class MockTypedInput implements TypedInput {
    private final Converter converter;
    private final Object body;

    private byte[] bytes;

    public MockTypedInput(Converter converter, Object body) {
      this.converter = converter;
      this.body = body;
    }

    @Override
    public String mimeType() {
      return "application/unknown";
    }

    @Override
    public long length() {
      try {
        initBytes();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return bytes.length;
    }

    @Override
    public InputStream in() throws IOException {
      initBytes();
      return new ByteArrayInputStream(bytes);
    }

    private synchronized void initBytes() throws IOException {
      if (bytes == null) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        converter.toBody(body).writeTo(out);
        bytes = out.toByteArray();
      }
    }
  }
}
