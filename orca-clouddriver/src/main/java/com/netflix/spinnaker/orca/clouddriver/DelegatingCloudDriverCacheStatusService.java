/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver;

import com.netflix.spinnaker.kork.web.selector.SelectableService;
import java.util.Collection;
import java.util.Map;

public class DelegatingCloudDriverCacheStatusService
    extends DelegatingClouddriverService<CloudDriverCacheStatusService>
    implements CloudDriverCacheStatusService {

  public DelegatingCloudDriverCacheStatusService(SelectableService selectableService) {
    super(selectableService);
  }

  @Override
  public Collection<Map> pendingForceCacheUpdates(String cloudProvider, String type) {
    return getService().pendingForceCacheUpdates(cloudProvider, type);
  }

  @Override
  public Collection<Map> pendingForceCacheUpdatesById(
      String cloudProvider, String type, String id) {
    return getService().pendingForceCacheUpdatesById(cloudProvider, type, id);
  }
}
