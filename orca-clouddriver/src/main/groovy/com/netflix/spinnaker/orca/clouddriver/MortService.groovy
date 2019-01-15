/*
 * Copyright 2014 Netflix, Inc.
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


package com.netflix.spinnaker.orca.clouddriver

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonRawValue
import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.databind.JsonNode
import groovy.transform.Canonical
import groovy.transform.EqualsAndHashCode
import retrofit.http.GET
import retrofit.http.Path
import retrofit.http.Query

interface MortService {
  @GET("/securityGroups/{account}/{type}/{region}/{securityGroupName}")
  SecurityGroup getSecurityGroup(
    @Path("account") String account,
    @Path("type") String type,
    @Path("securityGroupName") String securityGroupName,
    @Path("region") String region)

  @GET("/securityGroups/{account}/{type}/{region}/{securityGroupName}")
  SecurityGroup getSecurityGroup(
    @Path("account") String account,
    @Path("type") String type,
    @Path("securityGroupName") String securityGroupName,
    @Path("region") String region,
    @Query("vpcId") String vpcId)

  @GET("/vpcs")
  Collection<VPC> getVPCs()

  @GET("/search")
  List<SearchResult> getSearchResults(@Query("q") String searchTerm,
                                      @Query("type") String type)

  @GET("/credentials/{account}")
  AccountDetails getAccountDetails(@Path("account") String account)

  @GET("/credentials?expand=true")
  List<AccountDetails> getAllAccountDetails()

  @JsonIgnoreProperties(ignoreUnknown = false)
  static class AccountDetails {
    String name
    String accountId
    String type
    String providerVersion
    Collection<String> requiredGroupMembership = []
    String skin
    Map<String, Collection<String>> permissions
    String accountType
    String environment
    Boolean challengeDestructiveActions
    Boolean primaryAccount
    String cloudProvider
    private Map<String, Object> details = new HashMap<String, Object>()

    @JsonAnyGetter
    public Map<String,Object> details() {
      return details
    }

    @JsonAnySetter
    public void set(String name, Object value) {
      details.put(name, value)
    }
  }

  static class SearchResult {
    int totalMatches
    int pageNumber
    int pageSize
    String platform
    String query
    List<Map> results
  }

  @EqualsAndHashCode
  static class SecurityGroup {
    String type
    String id
    String name
    Object description
    String accountName
    String region
    String vpcId
    List<Map> inboundRules

    //Custon Jackson settings to handle either String or JSON Object for description
    @JsonRawValue
    String getDescription() {
      return description != null ? description.toString() : null
    }

    void setDescription(String description) {
      this.description = description
    }

    @JsonSetter('description')
    void setDescription(JsonNode node) {
      // If it's a simple text node, unwrap it to its value
      if (node.textual) {
        this.description = node.textValue()
      } else {
        this.description = node
      }
    }

    @Canonical
    static class SecurityGroupIngress {
      String name
      int startPort
      int endPort
      String type
    }

    static List<SecurityGroupIngress> applyMappings(Map<String, String> securityGroupMappings,
                                                    List<SecurityGroupIngress> securityGroupIngress) {
      securityGroupMappings = securityGroupMappings ?: [:]
      securityGroupIngress.collect {
        it.name = securityGroupMappings[it.name as String] ?: it.name
        it
      }
    }

    static List<SecurityGroupIngress> filterForSecurityGroupIngress(MortService mortService,
                                                                    SecurityGroup currentSecurityGroup) {
      if (currentSecurityGroup == null) {
        return []
      }

      currentSecurityGroup.inboundRules.findAll {
        it.securityGroup
      }.collect { Map inboundRule ->
        def securityGroupName = inboundRule.securityGroup.name
        if (!securityGroupName) {
          def searchResults = mortService.getSearchResults(inboundRule.securityGroup.id as String, "securityGroups")
          securityGroupName = searchResults ? searchResults[0].results.getAt(0)?.name : inboundRule.securityGroup.id
        }

        inboundRule.portRanges.collect {
          new SecurityGroupIngress(
            securityGroupName as String, it.startPort as int, it.endPort as int, inboundRule.protocol as String
          )
        }
      }.flatten()
    }

    static SecurityGroup findById(MortService mortService, String securityGroupId) {
      def searchResults = mortService.getSearchResults(securityGroupId, "securityGroups")
      def securityGroup = searchResults?.getAt(0)?.results?.getAt(0)

      if (!securityGroup?.name) {
        throw new IllegalArgumentException("Security group (${securityGroupId}) does not exist")
      }

      return mortService.getSecurityGroup(
        securityGroup.account as String,
        searchResults[0].platform,
        securityGroup.name as String,
        securityGroup.region as String,
        securityGroup.vpcId as String
      )
    }
  }

  static class VPC {
    String id
    String name
    String region
    String account

    static VPC findForRegionAndAccount(Collection<MortService.VPC> allVPCs,
                                       String sourceVpcIdOrName,
                                       String region,
                                       String account) {
      def sourceVpc = allVPCs.find { it.id == sourceVpcIdOrName || (sourceVpcIdOrName.equalsIgnoreCase(it.name) && it.region == region && it.account == account) }
      def targetVpc = allVPCs.find { it.name == sourceVpc?.name && it.region == region && it.account == account }

      if (!targetVpc) {
        throw new IllegalStateException("No matching VPC found (vpcId: ${sourceVpcIdOrName}, region: ${region}, account: ${account}")
      }

      return targetVpc
    }
  }
}
