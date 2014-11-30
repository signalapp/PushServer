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

  public ApnMessage() {}

  @VisibleForTesting
  public ApnMessage(String apnId, String number, int deviceId, String message) {
    this.apnId    = apnId;
    this.number   = number;
    this.deviceId = deviceId;
    this.message  = message;
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
}
