package io.testopsai.server.platform.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.testopsai.server.modules.identity.LoginRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(
    named = "REDIS_PASSWORD",
    matches = ".+",
    disabledReason = "Redis not configured")
class RateLimitApiTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper objectMapper;

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.url",
        () ->
            System.getenv()
                .getOrDefault(
                    "MYSQL_URL",
                    "jdbc:mysql://127.0.0.1:3306/testops?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"));
    registry.add(
        "spring.datasource.username", () -> System.getenv().getOrDefault("MYSQL_USER", "testops"));
    registry.add(
        "spring.datasource.password", () -> System.getenv().getOrDefault("MYSQL_PASSWORD", ""));
    registry.add(
        "testopsai.jwt.secret",
        () ->
            System.getenv().getOrDefault("JWT_SECRET", "test-secret-key-at-least-32-bytes-long!!"));
    registry.add(
        "spring.data.redis.host", () -> System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1"));
    registry.add(
        "spring.data.redis.port", () -> System.getenv().getOrDefault("REDIS_PORT", "6379"));
    registry.add(
        "spring.data.redis.password", () -> System.getenv().getOrDefault("REDIS_PASSWORD", ""));
  }

  @Test
  void loginWithinLimitSucceeds() throws Exception {
    var body = new LoginRequest("admin", "admin123");
    mvc.perform(
            loginFrom("192.0.2.1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").exists());
  }

  @Test
  void loginExceedingLimitReturns429() throws Exception {
    var body = new LoginRequest("admin", "wrong-password");
    // Send 11 rapid requests (limit is 10/min)
    for (int i = 0; i < 10; i++) {
      mvc.perform(
              loginFrom("192.0.2.2")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(body)))
          .andExpect(status().isUnauthorized());
    }
    // 11th request should be rate limited
    mvc.perform(
            loginFrom("192.0.2.2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
        .andExpect(header().exists("Retry-After"));
  }

  private MockHttpServletRequestBuilder loginFrom(String remoteAddress) {
    return post("/api/v1/auth/login")
        .with(
            request -> {
              request.setRemoteAddr(remoteAddress);
              return request;
            });
  }
}
