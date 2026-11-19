package io.testopsai.server.modules.testcase;

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
class TestCaseApiTest {
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
  void createAndListTestCases() throws Exception {
    String token = loginAsAdmin();
    var req =
        new CreateTestCaseRequest(
            "PROJ-" + System.nanoTime(),
            "auth",
            "TC " + System.nanoTime(),
            "tag1,tag2",
            "P1",
            "pass");
    mvc.perform(
            post("/api/v1/test-cases")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.projectKey").value(req.projectKey()))
        .andExpect(jsonPath("$.priority").value("P1"));

    mvc.perform(get("/api/v1/test-cases").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.total").isNumber())
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20));
  }

  @Test
  void filterByProjectKey() throws Exception {
    String token = loginAsAdmin();
    String projKey = "FILTER-" + System.nanoTime();
    var req1 = new CreateTestCaseRequest(projKey, "mod", "TC1", null, "P2", null);
    var req2 =
        new CreateTestCaseRequest("OTHER-" + System.nanoTime(), "mod", "TC2", null, "P2", null);
    mvc.perform(
            post("/api/v1/test-cases")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/v1/test-cases")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
        .andExpect(status().isCreated());

    mvc.perform(
            get("/api/v1/test-cases?projectKey=" + projKey)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].projectKey").value(projKey));
  }

  @Test
  void filterByModule() throws Exception {
    String token = loginAsAdmin();
    String uniqueMod = "unique-mod-" + System.nanoTime();
    var req = new CreateTestCaseRequest("PROJ", uniqueMod, "TC mod filter", null, "P2", null);
    mvc.perform(
            post("/api/v1/test-cases")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated());

    mvc.perform(
            get("/api/v1/test-cases?module=" + uniqueMod)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].module").value(uniqueMod));
  }

  @Test
  void sortByWhitelistField() throws Exception {
    String token = loginAsAdmin();
    mvc.perform(get("/api/v1/test-cases?sort=title,asc").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray());
  }

  @Test
  void invalidSortFieldRejected() throws Exception {
    String token = loginAsAdmin();
    mvc.perform(
            get("/api/v1/test-cases?sort=password,desc").header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void getTestCaseById() throws Exception {
    String token = loginAsAdmin();
    var req =
        new CreateTestCaseRequest(
            "PROJ", "mod", "Detail TC " + System.nanoTime(), null, "P0", "expected");
    MvcResult createResult =
        mvc.perform(
                post("/api/v1/test-cases")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn();
    Long id =
        objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

    mvc.perform(get("/api/v1/test-cases/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.title").value(req.title()));
  }

  @Test
  void updateTestCase() throws Exception {
    String token = loginAsAdmin();
    var req =
        new CreateTestCaseRequest(
            "PROJ", "mod", "Update TC " + System.nanoTime(), null, "P2", null);
    MvcResult createResult =
        mvc.perform(
                post("/api/v1/test-cases")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn();
    Long id =
        objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

    var updateReq = new UpdateTestCaseRequest("Updated Title", null, null, "P0", null, null);
    mvc.perform(
            put("/api/v1/test-cases/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Updated Title"))
        .andExpect(jsonPath("$.priority").value("P0"));
  }

  @Test
  void archiveTestCase() throws Exception {
    String token = loginAsAdmin();
    var req =
        new CreateTestCaseRequest(
            "PROJ", "mod", "Archive TC " + System.nanoTime(), null, "P2", null);
    MvcResult createResult =
        mvc.perform(
                post("/api/v1/test-cases")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andReturn();
    Long id =
        objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

    mvc.perform(delete("/api/v1/test-cases/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());

    mvc.perform(get("/api/v1/test-cases/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ARCHIVED"));
  }

  @Test
  void unauthenticatedReturns401() throws Exception {
    mvc.perform(get("/api/v1/test-cases"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void paginationWithFilters() throws Exception {
    String token = loginAsAdmin();
    mvc.perform(
            get("/api/v1/test-cases?page=0&size=3&status=ACTIVE")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(org.hamcrest.Matchers.lessThanOrEqualTo(3))))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(3));
  }

  @Test
  void nonExistentTestCaseReturns404() throws Exception {
    String token = loginAsAdmin();
    mvc.perform(get("/api/v1/test-cases/999999999").header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("TC_NOT_FOUND"));
  }
}
