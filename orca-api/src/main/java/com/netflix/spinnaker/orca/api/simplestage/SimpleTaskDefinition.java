/*
 * Copyright 2019 Armory, Inc.
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

package com.netflix.spinnaker.orca.api.simplestage;

import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode;
import com.netflix.spinnaker.orca.api.pipeline.graph.TaskNode.DefinedTask;
import javax.annotation.Nonnull;

public class SimpleTaskDefinition implements TaskNode, DefinedTask {
  private final String name;
  private final Class<? extends SimpleStage> implementingClass;

  public SimpleTaskDefinition(
      @Nonnull String name, @Nonnull Class<? extends SimpleStage> implementingClass) {
    this.name = name;
    this.implementingClass = implementingClass;
  }

  public @Nonnull String getName() {
    return name;
  }

  public @Nonnull Class<? extends SimpleStage> getImplementingClass() {
    return implementingClass;
  }

  @Override
  public @Nonnull String getImplementingClassName() {
    return getImplementingClass().getCanonicalName();
  }
}
