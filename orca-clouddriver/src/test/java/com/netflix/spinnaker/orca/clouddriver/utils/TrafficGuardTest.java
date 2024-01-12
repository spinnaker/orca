/*
 * Copyright 2024 OpsMx, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.utils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService;
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.Location;
import com.netflix.spinnaker.orca.front50.Front50Service;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit.converter.ConversionException;

public class TrafficGuardTest {

  private Front50Service front50Service = mock(Front50Service.class);

  private Registry registry = new NoopRegistry();
  private DynamicConfigService dynamicConfigService = mock(DynamicConfigService.class);
  private CloudDriverService cloudDriverService = mock(CloudDriverService.class);

  private TrafficGuard trafficGuard =
      new TrafficGuard(
          Optional.of(front50Service), registry, dynamicConfigService, cloudDriverService);

  private Location location = new Location(Location.Type.REGION, "us-east-1");

  @Test
  public void testHasDisableLockThrowsRetrofitNetworkError() {

    var app = "testapp";
    var url = "https://front50service.com/v2/applications/" + app;
    String cluster = "testCluster";
    String detail = "a test moniker";
    String stack = "testStack";
    Integer sequence = 0;

    RetrofitError networkError =
        RetrofitError.networkError(
            url, new IOException("Failed to connect to the host : front50.service.com"));

    when(front50Service.get(app)).thenThrow(networkError);

    assertThatThrownBy(
            () ->
                trafficGuard.hasDisableLock(
                    new Moniker(app, cluster, detail, stack, sequence), "test", location))
        .isExactlyInstanceOf(RetrofitError.class)
        .hasMessage("Failed to connect to the host : front50.service.com")
        .hasCause(networkError.getCause());
  }

  @Test
  public void testHasDisableLockThrowsRetrofitHttpError() {

    var app = "testapp";
    var url = "https://front50service.com/v2/applications/" + app;
    String cluster = "testCluster";
    String detail = "a test moniker";
    String stack = "testStack";
    Integer sequence = 0;

    Response response =
        new Response(
            url,
            HttpStatus.CONFLICT.value(),
            HttpStatus.CONFLICT.name(),
            Collections.emptyList(),
            null);

    RetrofitError httpError = RetrofitError.httpError(url, response, null, null);

    when(front50Service.get(app)).thenThrow(httpError);

    assertThatThrownBy(
            () ->
                trafficGuard.hasDisableLock(
                    new Moniker(app, cluster, detail, stack, sequence), "test", location))
        .isExactlyInstanceOf(RetrofitError.class)
        .hasMessage(HttpStatus.CONFLICT.value() + " " + HttpStatus.CONFLICT.name())
        .hasCause(httpError.getCause());
  }

  @Test
  public void testHasDisableLockThrowsRetrofitConversionError() {

    var app = "testapp";
    var url = "https://front50service.com/v2/applications/" + app;
    String cluster = "testCluster";
    String detail = "a test moniker";
    String stack = "testStack";
    Integer sequence = 0;

    Response response =
        new Response(
            url,
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.name(),
            Collections.emptyList(),
            null);

    RetrofitError conversionError =
        RetrofitError.conversionError(
            url,
            response,
            null,
            null,
            new ConversionException("Failed to convert http error body"));

    when(front50Service.get(app)).thenThrow(conversionError);

    assertThatThrownBy(
            () ->
                trafficGuard.hasDisableLock(
                    new Moniker(app, cluster, detail, stack, sequence), "test", location))
        .isExactlyInstanceOf(RetrofitError.class)
        .hasMessage("Failed to convert http error body")
        .hasCause(conversionError.getCause());
  }

  @Test
  public void testHasDisableLockThrowsRetrofitUnexpectedError() {

    var app = "testapp";
    var url = "https://front50service.com/v2/applications/" + app;
    String cluster = "testCluster";
    String detail = "a test moniker";
    String stack = "testStack";
    Integer sequence = 0;

    RetrofitError unexpectedError =
        RetrofitError.unexpectedError(
            url, new IOException("Something went wrong, please try again"));

    when(front50Service.get(app)).thenThrow(unexpectedError);

    assertThatThrownBy(
            () ->
                trafficGuard.hasDisableLock(
                    new Moniker(app, cluster, detail, stack, sequence), "test", location))
        .isExactlyInstanceOf(RetrofitError.class)
        .hasMessage("Something went wrong, please try again")
        .hasCause(unexpectedError.getCause());
  }
}
