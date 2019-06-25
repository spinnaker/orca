package com.netflix.spinnaker.orca.pipeline.model.execution;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;

import com.google.common.collect.ImmutableSet;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import com.netflix.spinnaker.security.User;
import java.io.Serializable;
import java.util.Collection;
import java.util.Optional;
import javax.annotation.Nullable;

public class AuthenticationDetails implements Serializable {

  private String user;

  public @Nullable String getUser() {
    return user;
  }

  public void setUser(@Nullable String user) {
    this.user = user;
  }

  private Collection<String> allowedAccounts = emptySet();

  public Collection<String> getAllowedAccounts() {
    return ImmutableSet.copyOf(allowedAccounts);
  }

  public void setAllowedAccounts(Collection<String> allowedAccounts) {
    this.allowedAccounts = ImmutableSet.copyOf(allowedAccounts);
  }

  public AuthenticationDetails() {}

  public AuthenticationDetails(String user, String... allowedAccounts) {
    this.user = user;
    this.allowedAccounts = asList(allowedAccounts);
  }

  public static Optional<AuthenticationDetails> build() {
    Optional<String> spinnakerUserOptional = AuthenticatedRequest.getSpinnakerUser();
    Optional<String> spinnakerAccountsOptional = AuthenticatedRequest.getSpinnakerAccounts();
    if (spinnakerUserOptional.isPresent() || spinnakerAccountsOptional.isPresent()) {
      return Optional.of(
          new AuthenticationDetails(
              spinnakerUserOptional.orElse(null),
              spinnakerAccountsOptional.map(s -> s.split(",")).orElse(new String[0])));
    }

    return Optional.empty();
  }

  public Optional<User> toKorkUser() {
    return Optional.ofNullable(user)
        .map(
            it -> {
              User user = new User();
              user.setEmail(it);
              user.setAllowedAccounts(allowedAccounts);
              return user;
            });
  }
}
