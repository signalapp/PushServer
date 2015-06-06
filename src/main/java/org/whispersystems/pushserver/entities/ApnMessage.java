package org.whispersystems.pushserver.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

public class ApnMessage {

  @JsonProperty
  @NotEmpty
  private String apnId;

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
  @NotNull
  private boolean voip;

  @JsonProperty
  private long expirationTime = Integer.MAX_VALUE * 1000L;

  public ApnMessage() {}

  @VisibleForTesting
  public ApnMessage(String apnId, String number, int deviceId, String message, boolean voip, long expirationTime) {
    this.apnId          = apnId;
    this.number         = number;
    this.deviceId       = deviceId;
    this.message        = message;
    this.voip           = voip;
    this.expirationTime = expirationTime;
  }

  public String getApnId() {
    return apnId;
  }

  public String getNumber() {
    return number;
  }

  public int getDeviceId() {
    return deviceId;
  }

  public String getMessage() {
    return message;
  }

  public boolean isVoip() {
    return voip;
  }

  public long getExpirationTime() {
    return expirationTime;
  }
}
