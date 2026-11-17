package io.testopsai.server.platform.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Type;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisCacheService {
  private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);
  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final Duration defaultTtl;

  public RedisCacheService(
      StringRedisTemplate redis, ObjectMapper objectMapper, CacheProperties properties) {
    this.redis = redis;
    this.objectMapper = objectMapper;
    this.defaultTtl = Duration.ofSeconds(properties.kbListTtlSeconds());
  }

  public <T> T get(String key, Class<T> type) {
    try {
      String json = redis.opsForValue().get(key);
      if (json == null) return null;
      return objectMapper.readValue(json, type);
    } catch (Exception e) {
      log.warn("Redis GET failed for key={}, degrading: {}", key, e.getMessage());
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  public <T> T get(String key, Type type) {
    try {
      String json = redis.opsForValue().get(key);
      if (json == null) return null;
      JavaType javaType = objectMapper.getTypeFactory().constructType(type);
      return (T) objectMapper.readValue(json, javaType);
    } catch (Exception e) {
      log.warn("Redis GET failed for key={}, degrading: {}", key, e.getMessage());
      return null;
    }
  }

  public void set(String key, Object value) {
    try {
      String json = objectMapper.writeValueAsString(value);
      redis.opsForValue().set(key, json, defaultTtl);
    } catch (JsonProcessingException e) {
      log.warn("Cache serialization failed for key={}: {}", key, e.getMessage());
    } catch (Exception e) {
      log.warn("Redis SET failed for key={}, degrading: {}", key, e.getMessage());
    }
  }

  public void evict(String pattern) {
    try {
      var keys = redis.keys(pattern);
      if (keys != null && !keys.isEmpty()) {
        redis.delete(keys);
      }
    } catch (Exception e) {
      log.warn("Redis EVICT failed for pattern={}, degrading: {}", pattern, e.getMessage());
    }
  }

  public boolean isAvailable() {
    try {
      return "PONG".equals(redis.getConnectionFactory().getConnection().ping());
    } catch (Exception e) {
      return false;
    }
  }
}
