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

package com.netflix.spinnaker.orca.clouddriver.pollers;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.exceptions.SystemException;
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService;
import com.netflix.spinnaker.orca.clouddriver.config.PollerConfigurationProperties;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock;
import com.netflix.spinnaker.orca.pipeline.ExecutionLauncher;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit.mime.TypedByteArray;

public class EphemeralServerGroupsPollerTest {

  private Front50Service front50Service;

  private EphemeralServerGroupsPoller ephemeralServerGroupsPoller;

  private NotificationClusterLock notificationClusterLock;
  private ObjectMapper objectMapper;
  private CloudDriverService cloudDriverService;
  private RetrySupport retrySupport;
  private Registry registry;
  private ExecutionLauncher executionLauncher;
  private PollerConfigurationProperties pollerConfigurationProperties;

  @BeforeEach
  public void setup() {

    front50Service = mock(Front50Service.class);
    notificationClusterLock = mock(NotificationClusterLock.class);
    objectMapper = new ObjectMapper();
    cloudDriverService = mock(CloudDriverService.class);
    retrySupport = mock(RetrySupport.class);
    registry = mock(Registry.class);
    executionLauncher = mock(ExecutionLauncher.class);
    pollerConfigurationProperties = mock(PollerConfigurationProperties.class);
    ephemeralServerGroupsPoller =
        new EphemeralServerGroupsPoller(
            notificationClusterLock,
            objectMapper,
            cloudDriverService,
            retrySupport,
            registry,
            executionLauncher,
            front50Service,
            pollerConfigurationProperties);
  }

  @Test
  public void testSystemExceptionStackTraceWhenRetrofitHttpErrorOccurs() {
    var application = "testapp";

    var url = "https://front50service.com/v2/applications/" + application;
    Response mockResponse =
        new Response(
            url,
            HttpStatus.NOT_ACCEPTABLE.value(),
            HttpStatus.NOT_ACCEPTABLE.name(),
            Collections.emptyList(),
            new TypedByteArray(
                "application/json", "{ \"error\": \"application testapp not found\"}".getBytes()));

    RetrofitError httpError = RetrofitError.httpError(url, mockResponse, null, null);

    when(front50Service.get(application)).thenThrow(httpError);

    SystemException systemException = new SystemException(httpError.getMessage(), httpError);

    System.out.println("cause : " + systemException.getCause());
    assertThatThrownBy(() -> ephemeralServerGroupsPoller.getApplication(application))
        .hasCause(systemException.getCause())
        .hasCauseExactlyInstanceOf(RetrofitError.class);
  }
}
