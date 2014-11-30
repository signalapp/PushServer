package org.whispersystems.pushserver.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import org.hibernate.validator.constraints.NotEmpty;

public class Server {

  @JsonProperty
  @NotEmpty
  private String name;

  @JsonProperty
  @NotEmpty
  private String password;

  public Server() {}

  @VisibleForTesting
  public Server(String name, String password) {
    this.name     = name;
    this.password = password;
  }

  public String getName() {
    return name;
  }

  public String getPassword() {
    return password;
  }
}
