package org.whispersystems.pushserver.senders;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.gcm.server.Message;
import org.whispersystems.gcm.server.Result;
import org.whispersystems.gcm.server.Sender;
import org.whispersystems.pushserver.entities.GcmMessage;
import org.whispersystems.pushserver.entities.UnregisteredEvent;
import org.whispersystems.pushserver.util.Constants;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.codahale.metrics.MetricRegistry.name;

public class HttpGCMSender implements GCMSender {

  private final Logger logger = LoggerFactory.getLogger(HttpGCMSender.class);

  private final MetricRegistry metricRegistry = SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);
  private final Meter          success        = metricRegistry.meter(name(getClass(), "sent", "success"));
  private final Meter          failure        = metricRegistry.meter(name(getClass(), "sent", "failure"));
  private final Meter          unregistered   = metricRegistry.meter(name(getClass(), "sent", "unregistered"));
  private final Meter          canonical      = metricRegistry.meter(name(getClass(), "sent", "canonical"));

  private final Map<String, Meter> outboundMeters = new HashMap<String, Meter>() {{
    put("receipt", metricRegistry.meter(name(getClass(), "outbound", "receipt")));
    put("notification", metricRegistry.meter(name(getClass(), "outbound", "notification")));
    put("call", metricRegistry.meter(name(getClass(), "outbound", "call")));
    put("message", metricRegistry.meter(name(getClass(), "outbound", "message")));
    put("signal", metricRegistry.meter(name(getClass(), "outbound", "signal")));
  }};

  private final Sender            signalSender;
  private final Sender            redphoneSender;
  private final UnregisteredQueue unregisteredQueue;
  private       ExecutorService   executor;

  public HttpGCMSender(UnregisteredQueue unregisteredQueue, String signalKey, String redphoneKey) {
    this.unregisteredQueue = unregisteredQueue;
    this.signalSender      = new Sender(signalKey, 50);
    this.redphoneSender    = new Sender(redphoneKey, 50);
  }

  @Override
  public void sendMessage(GcmMessage message) {
    Message.Builder builder = Message.newBuilder()
                                     .withDestination(message.getGcmId())
                                     .withPriority("high");

    ListenableFuture<Result> future;

    if (!message.isRedphone()) {
      String  key     = message.isReceipt() ? "receipt" : message.isNotification() ? "notification" : message.isCall() ? "call" : "message";
      Message request = builder.withDataPart(key, message.getMessage()).build();

      future = signalSender.send(request, message);
      markOutboundMeter(key);
    } else {
      Message request = builder.withDataPart("signal", message.getMessage()).build();

      future = redphoneSender.send(request, message);
      markOutboundMeter("signal");
    }

    Futures.addCallback(future, new FutureCallback<Result>() {
      @Override
      public void onSuccess(Result result) {
        if (result.isUnregistered() || result.isInvalidRegistrationId()) {
          handleBadRegistration(result);
        } else if (result.hasCanonicalRegistrationId()) {
          handleCanonicalRegistrationId(result);
        } else if (!result.isSuccess()) {
          handleGenericError(result);
        } else {
          success.mark();
        }
      }

      @Override
      public void onFailure(Throwable throwable) {
        logger.warn("GCM Failed: " + throwable);
      }
    }, executor);
  }

  @Override
  public void start() {
    executor = Executors.newSingleThreadExecutor();
  }

  @Override
  public void stop() throws IOException {
    this.signalSender.stop();
    this.redphoneSender.stop();
    this.executor.shutdown();
  }

  private void handleBadRegistration(Result result) {
    GcmMessage message = (GcmMessage)result.getContext();
    logger.warn("Got GCM unregistered notice! " + message.getGcmId());
    unregisteredQueue.put(new UnregisteredEvent(message.getGcmId(), null, message.getNumber(),
                                                message.getDeviceId(), System.currentTimeMillis()));
    unregistered.mark();
  }

  private void handleCanonicalRegistrationId(Result result) {
    GcmMessage message = (GcmMessage)result.getContext();
    logger.warn(String.format("Actually received 'CanonicalRegistrationId' ::: (canonical=%s), (original=%s)",
                              result.getCanonicalRegistrationId(), message.getGcmId()));
    unregisteredQueue.put(new UnregisteredEvent(message.getGcmId(), result.getCanonicalRegistrationId(),
                                                message.getNumber(), message.getDeviceId(), System.currentTimeMillis()));
    canonical.mark();
  }

  private void handleGenericError(Result result) {
    GcmMessage message = (GcmMessage)result.getContext();
    logger.warn(String.format("Unrecoverable Error ::: (error=%s), (gcm_id=%s), " +
                              "(destination=%s), (device_id=%d)",
                              result.getError(), message.getGcmId(), message.getNumber(),
                              message.getDeviceId()));
    failure.mark();
  }

  private void markOutboundMeter(String key) {
    Meter meter = outboundMeters.get(key);

    if (meter != null) meter.mark();
    else               logger.warn("Unknown outbound key: " + key);
  }
}
