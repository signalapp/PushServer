package org.whispersystems.pushserver.controllers;

import com.sun.jersey.api.client.ClientResponse;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.whispersystems.pushserver.auth.ServerAuthenticator;
import org.whispersystems.pushserver.config.TestAuthenticationConfig;
import org.whispersystems.pushserver.entities.ApnMessage;
import org.whispersystems.pushserver.entities.GcmMessage;
import org.whispersystems.pushserver.senders.APNSender;
import org.whispersystems.pushserver.senders.GCMSender;
import org.whispersystems.pushserver.senders.TransientPushFailureException;
import org.whispersystems.pushserver.util.AuthHelper;

import javax.ws.rs.core.MediaType;

import io.dropwizard.auth.basic.BasicAuthProvider;
import io.dropwizard.testing.junit.ResourceTestRule;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class PushControllerTest {

  private static final APNSender apnSender = mock(APNSender.class);
  private static final GCMSender gcmSender = mock(GCMSender.class);

  @ClassRule
  public static final ResourceTestRule resources =
      ResourceTestRule.builder()
                      .addResource(new PushController(apnSender, gcmSender))
                      .addProvider(new BasicAuthProvider<>(new ServerAuthenticator(new TestAuthenticationConfig()), "TEST"))
                      .build();

  @Test
  public void testSendApn() throws TransientPushFailureException {
    ClientResponse response = resources.client().resource("/api/v1/push/apn/")
                                       .header("Authorization", AuthHelper.getAuthHeader("textsecure", "foobar"))
                                       .entity(new ApnMessage("12345", "+14152222222", 1, "Hey there!"), MediaType.APPLICATION_JSON)
                                       .put(ClientResponse.class);

    assertThat(response.getStatus()).isEqualTo(204);

    ArgumentCaptor<ApnMessage> captor = ArgumentCaptor.forClass(ApnMessage.class);
    verify(apnSender).sendMessage(captor.capture());

    assertThat(captor.getValue().getApnId()).isEqualTo("12345");
    assertThat(captor.getValue().getDeviceId()).isEqualTo(1);
    assertThat(captor.getValue().getNumber()).isEqualTo("+14152222222");
    assertThat(captor.getValue().getMessage()).isEqualTo("Hey there!");
  }

  @Test
  public void testSendGcm() {
    ClientResponse response = resources.client().resource("/api/v1/push/gcm/")
                                       .header("Authorization", AuthHelper.getAuthHeader("redphone", "foobaz"))
                                       .entity(new GcmMessage("12345", "+14152222222", 1, "Hey there!", false), MediaType.APPLICATION_JSON)
                                       .put(ClientResponse.class);

    assertThat(response.getStatus()).isEqualTo(204);

    ArgumentCaptor<GcmMessage> captor = ArgumentCaptor.forClass(GcmMessage.class);
    verify(gcmSender).sendMessage(captor.capture());

    assertThat(captor.getValue().getGcmId()).isEqualTo("12345");
    assertThat(captor.getValue().getDeviceId()).isEqualTo(1);
    assertThat(captor.getValue().getNumber()).isEqualTo("+14152222222");
    assertThat(captor.getValue().getMessage()).isEqualTo("Hey there!");
    assertThat(captor.getValue().isReceipt()).isEqualTo(false);
  }

  @Test
  public void testUnauthorizedSendApn() throws TransientPushFailureException {
    ClientResponse response = resources.client().resource("/api/v1/push/apn/")
                                       .header("Authorization", AuthHelper.getAuthHeader("redphone", "foobar"))
                                       .entity(new ApnMessage("12345", "+14152222222", 1, "Hey there!"), MediaType.APPLICATION_JSON)
                                       .put(ClientResponse.class);

    assertThat(response.getStatus()).isEqualTo(401);
    verifyNoMoreInteractions(apnSender);
  }

  @Test
  public void testUnauthorizedSendGcm() {
    ClientResponse response = resources.client().resource("/api/v1/push/gcm/")
                                       .header("Authorization", AuthHelper.getAuthHeader("textsecure", "foobaz"))
                                       .entity(new GcmMessage("12345", "+14152222222", 1, "Hey there!", false), MediaType.APPLICATION_JSON)
                                       .put(ClientResponse.class);

    assertThat(response.getStatus()).isEqualTo(401);
    verifyNoMoreInteractions(gcmSender);
  }



}
