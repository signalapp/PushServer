package org.whispersystems.pushserver.senders;

import org.whispersystems.pushserver.entities.GcmMessage;

import io.dropwizard.lifecycle.Managed;

public interface GCMSender extends Managed {
  public void sendMessage(GcmMessage message);
}
