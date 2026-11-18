package io.testopsai.server.modules.project;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class ProjectApiTest {
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
  void listProjectsReturnsSeededDemo() throws Exception {
    String token = loginAsAdmin();
    mvc.perform(get("/api/v2/projects").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].key").value("DEMO"))
        .andExpect(jsonPath("$[0].role").value("OWNER"))
        .andExpect(jsonPath("$[0].id").isString()); // v2 string ID
  }

  @Test
  void createAndGetProject() throws Exception {
    String token = loginAsAdmin();
    String key = "TEST-" + System.nanoTime() % 100000;
    var req = new CreateProjectRequest(key, "Test Project", "desc");
    MvcResult createResult =
        mvc.perform(
                post("/api/v2/projects")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.key").value(key))
            .andExpect(jsonPath("$.role").value("OWNER"))
            .andReturn();
    String id =
        objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

    mvc.perform(get("/api/v2/projects/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.key").value(key));
  }

  @Test
  void duplicateKeyReturns409() throws Exception {
    String token = loginAsAdmin();
    String key = "DUP-" + System.nanoTime() % 100000;
    var req = new CreateProjectRequest(key, "First", null);
    mvc.perform(
            post("/api/v2/projects")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated());

    mvc.perform(
            post("/api/v2/projects")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PROJECT_KEY_DUPLICATE"));
  }

  @Test
  void invalidKeyReturns400() throws Exception {
    String token = loginAsAdmin();
    var req = new CreateProjectRequest("invalid-lowercase", "Bad", null);
    mvc.perform(
            post("/api/v2/projects")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void updateProject() throws Exception {
    String token = loginAsAdmin();
    String key = "UPD-" + System.nanoTime() % 100000;
    var createReq = new CreateProjectRequest(key, "Original", "old desc");
    MvcResult createResult =
        mvc.perform(
                post("/api/v2/projects")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq)))
            .andExpect(status().isCreated())
            .andReturn();
    String id =
        objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

    var patchReq = new PatchProjectRequest("Updated Name", null);
    mvc.perform(
            patch("/api/v2/projects/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(patchReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated Name"));
  }

  @Test
  void memberManagement() throws Exception {
    String token = loginAsAdmin();
    String key = "MEM-" + System.nanoTime() % 100000;
    var createReq = new CreateProjectRequest(key, "Member Test", null);
    MvcResult createResult =
        mvc.perform(
                post("/api/v2/projects")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(createReq)))
            .andExpect(status().isCreated())
            .andReturn();
    String id =
        objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

    // List members (should have admin as OWNER)
    mvc.perform(
            get("/api/v2/projects/" + id + "/members").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].role").value("OWNER"));

    // Set member (user 1 = admin, set as EDITOR - but admin is already OWNER, this updates)
    var setReq = new SetMemberRequest("EDITOR");
    mvc.perform(
            put("/api/v2/projects/" + id + "/members/1")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(setReq)))
        .andExpect(status().isNoContent());
  }

  @Test
  void unauthenticatedReturns401() throws Exception {
    mvc.perform(get("/api/v2/projects"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void nonExistentProjectReturns404() throws Exception {
    String token = loginAsAdmin();
    mvc.perform(get("/api/v2/projects/999999999").header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
  }
}
