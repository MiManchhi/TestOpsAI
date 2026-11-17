package io.testopsai.server.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "testopsai.jwt")
public record JwtProperties(String secret, int expirationMinutes) {}
