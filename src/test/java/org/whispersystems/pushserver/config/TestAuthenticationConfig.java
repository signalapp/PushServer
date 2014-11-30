package org.whispersystems.pushserver.config;

import org.whispersystems.pushserver.auth.Server;

import java.util.LinkedList;
import java.util.List;

public class TestAuthenticationConfig extends AuthenticationConfiguration {
  @Override
  public List<Server> getServers() {
    return new LinkedList<Server>() {{
      add(new Server("textsecure", "foobar"));
      add(new Server("redphone", "foobaz"));
    }};
  }
}
