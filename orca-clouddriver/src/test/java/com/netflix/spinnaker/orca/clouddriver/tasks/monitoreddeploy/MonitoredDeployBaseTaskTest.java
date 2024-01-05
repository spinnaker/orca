/*
 * Copyright 2023 OpsMx, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.monitoreddeploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.config.DeploymentMonitorDefinition;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerConversionException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerNetworkException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
import com.netflix.spinnaker.orca.deploymentmonitor.DeploymentMonitorServiceProvider;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.Header;
import retrofit.client.OkClient;
import retrofit.client.Response;
import retrofit.converter.ConversionException;
import retrofit.converter.Converter;
import retrofit.converter.JacksonConverter;
import retrofit.mime.TypedInput;

public class MonitoredDeployBaseTaskTest {

  private MonitoredDeployBaseTask monitoredDeployBaseTask;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private DeploymentMonitorServiceProvider deploymentMonitorServiceProvider;

  @BeforeEach
  void setup() {
    OkClient okClient = new OkClient();
    RestAdapter.LogLevel retrofitLogLevel = RestAdapter.LogLevel.NONE;

    RequestInterceptor requestInterceptor = request -> {};
    DeploymentMonitorDefinition deploymentMonitorDefinition = new DeploymentMonitorDefinition();
    deploymentMonitorDefinition.setId("LogMonitorId");
    deploymentMonitorDefinition.setName("LogMonitor");
    deploymentMonitorDefinition.setFailOnError(true);
    var deploymentMonitorDefinitions = new ArrayList<DeploymentMonitorDefinition>();
    deploymentMonitorDefinitions.add(deploymentMonitorDefinition);

    deploymentMonitorServiceProvider =
        new DeploymentMonitorServiceProvider(
            okClient, retrofitLogLevel, requestInterceptor, deploymentMonitorDefinitions);
    monitoredDeployBaseTask =
        new MonitoredDeployBaseTask(deploymentMonitorServiceProvider, new NoopRegistry());
  }

  @Test
  public void shouldParseHttpErrorResponseDetailsWhenHttpErrorHasOccurred() {

    var converter = new JacksonConverter(objectMapper);
    var responseBody = new HashMap<String, String>();
    var headers = new ArrayList<Header>();
    var header = new Header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

    headers.add(header);
    responseBody.put("error", "400 - Bad request, application name cannot be empty");

    Response response =
        new Response(
            "/deployment/evaluateHealth",
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.name(),
            headers,
            new MockTypedInput(converter, responseBody));

    RetrofitError httpError =
        RetrofitError.httpError(
            "https://foo.com/deployment/evaluateHealth", response, new JacksonConverter(), null);

    SpinnakerHttpException httpException = new SpinnakerHttpException(httpError);

    String logMessageOnSpinHttpException = monitoredDeployBaseTask.getErrorMessage(httpException);
    String body = "{\"error\":\"400 - Bad request, application name cannot be empty\"}";

    assertThat(logMessageOnSpinHttpException)
        .isEqualTo(
            String.format("headers: %s\nresponse body: %s", httpException.getHeaders(), body));
  }

  @Test
  public void shouldParseHttpErrorResponseDetailsWhenConversionErrorHasOccurred() {

    var converter = new JacksonConverter(objectMapper);
    var responseBody = new HashMap<String, String>();
    var headers = new ArrayList<Header>();
    var header = new Header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

    headers.add(header);
    responseBody.put("error", "400 - Bad request, application name cannot be empty");

    Response response =
        new Response(
            "/deployment/evaluateHealth",
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.name(),
            headers,
            new MockTypedInput(converter, responseBody));

    RetrofitError conversionError =
        RetrofitError.conversionError(
            "https://foo.com/deployment/evaluateHealth",
            response,
            new JacksonConverter(),
            null,
            new ConversionException("Failed to parse response"));

    SpinnakerConversionException conversionException =
        new SpinnakerConversionException(conversionError);

    String logMessageOnSpinConversionException =
        monitoredDeployBaseTask.getErrorMessage(conversionException);

    assertThat(logMessageOnSpinConversionException).isEqualTo("<NO RESPONSE>");
  }

  @Test
  void shouldReturnDefaultLogMsgWhenNetworkErrorHasOccurred() {

    RetrofitError networkError =
        RetrofitError.networkError(
            "https://foo.com/deployment/evaluateHealth",
            new IOException("Failed to connect to the host : foo.com"));

    SpinnakerNetworkException networkException = new SpinnakerNetworkException(networkError);

    String logMessageOnSpinNetworkException =
        monitoredDeployBaseTask.getErrorMessage(networkException);

    assertThat(logMessageOnSpinNetworkException).isEqualTo("<NO RESPONSE>");
  }

  @Test
  void shouldReturnDefaultLogMsgWhenUnexpectedErrorHasOccurred() {

    RetrofitError unexpectedError =
        RetrofitError.unexpectedError(
            "https://foo.com/deployment/evaluateHealth",
            new IOException("Failed to connect to the host : foo.com"));

    SpinnakerServerException serverException = new SpinnakerServerException(unexpectedError);

    String logMessageOnSpinServerException =
        monitoredDeployBaseTask.getErrorMessage(serverException);

    assertThat(logMessageOnSpinServerException).isEqualTo("<NO RESPONSE>");
  }

  @Test
  void shouldReturnHeadersAndErrorMessageWhenErrorResponseBodyIsNull() {

    var converter = new JacksonConverter(objectMapper);
    var headers = new ArrayList<Header>();
    var header = new Header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    monitoredDeployBaseTask.objectMapper = mock(ObjectMapper.class);

    headers.add(header);

    Response response =
        new Response(
            "/deployment/evaluateHealth",
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.name(),
            headers,
            new MockTypedInput(converter, null));

    RetrofitError httpError =
        RetrofitError.httpError(
            "https://foo.com/deployment/evaluateHealth", response, converter, null);

    SpinnakerHttpException httpException = new SpinnakerHttpException(httpError);

    String logMessageOnSpinHttpException = monitoredDeployBaseTask.getErrorMessage(httpException);
    String body = "Failed to serialize the error response body";

    assertThat(logMessageOnSpinHttpException)
        .isEqualTo(
            String.format("headers: %s\nresponse body: %s", httpException.getHeaders(), body));
  }

  static class MockTypedInput implements TypedInput {
    private final Converter converter;
    private final Object body;

    private byte[] bytes;

    MockTypedInput(Converter converter, Object body) {
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
