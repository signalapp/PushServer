/**
 * Copyright (C) 2013 Open WhisperSystems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.whispersystems.pushserver.senders;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.google.common.base.Optional;
import com.notnoop.apns.APNS;
import com.notnoop.apns.ApnsService;
import com.notnoop.exceptions.NetworkIOException;
import org.bouncycastle.openssl.PEMReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.pushserver.entities.ApnMessage;
import org.whispersystems.pushserver.entities.UnregisteredEvent;
import org.whispersystems.pushserver.util.Constants;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.codahale.metrics.MetricRegistry.name;
import io.dropwizard.lifecycle.Managed;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class APNSender implements Managed {

  private final MetricRegistry metricRegistry = SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);

  private final Meter  voipMeter    = metricRegistry.meter(name(getClass(), "voip"));
  private final Meter  pushMeter    = metricRegistry.meter(name(getClass(), "push"));
  private final Meter  failureMeter = metricRegistry.meter(name(getClass(), "failure"));
  private final Logger logger       = LoggerFactory.getLogger(APNSender.class);

  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

  private final JedisPool         jedisPool;
  private final UnregisteredQueue unregisteredQueue;
  private final String            pushCertificate;
  private final String            pushKey;
  private final String            voipCertificate;
  private final String            voipKey;
  private final boolean           feedbackEnabled;

  private ApnsService pushApnService;
  private ApnsService voipApnService;

  public APNSender(JedisPool jedisPool, UnregisteredQueue unregisteredQueue,
                   String pushCertificate, String pushKey,
                   String voipCertificate, String voipKey,
                   boolean feedbackEnabled)
  {
    this.jedisPool         = jedisPool;
    this.unregisteredQueue = unregisteredQueue;
    this.pushCertificate   = pushCertificate;
    this.pushKey           = pushKey;
    this.voipCertificate   = voipCertificate;
    this.voipKey           = voipKey;
    this.feedbackEnabled   = feedbackEnabled;
  }

  public void sendMessage(ApnMessage message)
      throws TransientPushFailureException
  {
    try {
      redisSet(message.getApnId(), message.getNumber(), message.getDeviceId());

      if (message.isVoip()) {
        voipApnService.push(message.getApnId(), message.getMessage(), new Date(message.getExpirationTime()));
        voipMeter.mark();
      } else {
        pushApnService.push(message.getApnId(), message.getMessage(), new Date(message.getExpirationTime()));
        pushMeter.mark();
      }
    } catch (NetworkIOException nioe) {
      logger.warn("Network Error", nioe);
      failureMeter.mark();
      throw new TransientPushFailureException(nioe);
    }
  }

  private static byte[] initializeKeyStore(String pemCertificate, String pemKey)
      throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException
  {
    PEMReader       reader           = new PEMReader(new InputStreamReader(new ByteArrayInputStream(pemCertificate.getBytes())));
    X509Certificate certificate      = (X509Certificate) reader.readObject();
    Certificate[]   certificateChain = {certificate};

    reader = new PEMReader(new InputStreamReader(new ByteArrayInputStream(pemKey.getBytes())));
    KeyPair keyPair = (KeyPair) reader.readObject();

    KeyStore keyStore = KeyStore.getInstance("pkcs12");
    keyStore.load(null);
    keyStore.setEntry("apn",
                      new KeyStore.PrivateKeyEntry(keyPair.getPrivate(), certificateChain),
                      new KeyStore.PasswordProtection("insecure".toCharArray()));

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    keyStore.store(baos, "insecure".toCharArray());

    return baos.toByteArray();
  }

  @Override
  public void start() throws Exception {
    byte[] pushKeyStore = initializeKeyStore(pushCertificate, pushKey);
    byte[] voipKeyStore = initializeKeyStore(voipCertificate, voipKey);

    this.pushApnService = APNS.newService()
                              .withCert(new ByteArrayInputStream(pushKeyStore), "insecure")
                              .asQueued()
                              .withProductionDestination().build();

    this.voipApnService = APNS.newService()
                              .withCert(new ByteArrayInputStream(voipKeyStore), "insecure")
                              .asQueued()
                              .withProductionDestination().build();

    if (feedbackEnabled) {
      this.executor.scheduleAtFixedRate(new FeedbackRunnable(), 0, 1, TimeUnit.HOURS);
    }
  }

  @Override
  public void stop() throws Exception {
    pushApnService.stop();
    voipApnService.stop();
  }

  private void redisSet(String registrationId, String number, int deviceId) {
    try (Jedis jedis = jedisPool.getResource()) {
      jedis.set("APN-" + registrationId.toLowerCase(), number + "." + deviceId);
      jedis.expire("APN-" + registrationId.toLowerCase(), (int) TimeUnit.HOURS.toSeconds(1));
    }
  }

  private Optional<String> redisGet(String registrationId) {
    try (Jedis jedis = jedisPool.getResource()) {
      String number = jedis.get("APN-" + registrationId.toLowerCase());
      return Optional.fromNullable(number);
    }
  }

  private class FeedbackRunnable implements Runnable {

    @Override
    public void run() {
      Map<String, Date> inactiveDevices = pushApnService.getInactiveDevices();
      inactiveDevices.putAll(voipApnService.getInactiveDevices());

      for (String registrationId : inactiveDevices.keySet()) {
        Optional<String> device = redisGet(registrationId);

        if (device.isPresent()) {
          logger.warn("Got APN unregistered notice!");
          String[] parts    = device.get().split(".", 2);

          if (parts.length == 2) {
            String number    = parts[0];
            int    deviceId  = Integer.parseInt(parts[1]);
            long   timestamp = inactiveDevices.get(registrationId).getTime();

            unregisteredQueue.put(new UnregisteredEvent(registrationId, null, number, deviceId, timestamp));
          } else {
            logger.warn("APN unregister event for device with no parts: " + device.get());
          }
        } else {
          logger.warn("APN unregister event received for uncached ID: " + registrationId);
        }
      }
    }
  }
}
