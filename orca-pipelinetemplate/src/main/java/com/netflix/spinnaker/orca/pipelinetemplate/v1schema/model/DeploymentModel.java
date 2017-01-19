/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.MapMerge;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

//wire model
public class DeploymentModel {
  Map<String, ExtensibleNamedHashMap> clusters = new HashMap<>();
  Map<String, ExtensibleNamedHashMap> loadBalancers = new HashMap<>();
  Map<String, ExtensibleNamedHashMap> accounts = new HashMap<>();
  Map<String, ExtensibleNamedHashMap> environments = new HashMap<>();

  public Map<String, ExtensibleNamedHashMap> getClusters() {
    return clusters;
  }

  public Map<String, ExtensibleNamedHashMap> getLoadBalancers() {
    return loadBalancers;
  }

  public Map<String, ExtensibleNamedHashMap> getAccounts() {
    return accounts;
  }

  public Map<String, ExtensibleNamedHashMap> getEnvironments() {
    return environments;
  }
}


class Ingress {

}

class ClusterRequirements {
  static class Resources {
    String cpu;
    String ram;
    String disk;
    String network;

    public String getCpu() {
      return cpu;
    }

    public void setCpu(String cpu) {
      this.cpu = cpu;
    }

    public String getRam() {
      return ram;
    }

    public void setRam(String ram) {
      this.ram = ram;
    }

    public String getDisk() {
      return disk;
    }

    public void setDisk(String disk) {
      this.disk = disk;
    }

    public String getNetwork() {
      return network;
    }

    public void setNetwork(String network) {
      this.network = network;
    }
  }

  Resources resources;
  List<String> membership;

  Ingress ingress;

  public Resources getResources() {
    return resources;
  }

  public void setResources(Resources resources) {
    this.resources = resources;
  }

  public List<String> getMembership() {
    return membership;
  }

  public void setMembership(List<String> membership) {
    this.membership = membership;
  }

  public Ingress getIngress() {
    return ingress;
  }

  public void setIngress(Ingress ingress) {
    this.ingress = ingress;
  }
}

class Region {
  String name;
  List<String> zones;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<String> getZones() {
    return zones;
  }

  public void setZones(List<String> zones) {
    this.zones = zones;
  }
}
class Account {
  String name;
  String cloudProvider;
  Map<String, Region> regions;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getCloudProvider() {
    return cloudProvider;
  }

  public void setCloudProvider(String cloudProvider) {
    this.cloudProvider = cloudProvider;
  }

  public Map<String, Region> getRegions() {
    return regions;
  }

  public void setRegions(Map<String, Region> regions) {
    this.regions = regions;
  }
}

class Environment {
  Account account;
  String stack;
  String detail;
  Map<String, Region> regions;

  public Account getAccount() {
    return account;
  }

  public void setAccount(Account account) {
    this.account = account;
  }

  public String getStack() {
    return stack;
  }

  public void setStack(String stack) {
    this.stack = stack;
  }

  public String getDetail() {
    return detail;
  }

  public void setDetail(String detail) {
    this.detail = detail;
  }

  public Map<String, Region> getRegions() {
    return regions;
  }

  public void setRegions(Map<String, Region> regions) {
    this.regions = regions;
  }
}

class LoadBalancer {
  String name;
  String type;
  Ingress ingress;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Ingress getIngress() {
    return ingress;
  }

  public void setIngress(Ingress ingress) {
    this.ingress = ingress;
  }
}

// enhm -> clustermodel -> cluster
class Cluster {
  String name;
  String application;
  Environment environment;
  ClusterRequirements requires;
  List<LoadBalancer> loadBalancers;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getApplication() {
    return application;
  }

  public void setApplication(String application) {
    this.application = application;
  }

  public Environment getEnvironment() {
    return environment;
  }

  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  public ClusterRequirements getRequires() {
    return requires;
  }

  public void setRequires(ClusterRequirements requires) {
    this.requires = requires;
  }

  public List<LoadBalancer> getLoadBalancers() {
    return loadBalancers;
  }

  public void setLoadBalancers(List<LoadBalancer> loadBalancers) {
    this.loadBalancers = loadBalancers;
  }
}


class DeploymentModelResolver {
  private ExtensibleContentResolver<DeploymentModel> resolver;
  private final ObjectMapper mapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  public DeploymentModelResolver(List<DeploymentModel> sources) {
    this.resolver = new ExtensibleContentResolver<>(sources);
  }

  public Cluster getCluster(String name) {
    ExtensibleNamedHashMap clusterContent = getResolved(name, DeploymentModel::getClusters);
    clusterContent.computeIfPresent("environment", (k, environment) -> getEnvironment(environment));
    clusterContent.computeIfPresent("loadBalancers", (k, loadBalancers) -> {
      if (loadBalancers instanceof Collection) {
        return ((Collection<Object>) loadBalancers).stream().map(this::getLoadBalancer).collect(Collectors.toList());
      } else {
        throw new IllegalStateException("expected collection");
      }
    });

    return mapper.convertValue(clusterContent, Cluster.class);
  }

  public Environment getEnvironment(Object nameOrPayload) {
    ExtensibleNamedHashMap environmentContent = getResolved(nameOrPayload, DeploymentModel::getEnvironments);
    environmentContent.computeIfPresent("account", (k, accountPayload) -> getAccount(accountPayload));
    return mapper.convertValue(environmentContent, Environment.class);
  }

  public Account getAccount(Object nameOrPayload) {
    ExtensibleNamedHashMap accountContent = getResolved(nameOrPayload, DeploymentModel::getAccounts);
    return mapper.convertValue(accountContent, Account.class);
  }

  public LoadBalancer getLoadBalancer(Object nameOrPayload) {
    ExtensibleNamedHashMap loadBalancerContent = getResolved(nameOrPayload, DeploymentModel::getLoadBalancers);
    return mapper.convertValue(loadBalancerContent, LoadBalancer.class);
  }

  private ExtensibleNamedHashMap getResolved(Object nameOrPayload, Function<DeploymentModel, Map<String, ExtensibleNamedHashMap>> type) {
    if (nameOrPayload instanceof String) {
      String name = nameOrPayload.toString();
      ExtensibleNamedHashMap resolved = resolver.resolve(name, type).orElseThrow(NoSuchElementException::new);
      if (!name.equals(resolved.getName())) {
        resolved.put("name", name);
      }
      return resolved;
    } else if (nameOrPayload instanceof Map) {
      ExtensibleNamedHashMap content = new ExtensibleNamedHashMap((Map<String, Object>) nameOrPayload);
      String name = content.getName();
      if (name == null) {
        name = "inlineOverride";
        content.put("name", name);
      }
      DeploymentModel dm = new DeploymentModel();
      type.apply(dm).put(name, content);
      return new ExtensibleContentResolver<>(resolver, Arrays.asList(dm)).resolve(name, type).orElseThrow(NoSuchElementException::new);
    } else {
      throw new IllegalStateException("invalid content supplied: " + nameOrPayload.getClass().getSimpleName());
    }
  }
}
class ExtensibleContentResolver<T> {
  private final Optional<ExtensibleContentResolver<T>> chain;
  private final List<T> sources;

  public ExtensibleContentResolver(List<T> sources) {
    this(null, sources);
  }

  public ExtensibleContentResolver(ExtensibleContentResolver<T> chain, List<T> sources) {
    this.chain = Optional.ofNullable(chain);
    this.sources = sources;
  }

  public Optional<ExtensibleNamedHashMap> resolve(String name, Function<T, Map<String, ExtensibleNamedHashMap>> type) {
    if (name == null || name.isEmpty()) {
      return Optional.empty();
    }
    Optional<ExtensibleNamedHashMap> base = sources.stream().map(type).filter(enhm -> enhm.containsKey(name)).findFirst().map(enhm -> enhm.get(name));
    ExtensibleNamedHashMap baseContent = base.orElseGet(() -> {
        ExtensibleNamedHashMap chainContent = chain.flatMap((p) -> p.resolve(name, type)).orElse(null);
        return chainContent;
    });

    return Optional.ofNullable(baseContent).map(enhm -> {
      if (enhm.getExtends() != null && !enhm.isEmpty()) {
        ExtensibleNamedHashMap parent = resolve(enhm.getExtends(), type).orElseThrow(NoSuchElementException::new);
        ExtensibleNamedHashMap result = new ExtensibleNamedHashMap();
        result.putAll(MapMerge.merge(parent, enhm));
        return result;
      } else {
        return enhm;
      }
    });
  }
}

interface ExtensibleContent {
  default String getExtends() { return null; }
}

class ExtensibleNamedHashMap extends NamedHashMap implements ExtensibleContent {
  public ExtensibleNamedHashMap() {
    super();
  }

  public ExtensibleNamedHashMap(Map<String, Object> content) {
    super(content);
  }

  @Override
  public String getExtends() {
    Object ext = get("extends");
    if (ext == null) {
      return null;
    }
    return ext.toString();
  }
}
