package org.whispersystems.pushserver.util;

import org.whispersystems.pushserver.auth.Server;
import org.whispersystems.pushserver.config.AuthenticationConfiguration;

import java.util.LinkedList;
import java.util.List;

public class MockAuthenticationConfig extends AuthenticationConfiguration {
  @Override
  public List<Server> getServers() {
    return new LinkedList<Server>() {{
      add(new Server("textsecure", "foobar"));
      add(new Server("redphone", "foobaz"));
    }};
  }
}
