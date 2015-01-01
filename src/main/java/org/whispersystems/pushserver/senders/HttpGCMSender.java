package org.whispersystems.pushserver.senders;

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

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HttpGCMSender implements GCMSender {

  private final Logger logger = LoggerFactory.getLogger(HttpGCMSender.class);

  private final Sender            sender;
  private final UnregisteredQueue unregisteredQueue;
  private       ExecutorService   executor;

  public HttpGCMSender(UnregisteredQueue unregisteredQueue, String apiKey) {
    this.unregisteredQueue = unregisteredQueue;
    this.sender            = new Sender(apiKey, 50);
  }

  @Override
  public void sendMessage(GcmMessage message) {
    Message request = Message.newBuilder()
                             .withDestination(message.getGcmId())
                             .withDataPart(message.isReceipt() ? "receipt" : "message", message.getMessage())
                             .build();

    ListenableFuture<Result> future = sender.send(request, message);

    Futures.addCallback(future, new FutureCallback<Result>() {
      @Override
      public void onSuccess(Result result) {
        if (result.isUnregistered() || result.isInvalidRegistrationId()) {
          handleBadRegistration(result);
        } else if (result.hasCanonicalRegistrationId()) {
          handleCanonicalRegistrationId(result);
        } else if (!result.isSuccess())                                         {
          handleGenericError(result);
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
    this.sender.stop();
    this.executor.shutdown();
  }

  private void handleBadRegistration(Result result) {
    GcmMessage message = (GcmMessage)result.getContext();
    logger.warn("Got GCM unregistered notice! " + message.getGcmId());
    unregisteredQueue.put(new UnregisteredEvent(message.getGcmId(), message.getNumber(),
                                                message.getDeviceId(), System.currentTimeMillis()));
  }

  private void handleCanonicalRegistrationId(Result result) {
    logger.warn(String.format("Actually received 'CanonicalRegistrationId' ::: (canonical=%s), (original=%s)",
                              result.getCanonicalRegistrationId(), ((GcmMessage)result.getContext()).getGcmId()));
  }

  private void handleGenericError(Result result) {
    GcmMessage message = (GcmMessage)result.getContext();
    logger.warn(String.format("Unrecoverable Error ::: (error=%s), (gcm_id=%s), " +
                              "(destination=%s), (device_id=%d)",
                              result.getError(), message.getGcmId(), message.getNumber(),
                              message.getDeviceId()));
  }
}
