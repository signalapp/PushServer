package org.whispersystems.pushserver.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;

public class UnregisteredEvent {

  @JsonProperty
  private String registrationId;

  @JsonProperty
  private String canonicalId;

  @JsonProperty
  private String number;

  @JsonProperty
  private int deviceId;

  @JsonProperty
  private long timestamp;

  public UnregisteredEvent() {}

  public UnregisteredEvent(String registrationId, String canonicalId, String number, int deviceId, long timestamp) {
    this.registrationId = registrationId;
    this.canonicalId    = canonicalId;
    this.number         = number;
    this.deviceId       = deviceId;
    this.timestamp      = timestamp;
  }

  @VisibleForTesting
  public String getRegistrationId() {
    return registrationId;
  }

  @VisibleForTesting
  public String getNumber() {
    return number;
  }

  @VisibleForTesting
  public int getDeviceId() {
    return deviceId;
  }
}
