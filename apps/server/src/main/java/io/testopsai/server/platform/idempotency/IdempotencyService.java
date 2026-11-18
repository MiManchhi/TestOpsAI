package io.testopsai.server.platform.idempotency;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Idempotency key storage backed by Redis with 24h TTL. Key format:
 * idempotency:{actorId}:{operationId}:{key} Value: canonical request hash + response marker.
 */
@Service
public class IdempotencyService {
  private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
  private static final Duration TTL = Duration.ofHours(24);
  private static final String PREFIX = "idempotency:";

  private final StringRedisTemplate redis;

  public IdempotencyService(StringRedisTemplate redis) {
    this.redis = redis;
  }

  /**
   * Check if an idempotency key has been seen before.
   *
   * @return stored hash if key exists, null otherwise
   */
  public String check(Long actorId, String operationId, String key) {
    try {
      return redis.opsForValue().get(buildKey(actorId, operationId, key));
    } catch (Exception e) {
      log.warn("Idempotency check failed, degrading: {}", e.getMessage());
      return null;
    }
  }

  /** Store an idempotency key with its request hash. */
  public void store(Long actorId, String operationId, String key, String requestHash) {
    try {
      redis.opsForValue().set(buildKey(actorId, operationId, key), requestHash, TTL);
    } catch (Exception e) {
      log.warn("Idempotency store failed, degrading: {}", e.getMessage());
    }
  }

  private String buildKey(Long actorId, String operationId, String key) {
    return PREFIX + actorId + ":" + operationId + ":" + key;
  }
}
