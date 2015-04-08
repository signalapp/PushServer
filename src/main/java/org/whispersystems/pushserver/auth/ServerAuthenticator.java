package org.whispersystems.pushserver.auth;

import com.google.common.base.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.pushserver.config.AuthenticationConfiguration;

import java.security.MessageDigest;
import java.util.List;

import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;

public class ServerAuthenticator implements Authenticator<BasicCredentials, Server> {

  private static final Logger logger = LoggerFactory.getLogger(ServerAuthenticator.class);

  private final List<Server> servers;

  public ServerAuthenticator(AuthenticationConfiguration configuration) {
    this.servers = configuration.getServers();
  }

  @Override
  public Optional<Server> authenticate(BasicCredentials credentials) throws AuthenticationException {
    for (Server server : servers) {
      if (MessageDigest.isEqual(server.getName().getBytes(), credentials.getUsername().getBytes()) &&
          MessageDigest.isEqual(server.getPassword().getBytes(), credentials.getPassword().getBytes()))
      {
        return Optional.of(server);
      }
    }

    return Optional.absent();
  }
}
