package org.whispersystems.pushserver.senders;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whispersystems.pushserver.auth.Server;
import org.whispersystems.pushserver.entities.UnregisteredEvent;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class UnregisteredQueue {

  private final Logger logger = LoggerFactory.getLogger(UnregisteredQueue.class);

  private final JedisPool    jedisPool;
  private final List<Server> servers;
  private final String       prefix;
  private final ObjectMapper objectMapper;

  public UnregisteredQueue(JedisPool jedisPool, ObjectMapper objectMapper,
                           List<Server> servers, String prefix)
  {
    this.jedisPool    = jedisPool;
    this.objectMapper = objectMapper;
    this.servers      = servers;
    this.prefix       = prefix;
  }

  public void put(UnregisteredEvent event) {
    try {
      String serialized = objectMapper.writeValueAsString(event);

      try (Jedis jedis = jedisPool.getResource()) {
        for (Server server : servers) {
          jedis.rpush(server.getName() + "::" + prefix, serialized);
        }
      }
    } catch (JsonProcessingException e) {
      logger.warn("Serialization error", e);
    }
  }

  public List<UnregisteredEvent> get(String serverName) {
    List<UnregisteredEvent> results = new LinkedList<>();

    try (Jedis jedis = jedisPool.getResource()) {
      String result;

      while ((result = jedis.lpop(serverName + "::" + prefix)) != null) {
        try {
          results.add(objectMapper.readValue(result, UnregisteredEvent.class));
        } catch (IOException e) {
          logger.warn("Parsing Error", e);
        }
      }
    }

    return results;
  }

}
