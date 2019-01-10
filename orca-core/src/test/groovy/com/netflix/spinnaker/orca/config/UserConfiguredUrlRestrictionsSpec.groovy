package com.netflix.spinnaker.orca.config

import spock.lang.Specification

class UserConfiguredUrlRestrictionsSpec extends Specification {

  def "validateURI fails if URI is a rejected IP"() {
    setup:
    def urlRestrictions = new UserConfiguredUrlRestrictions.Builder()
      .withRejectedIps(Arrays.asList("10.63.245.46"))
      .build()

    when:
    urlRestrictions.validateURI("10.63.245.46")

    then:
    thrown(IllegalArgumentException)
  }

  def "validateURI fails if URI is within a rejected IP range"() {
    setup:
    def urlRestrictions = new UserConfiguredUrlRestrictions.Builder()
      .withRejectedIps(Arrays.asList("10.60.0.0/14"))
      .build()

    when:
    urlRestrictions.validateURI("10.60.0.19")

    then:
    thrown(IllegalArgumentException)
  }

  def "validateURI fails if URI is not a rejected IP or within a rejected IP range"() {
    setup:
    def urlRestrictions = new UserConfiguredUrlRestrictions.Builder()
      .withRejectedIps(Arrays.asList("10.63.245.46", "10.60.0.0/14"))
      .build()

    when:
    urlRestrictions.validateURI("http://example.com/")

    then:
    noExceptionThrown()
  }
}
