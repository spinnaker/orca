/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup;

import com.netflix.spinnaker.orca.pipeline.model.Stage;

import java.util.List;
import java.util.Map;

/**
 * cloning of server groups can vary across cloud providers. A ServerGroupCloner
 * is a cloud-provider specific way to hook into the Orca infrastructure.
 */
public interface ServerGroupCloner {
 String OPERATION = "cloneServerGroup";

  /**
   * @return a list of operation descriptors. Each operation should be a single entry map keyed by the operation name,
   * with the operation map as the value. (A list of maps of maps? We must go deeper...)
   */
  List<Map> getOperations(Stage stage);

  /**
   * @return The cloud provider type that this object supports.
   */
  String getCloudProvider();
}
