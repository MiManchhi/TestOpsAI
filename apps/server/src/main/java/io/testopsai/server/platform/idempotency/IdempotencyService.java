package io.testopsai.server.platform.idempotency;

import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Idempotency-key storage backed by Redis with a 24 hour TTL.
 *
 * <p>Stored value format is {@code <requestHash>|<resourceType>:<resourceId>}. A replay with the
 * same key and the same request hash returns the recorded resource; a replay with a different
 * request hash is rejected with {@link IdempotencyConflictException}. Redis failures degrade to "no
 * record" so that a cache outage never blocks writes.
 */
@Service
public class IdempotencyService {
  private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
  private static final Duration TTL = Duration.ofHours(24);
  private static final String PREFIX = "idempotency:";
  private static final String SEPARATOR = "|";

  private final StringRedisTemplate redis;

  public IdempotencyService(StringRedisTemplate redis) {
    this.redis = redis;
  }

  /** A previously recorded idempotent result. */
  public record StoredResult(String requestHash, String resourceType, String resourceId) {}

  /**
   * Looks up a recorded result. Returns empty when the key is unknown (or Redis is unavailable).
   * Throws {@link IdempotencyConflictException} when the same key was used with other content.
   */
  public Optional<StoredResult> find(
      Long actorId, String operationId, String key, String requestHash) {
    String raw;
    try {
      raw = redis.opsForValue().get(buildKey(actorId, operationId, key));
    } catch (Exception e) {
      log.warn("Idempotency lookup failed, degrading to no record: {}", e.getMessage());
      return Optional.empty();
    }
    if (raw == null) return Optional.empty();
    int separator = raw.indexOf(SEPARATOR);
    if (separator < 0) {
      // Legacy record without a resource reference: treat the whole value as the hash.
      if (!raw.equals(requestHash)) throw new IdempotencyConflictException(key);
      return Optional.empty();
    }
    StoredResult stored =
        new StoredResult(raw.substring(0, separator), "", raw.substring(separator + 1));
    if (!stored.requestHash().equals(requestHash)) throw new IdempotencyConflictException(key);
    int typeSeparator = stored.resourceId().indexOf(':');
    if (typeSeparator < 0) return Optional.of(new StoredResult(stored.requestHash(), "", ""));
    return Optional.of(
        new StoredResult(
            stored.requestHash(),
            stored.resourceId().substring(0, typeSeparator),
            stored.resourceId().substring(typeSeparator + 1)));
  }

  /** Records a key together with the canonical request hash and the created resource. */
  public void store(
      Long actorId,
      String operationId,
      String key,
      String requestHash,
      String resourceType,
      String resourceId) {
    String value = requestHash + SEPARATOR + resourceType + ":" + resourceId;
    try {
      redis.opsForValue().set(buildKey(actorId, operationId, key), value, TTL);
    } catch (Exception e) {
      log.warn("Idempotency store failed, degrading to no record: {}", e.getMessage());
    }
  }

  private String buildKey(Long actorId, String operationId, String key) {
    return PREFIX + actorId + ":" + operationId + ":" + key;
  }
}
