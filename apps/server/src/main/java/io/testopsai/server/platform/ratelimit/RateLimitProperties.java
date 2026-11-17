package io.testopsai.server.platform.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "testopsai.ratelimit")
public record RateLimitProperties(int windowSeconds, int maxRequests) {
  public RateLimitProperties {
    if (windowSeconds <= 0) windowSeconds = 60;
    if (maxRequests <= 0) maxRequests = 10;
  }

  public static RateLimitProperties defaults() {
    return new RateLimitProperties(60, 10);
  }
}
