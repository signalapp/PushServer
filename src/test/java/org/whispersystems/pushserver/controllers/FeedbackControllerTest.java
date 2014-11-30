package org.whispersystems.pushserver.controllers;

import com.sun.jersey.api.client.ClientResponse;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.whispersystems.pushserver.auth.ServerAuthenticator;
import org.whispersystems.pushserver.config.TestAuthenticationConfig;
import org.whispersystems.pushserver.entities.UnregisteredEvent;
import org.whispersystems.pushserver.entities.UnregisteredEventList;
import org.whispersystems.pushserver.senders.UnregisteredQueue;
import org.whispersystems.pushserver.util.AuthHelper;

import java.util.LinkedList;
import java.util.List;

import io.dropwizard.auth.basic.BasicAuthProvider;
import io.dropwizard.testing.junit.ResourceTestRule;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FeedbackControllerTest {

  private static final UnregisteredQueue gcmQueue = mock(UnregisteredQueue.class);
  private static final UnregisteredQueue apnQueue = mock(UnregisteredQueue.class);

  @ClassRule
  public static final ResourceTestRule resources =
      ResourceTestRule.builder()
                      .addResource(new FeedbackController(gcmQueue, apnQueue))
                      .addProvider(new BasicAuthProvider<>(new ServerAuthenticator(new TestAuthenticationConfig()), "TEST"))
                      .build();

  @Before
  public void setup() {
    List<UnregisteredEvent> gcmEvents = new LinkedList<UnregisteredEvent>() {{
      add(new UnregisteredEvent("1234", "+14152222222", 1));
      add(new UnregisteredEvent("5678", "+14152222222", 2));
      add(new UnregisteredEvent("9999", "+14151111111", 1));
    }};

    List<UnregisteredEvent> apnEvents = new LinkedList<UnregisteredEvent>() {{
      add(new UnregisteredEvent("6666", "+14151231234", 1));
      add(new UnregisteredEvent("7777", "+14154444444", 1));
    }};

    when(gcmQueue.get(anyString())).thenReturn(gcmEvents);
    when(apnQueue.get(anyString())).thenReturn(apnEvents);
  }

  @Test
  public void testGcmFeedback() {
    ClientResponse clientResponse = resources.client().resource("/api/v1/feedback/gcm")
                                             .header("Authorization", AuthHelper.getAuthHeader("textsecure", "foobar"))
                                             .get(ClientResponse.class);

    assertThat(clientResponse.getStatus()).isEqualTo(200);

    UnregisteredEventList list = clientResponse.getEntity(UnregisteredEventList.class);
    assertThat(list.getDevices().size()).isEqualTo(3);

    assertThat(list.getDevices().get(0).getRegistrationId()).isEqualTo("1234");
    assertThat(list.getDevices().get(0).getNumber()).isEqualTo("+14152222222");
    assertThat(list.getDevices().get(0).getDeviceId()).isEqualTo(1);

    assertThat(list.getDevices().get(1).getRegistrationId()).isEqualTo("5678");
    assertThat(list.getDevices().get(1).getNumber()).isEqualTo("+14152222222");
    assertThat(list.getDevices().get(1).getDeviceId()).isEqualTo(2);

    assertThat(list.getDevices().get(2).getRegistrationId()).isEqualTo("9999");
    assertThat(list.getDevices().get(2).getNumber()).isEqualTo("+14151111111");
    assertThat(list.getDevices().get(2).getDeviceId()).isEqualTo(1);
  }

  @Test
  public void testApnFeedback() {
    ClientResponse clientResponse = resources.client().resource("/api/v1/feedback/apn")
                                             .header("Authorization", AuthHelper.getAuthHeader("redphone", "foobaz"))
                                             .get(ClientResponse.class);

    assertThat(clientResponse.getStatus()).isEqualTo(200);

    UnregisteredEventList list = clientResponse.getEntity(UnregisteredEventList.class);
    assertThat(list.getDevices().size()).isEqualTo(2);

    assertThat(list.getDevices().get(0).getRegistrationId()).isEqualTo("6666");
    assertThat(list.getDevices().get(0).getNumber()).isEqualTo("+14151231234");
    assertThat(list.getDevices().get(0).getDeviceId()).isEqualTo(1);

    assertThat(list.getDevices().get(1).getRegistrationId()).isEqualTo("7777");
    assertThat(list.getDevices().get(1).getNumber()).isEqualTo("+14154444444");
    assertThat(list.getDevices().get(1).getDeviceId()).isEqualTo(1);
  }

  @Test
  public void testGcmFeedbackUnauthorized() {
    ClientResponse clientResponse = resources.client().resource("/api/v1/feedback/gcm")
                                             .header("Authorization", AuthHelper.getAuthHeader("textsecure", "foobaz"))
                                             .get(ClientResponse.class);

    assertThat(clientResponse.getStatus()).isEqualTo(401);
  }

  @Test
  public void testApnFeedbackUnauthorized() {
    ClientResponse clientResponse = resources.client().resource("/api/v1/feedback/apn")
                                             .header("Authorization", AuthHelper.getAuthHeader("something", "foobar"))
                                             .get(ClientResponse.class);

    assertThat(clientResponse.getStatus()).isEqualTo(401);
  }
}
