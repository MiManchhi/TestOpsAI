package io.testopsai.server.modules.identity;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(
    named = "MYSQL_PASSWORD",
    matches = ".+",
    disabledReason = "MySQL not configured")
class AuthIntegrationTest {
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
    // Keep business tests independent of the login rate limiter (dedicated test covers it).
    registry.add("testopsai.ratelimit.max-requests", () -> "1000");
  }

  @Test
  void loginWithValidCredentialsReturnsToken() throws Exception {
    var body = new LoginRequest("admin", "admin123");
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").exists())
        .andExpect(jsonPath("$.expiresIn").value(1800))
        .andExpect(header().string("X-Request-Id", matchesPattern("[0-9a-f\\-]{36}")));
  }

  @Test
  void loginWithWrongPasswordReturns401() throws Exception {
    var body = new LoginRequest("admin", "wrong-password");
    mvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
        .andExpect(jsonPath("$.requestId").exists());
  }

  @Test
  void meWithValidTokenReturnsUserInfo() throws Exception {
    var body = new LoginRequest("admin", "admin123");
    MvcResult loginResult =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andReturn();
    String token =
        objectMapper
            .readTree(loginResult.getResponse().getContentAsString())
            .get("accessToken")
            .asText();

    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("admin"))
        .andExpect(jsonPath("$.roles").isArray())
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
  }

  @Test
  void meWithoutTokenReturns401WithStructuredBody() throws Exception {
    mvc.perform(get("/api/v1/users/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.message").value("Authentication required"))
        .andExpect(jsonPath("$.requestId").exists())
        .andExpect(header().string("X-Request-Id", matchesPattern("[0-9a-f\\-]{36}")));
  }

  @Test
  void meWithInvalidTokenReturns401() throws Exception {
    mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer invalid.token.here"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }
}
