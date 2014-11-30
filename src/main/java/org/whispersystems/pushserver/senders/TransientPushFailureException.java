package org.whispersystems.pushserver.senders;

import com.notnoop.exceptions.NetworkIOException;

public class TransientPushFailureException extends Exception {
  public TransientPushFailureException(String s) {
    super(s);
  }

  public TransientPushFailureException(Exception e) {
    super(e);
  }
}
