package org.whispersystems.pushserver.controllers;

import com.codahale.metrics.annotation.Timed;

import org.whispersystems.pushserver.auth.Server;
import org.whispersystems.pushserver.entities.UnregisteredEventList;
import org.whispersystems.pushserver.senders.UnregisteredQueue;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.dropwizard.auth.Auth;

@Path("/api/v1/feedback")
public class FeedbackController {

  private final UnregisteredQueue gcmQueue;
  private final UnregisteredQueue apnQueue;
  private final UnregisteredQueue upsQueue;

  public FeedbackController(UnregisteredQueue gcmQueue, UnregisteredQueue apnQueue, UnregisteredQueue upsQueue) {
    this.gcmQueue = gcmQueue;
    this.apnQueue = apnQueue;
    this.upsQueue = upsQueue;
  }

  @Timed
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/gcm/")
  public UnregisteredEventList getUnregisteredGcmDevices(@Auth Server server) {
    return new UnregisteredEventList(gcmQueue.get(server.getName()));
  }

  @Timed
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/apn/")
  public UnregisteredEventList getUnregisteredApnDevices(@Auth Server server) {
    return new UnregisteredEventList(apnQueue.get(server.getName()));
  }

  @Timed
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/ups/")
  public UnregisteredEventList getUnregisteredUpsDevices(@Auth Server server) {
    return new UnregisteredEventList(upsQueue.get(server.getName()));
  }

}
