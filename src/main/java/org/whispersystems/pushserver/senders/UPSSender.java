package org.whispersystems.pushserver.senders;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ro.startx.ups.server.Message;
import ro.startx.ups.server.Result;
import ro.startx.ups.server.Sender;
import org.whispersystems.pushserver.entities.UpsMessage;
import org.whispersystems.pushserver.entities.UnregisteredEvent;
import org.whispersystems.pushserver.util.Constants;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.dropwizard.lifecycle.Managed;

import static com.codahale.metrics.MetricRegistry.name;

public class UPSSender implements Managed {

  private static final String APP_ID = "textsecure.jani_textsecure";

  private final Logger logger = LoggerFactory.getLogger(UPSSender.class);

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

  public UPSSender(UnregisteredQueue unregisteredQueue) {
    this.unregisteredQueue = unregisteredQueue;
    this.signalSender      = new Sender(50);
    this.redphoneSender    = new Sender(50);
  }

  public void sendMessage(UpsMessage message) {
    Message.Builder builder = Message.newBuilder()
                                     .withAppID(APP_ID)
                                     .withToken(message.getUpsId());

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
        if (!result.isSuccess()) {
          handleGenericError(result);
        } else {
          success.mark();
        }
      }

      @Override
      public void onFailure(Throwable throwable) {
        logger.warn("UPS Failed: " + throwable);
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

  private void handleGenericError(Result result) {
    UpsMessage message = (UpsMessage)result.getContext();
    logger.warn(String.format("Unrecoverable Error ::: (error=%s), (token=%s), " +
                              "(destination=%s), (device_id=%d)",
                              result.getError(), message.getUpsId(), message.getNumber(),
                              message.getDeviceId()));
    failure.mark();
  }

  private void markOutboundMeter(String key) {
    Meter meter = outboundMeters.get(key);

    if (meter != null) meter.mark();
    else               logger.warn("Unknown outbound key: " + key);
  }
}
