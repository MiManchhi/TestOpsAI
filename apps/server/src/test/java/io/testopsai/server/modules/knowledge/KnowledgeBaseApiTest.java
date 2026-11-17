package io.testopsai.server.modules.knowledge;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class KnowledgeBaseApiTest {
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
  void createAndListKnowledgeBases() throws Exception {
    String token = loginAsAdmin();
    var req = new CreateKnowledgeBaseRequest("Test KB " + System.nanoTime(), "desc", "PRIVATE");
    mvc.perform(
            post("/api/v1/knowledge-bases")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.name").value(req.name()))
        .andExpect(jsonPath("$.visibility").value("PRIVATE"));

    mvc.perform(get("/api/v1/knowledge-bases").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.total").isNumber())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20));
  }

  @Test
  void getKnowledgeBaseById() throws Exception {
    String token = loginAsAdmin();
    var req =
        new CreateKnowledgeBaseRequest("Detail KB " + System.nanoTime(), "detail desc", "PUBLIC");
    MvcResult createResult =
        mvc.perform(
                post("/api/v1/knowledge-bases")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn();
    Long id =
        objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

    mvc.perform(get("/api/v1/knowledge-bases/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.name").value(req.name()))
        .andExpect(jsonPath("$.description").value("detail desc"))
        .andExpect(jsonPath("$.visibility").value("PUBLIC"));
  }

  @Test
  void updateKnowledgeBase() throws Exception {
    String token = loginAsAdmin();
    var req = new CreateKnowledgeBaseRequest("Update KB " + System.nanoTime(), "old", "PRIVATE");
    MvcResult createResult =
        mvc.perform(
                post("/api/v1/knowledge-bases")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn();
    Long id =
        objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

    var updateReq = new UpdateKnowledgeBaseRequest(null, "new description", null, null);
    mvc.perform(
            put("/api/v1/knowledge-bases/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.description").value("new description"));
  }

  @Test
  void archiveKnowledgeBase() throws Exception {
    String token = loginAsAdmin();
    var req =
        new CreateKnowledgeBaseRequest("Archive KB " + System.nanoTime(), "to archive", "PRIVATE");
    MvcResult createResult =
        mvc.perform(
                post("/api/v1/knowledge-bases")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn();
    Long id =
        objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

    mvc.perform(delete("/api/v1/knowledge-bases/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());

    // Archived KB should not appear in active list (status filter)
    // But GET by id still returns it with status=ARCHIVED
    mvc.perform(get("/api/v1/knowledge-bases/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ARCHIVED"));
  }

  @Test
  void duplicateNameReturns409() throws Exception {
    String token = loginAsAdmin();
    String name = "Dup KB " + System.nanoTime();
    var req = new CreateKnowledgeBaseRequest(name, "first", "PRIVATE");
    mvc.perform(
            post("/api/v1/knowledge-bases")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated());

    mvc.perform(
            post("/api/v1/knowledge-bases")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("KB_NAME_DUPLICATE"));
  }

  @Test
  void unauthenticatedReturns401() throws Exception {
    mvc.perform(get("/api/v1/knowledge-bases"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void paginationWorks() throws Exception {
    String token = loginAsAdmin();
    // Seed data already has 3 KBs; list with small page size
    mvc.perform(
            get("/api/v1/knowledge-bases?page=0&size=2").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(2)))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(2));
  }

  @Test
  void adminCanAccessAllKbs() throws Exception {
    String token = loginAsAdmin();
    // Admin can list and access seed PUBLIC/SHARED/PRIVATE KBs
    mvc.perform(get("/api/v1/knowledge-bases").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray());
  }
}
