package org.whispersystems.pushserver.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Min;

public class GcmMessage {

  @JsonProperty
  @NotEmpty
  private String gcmId;

  @JsonProperty
  @NotEmpty
  private String number;

  @JsonProperty
  @Min(1)
  private int deviceId;

  @JsonProperty
  @NotEmpty
  private String message;

  @JsonProperty
  private boolean receipt;

  public GcmMessage() {}

  @VisibleForTesting
  public GcmMessage(String gcmId, String number, int deviceId, String message, boolean receipt) {
    this.gcmId    = gcmId;
    this.number   = number;
    this.deviceId = deviceId;
    this.message  = message;
    this.receipt  = receipt;
  }

  public String getGcmId() {
    return gcmId;
  }

  public String getNumber() {
    return number;
  }

  public String getMessage() {
    return message;
  }

  public boolean isReceipt() {
    return receipt;
  }

  public int getDeviceId() {
    return deviceId;
  }
}
