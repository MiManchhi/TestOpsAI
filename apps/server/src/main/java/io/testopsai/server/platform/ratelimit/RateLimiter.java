package io.testopsai.server.platform.ratelimit;

/** Sliding window rate limiter. Returns true if request is allowed. */
public interface RateLimiter {
  boolean tryAcquire(String key);

  long retryAfterSeconds(String key);
}
