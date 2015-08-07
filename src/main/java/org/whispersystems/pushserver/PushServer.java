package org.whispersystems.pushserver;

import com.codahale.metrics.SharedMetricRegistries;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.pushserver.auth.Server;
import org.whispersystems.pushserver.auth.ServerAuthenticator;
import org.whispersystems.pushserver.config.ApnConfiguration;
import org.whispersystems.pushserver.config.GcmConfiguration;
import org.whispersystems.pushserver.controllers.FeedbackController;
import org.whispersystems.pushserver.controllers.PushController;
import org.whispersystems.pushserver.providers.RedisClientFactory;
import org.whispersystems.pushserver.providers.RedisHealthCheck;
import org.whispersystems.pushserver.senders.APNSender;
import org.whispersystems.pushserver.senders.GCMSender;
import org.whispersystems.pushserver.senders.HttpGCMSender;
import org.whispersystems.pushserver.senders.UnregisteredQueue;
import org.whispersystems.pushserver.senders.XmppGCMSender;
import org.whispersystems.pushserver.util.Constants;

import java.security.Security;
import java.util.List;

import io.dropwizard.Application;
import io.dropwizard.auth.AuthFactory;
import io.dropwizard.auth.basic.BasicAuthFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import redis.clients.jedis.JedisPool;

public class PushServer extends Application<PushServerConfiguration> {

  private final Logger logger = LoggerFactory.getLogger(PushServer.class);

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

    APNSender apnSender = initializeApnSender(redisClient, apnQueue, config.getApnConfiguration());
    GCMSender gcmSender = initializeGcmSender(gcmQueue, config.getGcmConfiguration());

    environment.lifecycle().manage(apnSender);
    environment.lifecycle().manage(gcmSender);

    environment.jersey().register(AuthFactory.binder(new BasicAuthFactory<>(serverAuthenticator, "PushServer", Server.class)));
    environment.jersey().register(new PushController(apnSender, gcmSender));
    environment.jersey().register(new FeedbackController(gcmQueue, apnQueue));

    environment.healthChecks().register("Redis", new RedisHealthCheck(redisClient));
  }

  private APNSender initializeApnSender(JedisPool redisClient,
                                        UnregisteredQueue apnQueue,
                                        ApnConfiguration configuration)
  {
    return new APNSender(redisClient, apnQueue,
                         configuration.getPushCertificate(),
                         configuration.getPushKey(),
                         configuration.getVoipCertificate(),
                         configuration.getVoipKey(),
                         configuration.isFeedbackEnabled());
  }

  private GCMSender initializeGcmSender(UnregisteredQueue gcmQueue,
                                        GcmConfiguration configuration)
  {
    if (configuration.isXmpp()) {
      logger.info("Using XMPP GCM Interface.");
      return new XmppGCMSender(gcmQueue, configuration.getSenderId(), configuration.getApiKey());
    } else {
      logger.info("Using HTTP GCM Interface.");
      return new HttpGCMSender(gcmQueue, configuration.getApiKey(), configuration.getRedphoneApiKey());
    }
  }

  public static void main(String[] args) throws Exception {
    new PushServer().run(args);
  }
}
