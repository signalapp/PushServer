package org.whispersystems.pushserver.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Min;

public class UpsMessage {

  @JsonProperty
  @NotEmpty
  private String upsId;

  @JsonProperty
  @NotEmpty
  private String number;

  @JsonProperty
  @Min(1)
  private int deviceId;

  @JsonProperty
  private String message;

  @JsonProperty
  private boolean receipt;

  @JsonProperty
  private boolean notification;

  @JsonProperty
  private boolean redphone;

  @JsonProperty
  private boolean call;

  public UpsMessage() {}

  @VisibleForTesting
  public UpsMessage(String upsId, String number, int deviceId, String message,
                    boolean receipt, boolean notification, boolean redphone, boolean call) {
    this.upsId        = upsId;
    this.number       = number;
    this.deviceId     = deviceId;
    this.message      = message;
    this.receipt      = receipt;
    this.notification = notification;
    this.redphone     = redphone;
    this.call         = call;
  }

  public String getUpsId() {
    return upsId;
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

  public boolean isNotification() {
    return notification;
  }

  public boolean isRedphone() {
    return redphone;
  }

  public boolean isCall() {
    return call;
  }

  public int getDeviceId() {
    return deviceId;
  }
}
