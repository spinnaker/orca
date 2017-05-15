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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.LinkedHashSet;

public class StageDefinition implements Identifiable, Conditional {

  private String id;
  private String name;
  private InjectionRule inject;
  private Set<String> dependsOn;
  private String type;
  private Map<String, Object> config;
  private List<Map<String, Object>> notifications;
  private String comments;
  private List<String> when;
  private InheritanceControl inheritanceControl;
  private Boolean removed = false;

  private Set<String> requisiteStageRefIds = new LinkedHashSet<>();

  public static class InjectionRule {

    private Boolean first = false;
    private Boolean last = false;
    private List<String> before;
    private List<String> after;

    public Boolean getFirst() {
      return first;
    }

    public void setFirst(Boolean first) {
      this.first = first;
    }

    public Boolean getLast() {
      return last;
    }

    public void setLast(Boolean last) {
      this.last = last;
    }

    public List<String> getBefore() {
      return before;
    }

    public void setBefore(List<String> before) {
      this.before = before;
    }

    public List<String> getAfter() {
      return after;
    }

    public void setAfter(List<String> after) {
      this.after = after;
    }

    public boolean hasAny() {
      return first || last || before != null || after != null;
    }

    public boolean hasMany() {
      int count = 0;
      if (first) {
        count += 1;
      }
      if (last) {
        count += 1;
      }
      if (before != null) {
        count += 1;
      }
      if (after != null) {
        count += 1;
      }
      return count > 1;
    }
  }

  public static class InheritanceControl {

    private Collection<Rule> merge;
    private Collection<Rule> replace;
    private Collection<Rule> remove;

    public static class Rule {
      String path;
      Object value;

      public String getPath() {
        return path;
      }

      public void setPath(String path) {
        this.path = path;
      }

      public Object getValue() {
        return value;
      }

      public void setValue(Object value) {
        this.value = value;
      }
    }

    public Collection<Rule> getMerge() {
      return Optional.ofNullable(merge).orElse(new ArrayList<>());
    }

    public void setMerge(Collection<Rule> merge) {
      this.merge = merge;
    }

    public Collection<Rule> getReplace() {
      return Optional.ofNullable(replace).orElse(new ArrayList<>());
    }

    public void setReplace(Collection<Rule> replace) {
      this.replace = replace;
    }

    public Collection<Rule> getRemove() {
      return Optional.ofNullable(remove).orElse(new ArrayList<>());
    }

    public void setRemove(Collection<Rule> remove) {
      this.remove = remove;
    }
  }

  @Override
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return Optional.ofNullable(name).orElse(id);
  }

  public void setName(String name) {
    this.name = name;
  }

  public InjectionRule getInject() {
    return inject;
  }

  public void setInject(InjectionRule inject) {
    this.inject = inject;
  }

  public Set<String> getDependsOn() {
    return Optional.ofNullable(dependsOn).orElse(new LinkedHashSet<>());
  }

  public void setDependsOn(Set<String> dependsOn) {
    this.dependsOn = dependsOn;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Map<String, Object> getConfig() {
    return config;
  }

  public void setConfig(Map<String, Object> config) {
    this.config = config;
  }

  public List<Map<String, Object>> getNotifications() {
    return notifications;
  }

  public void setNotifications(List<Map<String, Object>> notifications) {
    this.notifications = notifications;
  }

  public String getComments() {
    return comments;
  }

  public void setComments(String comments) {
    this.comments = comments;
  }

  public List<String> getWhen() {
    return when;
  }

  @Override
  public void setRemove() {
    this.removed = true;
  }

  public Boolean getRemoved() {
    return removed;
  }


  public void setWhen(List<String> when) {
    this.when = when;
  }

  public InheritanceControl getInheritanceControl() {
    return inheritanceControl;
  }

  public void setInheritanceControl(InheritanceControl inheritanceControl) {
    this.inheritanceControl = inheritanceControl;
  }

  public Set<String> getRequisiteStageRefIds() {
    return requisiteStageRefIds;
  }

  public void setRequisiteStageRefIds(Set<String> requisiteStageRefIds) {
    this.requisiteStageRefIds = requisiteStageRefIds;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    StageDefinition that = (StageDefinition) o;

    return id != null ? id.equals(that.id) : that.id == null;

  }

  @Override
  public int hashCode() {
    return id != null ? id.hashCode() : 0;
  }
}
