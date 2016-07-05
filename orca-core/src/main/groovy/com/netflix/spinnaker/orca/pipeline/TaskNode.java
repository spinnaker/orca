/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Consumer;
import com.google.common.annotations.VisibleForTesting;
import com.netflix.spinnaker.orca.ExecutionStatus;
import lombok.Value;

/**
 * A node in a {@link TaskGraph} which can be either an individual task or a
 * sub-graph.
 */
public interface TaskNode {

  static TaskGraph build(Consumer<Builder> closure) {
    Builder builder = new Builder();
    closure.accept(builder);
    return builder.build();
  }

  static Builder Builder() {
    return new Builder();
  }

  class Builder {
    private final List<TaskNode> graph = new ArrayList<>();

    /**
     * Adds a task to the current graph.
     *
     * @param name              the name of the task.
     * @param implementingClass the class that implements the task.
     * @return this builder with the new task appended.
     */
    public Builder withTask(String name, Class<? extends com.netflix.spinnaker.orca.Task> implementingClass) {
      graph.add(new TaskDefinition(name, implementingClass));
      return this;
    }

    /**
     * Adds a sub-graph of tasks that may loop if any of them return
     * {@link ExecutionStatus#REDIRECT}. The sub-graph will run after any
     * previously added tasks and before any subsequently added ones. If the
     * final task in the sub-graph returns {@link ExecutionStatus#REDIRECT} the
     * tasks in the sub-graph will be run again. If it returns
     * {@link ExecutionStatus#SUCCEEDED} the sub-graph will exit.
     *
     * @param subGraph a lambda that defines the tasks for the sub-graph by
     *                 adding them to a @{link {@link TaskNode#Builder}.
     * @return this builder with the sub-graph appended.
     */
    public Builder withLoop(Consumer<Builder> subGraph) {
      Builder subGraphBuilder = new Builder();
      subGraph.accept(subGraphBuilder);
      graph.add(subGraphBuilder.build());
      return this;
    }

    TaskGraph build() {
      return new TaskGraph(graph);
    }
  }

  /**
   * A graph or sub-graph of tasks.
   */
  class TaskGraph implements TaskNode, Iterable<TaskNode> {

    private final List<TaskNode> graph;

    @VisibleForTesting
    public TaskGraph(List<TaskNode> graph) {
      this.graph = graph;
    }

    @Override
    public Iterator<TaskNode> iterator() {
      return graph.iterator();
    }

    public ListIterator<TaskNode> listIterator() {
      return graph.listIterator();
    }
  }

  /**
   * An individual task.
   */
  @Value
  class TaskDefinition implements TaskNode {
    String name;
    Class<? extends com.netflix.spinnaker.orca.Task> implementingClass;
  }
}
