package org.whispersystems.pushserver.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;

import java.util.List;

public class UnregisteredEventList {

  @JsonProperty
  private List<UnregisteredEvent> devices;

  public UnregisteredEventList() {}

  public UnregisteredEventList(List<UnregisteredEvent> devices) {
    this.devices = devices;
  }

  @VisibleForTesting
  public List<UnregisteredEvent> getDevices() {
    return devices;
  }
}
