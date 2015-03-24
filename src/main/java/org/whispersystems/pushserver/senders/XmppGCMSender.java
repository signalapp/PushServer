package org.whispersystems.pushserver.senders;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.packet.DefaultPacketExtension;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PacketExtension;
import org.jivesoftware.smack.provider.PacketExtensionProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.util.StringUtils;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.pushserver.entities.GcmMessage;
import org.whispersystems.pushserver.entities.UnregisteredEvent;
import org.whispersystems.pushserver.util.Constants;
import org.whispersystems.pushserver.util.Util;
import org.xmlpull.v1.XmlPullParser;

import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.codahale.metrics.MetricRegistry.name;

public class XmppGCMSender implements GCMSender, PacketListener {

  private final Logger logger = LoggerFactory.getLogger(XmppGCMSender.class);

  private final MetricRegistry metricRegistry = SharedMetricRegistries.getOrCreate(Constants.METRICS_NAME);

  private final Meter success      = metricRegistry.meter(name(getClass(), "sent", "success"     ));
  private final Meter failure      = metricRegistry.meter(name(getClass(), "sent", "failure"     ));
  private final Meter unregistered = metricRegistry.meter(name(getClass(), "sent", "unregistered"));

  private static final String GCM_SERVER       = "gcm.googleapis.com";
  private static final int    GCM_PORT         = 5235;

  private static final String GCM_ELEMENT_NAME = "gcm";
  private static final String GCM_NAMESPACE    = "google:mobile:data";

  private final Map<String, GcmMessage> pendingMessages = new ConcurrentHashMap<>();

  private final UnregisteredQueue unregisteredQueue;
  private final long              senderId;
  private final String            apiKey;

  private XMPPTCPConnection connection;

  public XmppGCMSender(UnregisteredQueue unregisteredQueue, long senderId, String apiKey) {
    this.unregisteredQueue = unregisteredQueue;
    this.senderId          = senderId;
    this.apiKey            = apiKey;

    ProviderManager.addExtensionProvider(GCM_ELEMENT_NAME, GCM_NAMESPACE,
                                         new GcmPacketExtensionProvider());
  }

  public void sendMessage(GcmMessage message)
  {
    String messageId = "m-" + UUID.randomUUID().toString();
    sendMessage(messageId, message);
  }

  private void sendMessage(String messageId, GcmMessage message) {
    try {
      boolean isReceipt = message.isReceipt();

      Map<String, String> dataObject = new HashMap<>();
      dataObject.put("type", "message");
      dataObject.put(isReceipt ? "receipt" : "message", message.getMessage());

      Map<String, Object> messageObject = new HashMap<>();
      messageObject.put("to", message.getGcmId());
      messageObject.put("message_id", messageId);
      messageObject.put("data", dataObject);

      String json = JSONObject.toJSONString(messageObject);

      pendingMessages.put(messageId, message);
      connection.sendPacket(new GcmPacketExtension(json).toPacket());
    } catch (SmackException.NotConnectedException e) {
      logger.warn("GCMClient", "No connection", e);
    }
  }

  @Override
  public void start() throws Exception {
    this.connection = connect(senderId, apiKey);
  }

  @Override
  public void stop() throws Exception {
    this.connection.disconnect();
  }

  @Override
  public void processPacket(Packet packet) throws SmackException.NotConnectedException {
    Message            incomingMessage = (Message) packet;
    GcmPacketExtension gcmPacket       = (GcmPacketExtension) incomingMessage.getExtension(GCM_NAMESPACE);
    String             json            = gcmPacket.getJson();

    try {
      Map<String, Object> jsonObject  = (Map<String, Object>) JSONValue.parseWithException(json);
      Object              messageType = jsonObject.get("message_type");

      if (messageType == null) {
        handleUpstreamMessage(jsonObject);
        return;
      }

      switch (messageType.toString()) {
        case "ack"     : handleAckReceipt(jsonObject);      break;
        case "nack"    : handleNackReceipt(jsonObject);     break;
        case "receipt" : handleDeliveryReceipt(jsonObject); break;
        case "control" : handleControlMessage(jsonObject);  break;
        default:
          logger.warn("Received unknown GCM message: " + messageType.toString());
      }

    } catch (ParseException e) {
      logger.warn("GCMClient", "Received unparsable message", e);
    } catch (Exception e) {
      logger.warn("GCMClient", "Failed to process packet", e);
    }
  }

  private void handleControlMessage(Map<String, Object> message) {
    String controlType = (String) message.get("control_type");

    if ("CONNECTION_DRAINING".equals(controlType)) {
      logger.warn("GCM Connection is draining! Initiating reconnect...");
      reconnect();
    } else {
      logger.warn("Received unknown GCM control message: " + controlType);
    }
  }

  private void handleDeliveryReceipt(Map<String, Object> message) {
    logger.warn("Got delivery receipt!");
  }

  private void handleNackReceipt(Map<String, Object> message) {
    String messageId = (String) message.get("message_id");
    String errorCode = (String) message.get("error");

    if (errorCode == null) {
      logger.warn("Null GCM error code!");
      if (messageId != null) {
        pendingMessages.remove(messageId);
      }

      return;
    }

    switch (errorCode) {
      case "BAD_REGISTRATION"      : handleBadRegistration(message); break;
      case "DEVICE_UNREGISTERED"   : handleBadRegistration(message); break;
      case "INTERNAL_SERVER_ERROR" : handleServerFailure(message);   break;
      case "INVALID_JSON"          : handleClientFailure(message);   break;
      case "QUOTA_EXCEEDED"        : handleClientFailure(message);   break;
      case "SERVICE_UNAVAILABLE"   : handleServerFailure(message);   break;
    }
  }

  private void handleAckReceipt(Map<String, Object> message) {
    success.mark();

    String messageId = (String) message.get("message_id");

    if (messageId != null) {
      pendingMessages.remove(messageId);
    }
  }

  private void handleUpstreamMessage(Map<String, Object> message)
      throws SmackException.NotConnectedException
  {
    logger.warn("Got upstream message from GCM Server!");

    for (String key : message.keySet()) {
      logger.warn(key + " : " + message.get(key));
    }

    Map<String, Object> ack = new HashMap<>();
    message.put("message_type", "ack");
    message.put("to", message.get("from"));
    message.put("message_id", message.get("message_id"));

    String json = JSONValue.toJSONString(ack);

    Packet request = new GcmPacketExtension(json).toPacket();
    connection.sendPacket(request);
  }

  private void handleBadRegistration(Map<String, Object> message) {
    logger.warn("Got GCM unregistered notice!");
    unregistered.mark();

    String messageId = (String) message.get("message_id");

    if (messageId != null) {
      GcmMessage unacknowledgedMessage = pendingMessages.remove(messageId);

      if (unacknowledgedMessage != null) {
        unregisteredQueue.put(new UnregisteredEvent(unacknowledgedMessage.getGcmId(),
                                                    null,
                                                    unacknowledgedMessage.getNumber(),
                                                    unacknowledgedMessage.getDeviceId(),
                                                    System.currentTimeMillis()));
      }
    }
  }

  private void handleServerFailure(Map<String, Object> message) {
    failure.mark();

    String messageId = (String)message.get("message_id");

    if (messageId != null) {
      GcmMessage unacknowledgedMessage = pendingMessages.remove(messageId);

      if (unacknowledgedMessage != null) {
        sendMessage(messageId, unacknowledgedMessage);
      }
    }
  }

  private void handleClientFailure(Map<String, Object> message) {
    failure.mark();

    logger.warn("Unrecoverable error: " + message.get("error"));
    String messageId = (String)message.get("message_id");

    if (messageId != null) {
      pendingMessages.remove(messageId);
    }
  }

  private void reconnect() {
    try {
      this.connection.disconnect();
    } catch (SmackException.NotConnectedException e) {
      logger.warn("GCMClient", "Disconnect attempt", e);
    }

    while (true) {
      try {
        this.connection = connect(senderId, apiKey);
        return;
      } catch (XMPPException | IOException | SmackException e) {
        logger.warn("GCMClient", "Reconnecting", e);
        Util.sleep(1000);
      }
    }
  }

  private XMPPTCPConnection connect(long senderId, String apiKey)
      throws XMPPException, IOException, SmackException
  {
    ConnectionConfiguration config = new ConnectionConfiguration(GCM_SERVER, GCM_PORT);
    config.setSecurityMode(ConnectionConfiguration.SecurityMode.enabled);
    config.setReconnectionAllowed(true);
    config.setRosterLoadedAtLogin(false);
    config.setSendPresence(false);
    config.setSocketFactory(SSLSocketFactory.getDefault());

    XMPPTCPConnection connection = new XMPPTCPConnection(config);
    connection.connect();

    connection.addConnectionListener(new LoggingConnectionListener());
    connection.addPacketListener(this, new PacketTypeFilter(Message.class));

    connection.login(senderId + "@gcm.googleapis.com", apiKey);

    return connection;
  }

  private static class GcmPacketExtensionProvider implements PacketExtensionProvider {
    @Override
    public PacketExtension parseExtension(XmlPullParser xmlPullParser) throws Exception {
      String json = xmlPullParser.nextText();
      return new GcmPacketExtension(json);
    }
  }

  private static final class GcmPacketExtension extends DefaultPacketExtension {

    private final String json;

    public GcmPacketExtension(String json) {
      super(GCM_ELEMENT_NAME, GCM_NAMESPACE);
      this.json = json;
    }

    public String getJson() {
      return json;
    }

    @Override
    public String toXML() {
      return String.format("<%s xmlns=\"%s\">%s</%s>", GCM_ELEMENT_NAME, GCM_NAMESPACE,
                           StringUtils.escapeForXML(json), GCM_ELEMENT_NAME);
    }

    public Packet toPacket() {
      Message message = new Message();
      message.addExtension(this);
      return message;
    }
  }

  private class LoggingConnectionListener implements ConnectionListener {

    @Override
    public void connected(XMPPConnection xmppConnection) {
      logger.warn("GCM XMPP Connected.");
    }

    @Override
    public void authenticated(XMPPConnection xmppConnection) {
      logger.warn("GCM XMPP Authenticated.");
      reconnectionSuccessful();
    }

    @Override
    public void reconnectionSuccessful() {
      logger.warn("GCM XMPP Reconnected, resending... Pending Size: " + pendingMessages.size());
      HashMap<String, GcmMessage> resendMessages = new HashMap<>(pendingMessages);

      for (Map.Entry<String, GcmMessage> resendMessage : resendMessages.entrySet()) {
        sendMessage(resendMessage.getKey(), resendMessage.getValue());
      }
    }

    @Override
    public void reconnectionFailed(Exception e) {
      logger.warn("GCM XMPP Reconnection failed!", e);
      reconnect();
    }

    @Override
    public void reconnectingIn(int seconds) {
      logger.warn(String.format("GCM XMPP Reconnecting in %d secs", seconds));
    }

    @Override
    public void connectionClosedOnError(Exception e) {
      logger.warn("GCM XMPP Connection closed on error. Pending Size: " + pendingMessages.size());
    }

    @Override
    public void connectionClosed() {
      logger.warn("GCM XMPP Connection closed. Pending Size: " + pendingMessages.size());
      reconnect();
    }
  }
}
