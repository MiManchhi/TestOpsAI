package io.testopsai.server.platform.ratelimit;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisRateLimiter implements RateLimiter {
  private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

  /**
   * Lua script for atomic sliding window counter. KEYS[1] = rate limit key ARGV[1] = window seconds
   * ARGV[2] = max requests Returns: 1 if allowed, 0 if denied
   */
  private static final String LUA_SCRIPT =
      """
      local key = KEYS[1]
      local window = tonumber(ARGV[1])
      local limit = tonumber(ARGV[2])
      local current = tonumber(redis.call('GET', key) or '0')
      if current < limit then
        redis.call('INCR', key)
        if current == 0 then
          redis.call('EXPIRE', key, window)
        end
        return 1
      else
        return 0
      end
      """;

  private final StringRedisTemplate redis;
  private final RateLimitProperties properties;
  private final DefaultRedisScript<Long> script;

  public RedisRateLimiter(StringRedisTemplate redis, RateLimitProperties properties) {
    this.redis = redis;
    this.properties = properties;
    this.script = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);
  }

  @Override
  public boolean tryAcquire(String key) {
    try {
      Long result =
          redis.execute(
              script,
              List.of(key),
              String.valueOf(properties.windowSeconds()),
              String.valueOf(properties.maxRequests()));
      return result != null && result == 1L;
    } catch (Exception e) {
      log.warn("Rate limiter Redis failed for key={}, degrading to allow: {}", key, e.getMessage());
      return true; // Degrade: allow request when Redis is down
    }
  }

  @Override
  public long retryAfterSeconds(String key) {
    try {
      Long ttl = redis.getExpire(key);
      return ttl != null && ttl > 0 ? ttl : properties.windowSeconds();
    } catch (Exception e) {
      return properties.windowSeconds();
    }
  }
}
