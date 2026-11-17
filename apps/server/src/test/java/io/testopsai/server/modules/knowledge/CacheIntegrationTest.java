package io.testopsai.server.modules.knowledge;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(
    named = "MYSQL_PASSWORD",
    matches = ".+",
    disabledReason = "MySQL not configured")
class CacheIntegrationTest {
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
    // Redis config - optional for degradation testing
    registry.add(
        "spring.data.redis.host", () -> System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1"));
    registry.add(
        "spring.data.redis.port", () -> System.getenv().getOrDefault("REDIS_PORT", "6379"));
    registry.add(
        "spring.data.redis.password", () -> System.getenv().getOrDefault("REDIS_PASSWORD", ""));
  }

  private String loginAsAdmin() throws Exception {
    var body = new LoginRequest("admin", "admin123");
    MvcResult result =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper
        .readTree(result.getResponse().getContentAsString())
        .get("accessToken")
        .asText();
  }

  @Test
  void cachedListReturnsSameResult() throws Exception {
    String token = loginAsAdmin();
    // First call populates cache
    MvcResult first =
        mvc.perform(
                get("/api/v1/knowledge-bases?page=0&size=5")
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andReturn();
    // Second call should hit cache and return same data
    mvc.perform(
            get("/api/v1/knowledge-bases?page=0&size=5").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.total")
                .value(
                    objectMapper
                        .readTree(first.getResponse().getContentAsString())
                        .get("total")
                        .asInt()));
  }

  @Test
  void cacheInvalidatedOnCreate() throws Exception {
    String token = loginAsAdmin();
    // Get initial count
    MvcResult before =
        mvc.perform(
                get("/api/v1/knowledge-bases?page=0&size=100")
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
    int totalBefore =
        objectMapper.readTree(before.getResponse().getContentAsString()).get("total").asInt();

    // Create a new KB
    var req =
        new CreateKnowledgeBaseRequest(
            "Cache Test KB " + System.nanoTime(), "cache test", "PRIVATE");
    mvc.perform(
            post("/api/v1/knowledge-bases")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated());

    // List should reflect new item (cache invalidated)
    mvc.perform(
            get("/api/v1/knowledge-bases?page=0&size=100")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(totalBefore + 1));
  }
}
