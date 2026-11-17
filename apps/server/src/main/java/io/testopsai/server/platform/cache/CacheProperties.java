package io.testopsai.server.platform.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "testopsai.cache")
public record CacheProperties(int kbListTtlSeconds) {
  public CacheProperties {
    if (kbListTtlSeconds <= 0) kbListTtlSeconds = 60;
  }

  public static CacheProperties defaults() {
    return new CacheProperties(60);
  }
}
