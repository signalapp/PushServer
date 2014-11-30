package org.whispersystems.pushserver.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.whispersystems.pushserver.auth.Server;

import javax.validation.Valid;
import java.util.List;

public class AuthenticationConfiguration {

  @JsonProperty
  @Valid
  private List<Server> servers;


  public List<Server> getServers() {
    return servers;
  }
}
