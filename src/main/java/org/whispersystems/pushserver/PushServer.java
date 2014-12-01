package org.whispersystems.pushserver;

import com.codahale.metrics.SharedMetricRegistries;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.whispersystems.pushserver.auth.Server;
import org.whispersystems.pushserver.auth.ServerAuthenticator;
import org.whispersystems.pushserver.controllers.FeedbackController;
import org.whispersystems.pushserver.controllers.PushController;
import org.whispersystems.pushserver.metrics.JsonMetricsReporter;
import org.whispersystems.pushserver.providers.RedisClientFactory;
import org.whispersystems.pushserver.providers.RedisHealthCheck;
import org.whispersystems.pushserver.senders.APNSender;
import org.whispersystems.pushserver.senders.GCMSender;
import org.whispersystems.pushserver.senders.UnregisteredQueue;
import org.whispersystems.pushserver.util.Constants;

import java.security.Security;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.dropwizard.Application;
import io.dropwizard.auth.basic.BasicAuthProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import redis.clients.jedis.JedisPool;

public class PushServer extends Application<PushServerConfiguration> {

  static {
    Security.addProvider(new BouncyCastleProvider());
  }

  @Override
  public void initialize(Bootstrap<PushServerConfiguration> bootstrap) {}

  @Override
  public void run(PushServerConfiguration config, Environment environment) throws Exception {
    SharedMetricRegistries.add(Constants.METRICS_NAME, environment.metrics());
    environment.getObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    JedisPool           redisClient         = new RedisClientFactory(config.getRedisConfiguration()).getRedisClientPool();
    ServerAuthenticator serverAuthenticator = new ServerAuthenticator(config.getAuthenticationConfiguration());
    List<Server>        servers             = config.getAuthenticationConfiguration().getServers();
    UnregisteredQueue   apnQueue            = new UnregisteredQueue(redisClient, environment.getObjectMapper(), servers, "apn");
    UnregisteredQueue   gcmQueue            = new UnregisteredQueue(redisClient, environment.getObjectMapper(), servers, "gcm");

    APNSender apnSender = new APNSender(redisClient, apnQueue,
                                        config.getApnConfiguration().getCertificate(),
                                        config.getApnConfiguration().getKey());
    GCMSender gcmSender = new GCMSender(gcmQueue,
                                        config.getGcmConfiguration().getSenderId(),
                                        config.getGcmConfiguration().getApiKey());

    environment.lifecycle().manage(apnSender);
    environment.lifecycle().manage(gcmSender);

    environment.jersey().register(new BasicAuthProvider<>(serverAuthenticator, "PushServer"));
    environment.jersey().register(new PushController(apnSender, gcmSender));
    environment.jersey().register(new FeedbackController(gcmQueue, apnQueue));

    environment.healthChecks().register("Redis", new RedisHealthCheck(redisClient));

    if (config.getMetricsConfiguration().isEnabled()) {
      new JsonMetricsReporter(environment.metrics(),
                              config.getMetricsConfiguration().getToken(),
                              config.getMetricsConfiguration().getHost())
          .start(60, TimeUnit.SECONDS);
    }
  }

  public static void main(String[] args) throws Exception {
    new PushServer().run(args);
  }
}
