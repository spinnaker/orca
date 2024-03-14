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

package com.netflix.spinnaker.orca.clouddriver.tasks.job;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.orca.clouddriver.KatoRestService;
import com.netflix.spinnaker.orca.front50.Front50Service;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit.mime.TypedByteArray;

public class JobUtilsTest {

  private RetrySupport retrySupport;
  private KatoRestService katoRestService;
  private Front50Service front50Service;

  private JobUtils jobUtils;

  @BeforeEach
  public void setup() {
    retrySupport = mock(RetrySupport.class);
    katoRestService = mock(KatoRestService.class);
    front50Service = mock(Front50Service.class);
    jobUtils = new JobUtils(retrySupport, katoRestService, Optional.ofNullable(front50Service));
  }

  @Test
  public void testErrorMsgWhenRetrofitHttpErrorOccurs() {

    var app = "testapp";
    var url = "https://front50service.com/v2/applications/" + app;

    Response mockResponse =
        new Response(
            url,
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.name(),
            Collections.emptyList(),
            new TypedByteArray("application/json", "{ \"error\": \"BAD REQUEST\"}".getBytes()));

    RetrofitError httpError = RetrofitError.httpError(url, mockResponse, null, null);

    when(front50Service.get(app)).thenThrow(httpError);

    assertThatThrownBy(() -> jobUtils.applicationExists(app))
        .isExactlyInstanceOf(RetrofitError.class)
        .hasMessage(HttpStatus.BAD_REQUEST.value() + " " + HttpStatus.BAD_REQUEST.name());
  }
}
