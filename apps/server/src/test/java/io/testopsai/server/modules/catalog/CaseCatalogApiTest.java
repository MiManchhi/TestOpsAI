package io.testopsai.server.modules.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testopsai.server.modules.identity.LoginRequest;
import io.testopsai.server.modules.project.CreateProjectRequest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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

/**
 * Contract 1.0.0 case catalog: suites, project test cases, selection snapshots. Requires a real
 * MySQL instance because filters, tags and version CAS are exercised through SQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(
    named = "MYSQL_PASSWORD",
    matches = ".+",
    disabledReason = "MySQL not configured")
class CaseCatalogApiTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper objectMapper;

  private String token;

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
    // Business contract tests must not depend on the login limiter; RateLimitApiTest covers it.
    registry.add("testopsai.ratelimit.max-requests", () -> "1000");
    registry.add(
        "spring.data.redis.host", () -> System.getenv().getOrDefault("REDIS_HOST", "127.0.0.1"));
    registry.add(
        "spring.data.redis.port", () -> System.getenv().getOrDefault("REDIS_PORT", "6379"));
    registry.add(
        "spring.data.redis.password", () -> System.getenv().getOrDefault("REDIS_PASSWORD", ""));
  }

  @BeforeEach
  void login() throws Exception {
    if (token != null) return;
    MvcResult result =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(new LoginRequest("admin", "admin123"))))
            .andExpect(status().isOk())
            .andReturn();
    token =
        objectMapper
            .readTree(result.getResponse().getContentAsString())
            .get("accessToken")
            .asText();
  }

  private JsonNode demoProject() throws Exception {
    MvcResult result =
        mvc.perform(get("/api/v2/projects").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
    for (JsonNode item :
        objectMapper.readTree(result.getResponse().getContentAsString()).get("items")) {
      if ("DEMO".equals(item.get("key").asText())) return item;
    }
    throw new IllegalStateException("DEMO project missing from seeded data");
  }

  private String createProject(String keyPrefix) throws Exception {
    String key = keyPrefix + "-" + (System.nanoTime() % 1_000_000);
    MvcResult result =
        mvc.perform(
                post("/api/v2/projects")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new CreateProjectRequest(key, "Contract test", null))))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private JsonNode listCases(String projectId, String queryString) throws Exception {
    MvcResult result =
        mvc.perform(
                get("/api/v2/projects/" + projectId + "/test-cases" + queryString)
                    .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private String createManualCase(String projectId, String title, List<String> tags)
      throws Exception {
    var body = new CreateCaseRequest(title, "demo-module", tags, "P2", "自造契约测试", "示例预期结果");
    MvcResult result =
        mvc.perform(
                post("/api/v2/projects/" + projectId + "/test-cases")
                    .header("Authorization", "Bearer " + token)
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  @Test
  void listSuitesReturnsSeededRobotTree() throws Exception {
    JsonNode demo = demoProject();
    mvc.perform(
            get("/api/v2/projects/" + demo.get("id").asText() + "/test-suites")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.items[0].id").isString())
        .andExpect(jsonPath("$.items[0].projectId").isString())
        .andExpect(jsonPath("$.items[0].revision").isString())
        .andExpect(jsonPath("$.total").isNumber());
  }

  @Test
  void listSuitesRejectsOutOfRangePagination() throws Exception {
    JsonNode demo = demoProject();
    mvc.perform(
            get("/api/v2/projects/" + demo.get("id").asText() + "/test-suites?size=101")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void listSuitesUnknownParentIs404() throws Exception {
    JsonNode demo = demoProject();
    mvc.perform(
            get("/api/v2/projects/" + demo.get("id").asText() + "/test-suites?parentId=999999999")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SUITE_NOT_FOUND"));
  }

  @Test
  void filterByModuleIsExact() throws Exception {
    JsonNode demo = demoProject();
    JsonNode page = listCases(demo.get("id").asText(), "?module=费率与时钟&size=100");
    assertThat(page.get("total").asInt()).isEqualTo(4);
    for (JsonNode item : page.get("items")) {
      assertThat(item.get("module").asText()).isEqualTo("费率与时钟");
    }
  }

  @Test
  void filterByTagsUsesExactMatchWithAllDefault() throws Exception {
    JsonNode demo = demoProject();
    String id = demo.get("id").asText();
    JsonNode smoke = listCases(id, "?sourceType=ROBOT&tags=smoke&size=100");
    assertThat(smoke.get("total").asInt()).isEqualTo(2);

    JsonNode any = listCases(id, "?sourceType=ROBOT&tags=smoke,regression&tagsMatch=ANY&size=100");
    assertThat(any.get("total").asInt()).isEqualTo(6);

    JsonNode all = listCases(id, "?sourceType=ROBOT&tags=smoke,tariff&tagsMatch=ALL&size=100");
    assertThat(all.get("total").asInt()).isEqualTo(2);
  }

  @Test
  void queryPerformsCaseInsensitiveContains() throws Exception {
    JsonNode demo = demoProject();
    JsonNode page = listCases(demo.get("id").asText(), "?query=掉电&size=100");
    assertThat(page.get("total").asInt()).isGreaterThanOrEqualTo(1);
    for (JsonNode item : page.get("items")) {
      assertThat(item.get("title").asText()).contains("掉电");
    }
  }

  @Test
  void queryMatchesLikeWildcardsAndEscapeCharacterLiterally() throws Exception {
    String projectId = createProject("LITERAL");
    String matchingId = createManualCase(projectId, "Power!50%_Ramp", List.of());
    createManualCase(projectId, "Power!50XARamp", List.of());

    mvc.perform(
            get("/api/v2/projects/{projectId}/test-cases", projectId)
                .queryParam("query", "power!50%_ramp")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].id").value(matchingId));
  }

  @Test
  void invalidSortAndPriorityAndLifecycleAreRejected() throws Exception {
    JsonNode demo = demoProject();
    String id = demo.get("id").asText();
    mvc.perform(
            get("/api/v2/projects/" + id + "/test-cases?sort=size,desc")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    mvc.perform(
            get("/api/v2/projects/" + id + "/test-cases?priority=P9")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/v2/projects/" + id + "/test-cases?lifecycleStatus=DELETED")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest());
  }

  @Test
  void sortIsStableByTitleThenId() throws Exception {
    JsonNode demo = demoProject();
    String id = demo.get("id").asText();
    String suffix = String.valueOf(System.nanoTime() % 100_000_000);
    createManualCase(id, "AAA-sort-" + suffix, List.of());
    createManualCase(id, "ZZZ-sort-" + suffix, List.of());

    String ascQuery = "?sort=title,asc&size=100&query=sort-" + suffix;
    JsonNode asc = listCases(id, ascQuery);
    assertThat(asc.get("items").get(0).get("title").asText()).isEqualTo("AAA-sort-" + suffix);

    JsonNode desc = listCases(id, "?sort=title,desc&size=100&query=sort-" + suffix);
    assertThat(desc.get("items").get(0).get("title").asText()).isEqualTo("ZZZ-sort-" + suffix);
  }

  @Test
  void createManualCaseRequiresIdempotencyKeyAndIsVersioned() throws Exception {
    JsonNode demo = demoProject();
    String projectId = demo.get("id").asText();
    var body =
        new CreateCaseRequest(
            "Manual " + System.nanoTime(),
            "demo-module",
            List.of("smoke"),
            "P1",
            "doc",
            "expected");

    mvc.perform(
            post("/api/v2/projects/" + projectId + "/test-cases")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    MvcResult created =
        mvc.perform(
                post("/api/v2/projects/" + projectId + "/test-cases")
                    .header("Authorization", "Bearer " + token)
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.sourceType").value("MANUAL"))
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.tags[0]").value("smoke"))
            .andExpect(jsonPath("$.stableKey").isString())
            .andReturn();
    String caseId =
        objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    var patchBody = new PatchCaseRequest(1, "更新后的标题", null, null, "P0", null, null, null);
    mvc.perform(
            patch("/api/v2/projects/" + projectId + "/test-cases/" + caseId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(patchBody)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.priority").value("P0"));

    mvc.perform(
            patch("/api/v2/projects/" + projectId + "/test-cases/" + caseId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(patchBody)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
  }

  @Test
  void createCaseReplaysSameKeyAndRejectsChangedPayload() throws Exception {
    JsonNode demo = demoProject();
    String projectId = demo.get("id").asText();
    String key = UUID.randomUUID().toString();
    String title = "Idempotent " + System.nanoTime();
    var body = new CreateCaseRequest(title, "demo-module", List.of(), "P2", "", "expected");

    MvcResult first =
        mvc.perform(
                post("/api/v2/projects/" + projectId + "/test-cases")
                    .header("Authorization", "Bearer " + token)
                    .header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andReturn();
    String firstId =
        objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

    MvcResult replay =
        mvc.perform(
                post("/api/v2/projects/" + projectId + "/test-cases")
                    .header("Authorization", "Bearer " + token)
                    .header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andReturn();
    String replayId =
        objectMapper.readTree(replay.getResponse().getContentAsString()).get("id").asText();
    assertThat(replayId).isEqualTo(firstId);

    var changed =
        new CreateCaseRequest(title + "-changed", "demo-module", List.of(), "P2", "", "expected");
    // Same key with different content is only rejected when the idempotency store is reachable.
    if (System.getenv("REDIS_PASSWORD") != null && !System.getenv("REDIS_PASSWORD").isBlank()) {
      mvc.perform(
              post("/api/v2/projects/" + projectId + "/test-cases")
                  .header("Authorization", "Bearer " + token)
                  .header("Idempotency-Key", key)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(changed)))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }
  }

  @Test
  void robotCaseIsReadOnly() throws Exception {
    JsonNode demo = demoProject();
    String projectId = demo.get("id").asText();
    JsonNode page = listCases(projectId, "?sourceType=ROBOT&size=1");
    String caseId = page.get("items").get(0).get("id").asText();
    var patchBody = new PatchCaseRequest(1, "不允许修改", null, null, null, null, null, null);
    mvc.perform(
            patch("/api/v2/projects/" + projectId + "/test-cases/" + caseId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(patchBody)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ROBOT_CASE_READ_ONLY"));
  }

  @Test
  void crossProjectCaseIsNotFound() throws Exception {
    JsonNode demo = demoProject();
    JsonNode page = listCases(demo.get("id").asText(), "?size=1");
    String caseId = page.get("items").get(0).get("id").asText();
    String otherProjectId = createProject("CROSS");
    mvc.perform(
            get("/api/v2/projects/" + otherProjectId + "/test-cases/" + caseId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CASE_NOT_FOUND"));
  }

  @Test
  void createExplicitSelectionSnapshot() throws Exception {
    JsonNode demo = demoProject();
    String projectId = demo.get("id").asText();
    JsonNode page = listCases(projectId, "?size=2");
    String first = page.get("items").get(0).get("id").asText();
    String second = page.get("items").get(1).get("id").asText();
    var body = new SelectionCreateRequest("EXPLICIT", List.of(first, second), null, null);

    mvc.perform(
            post("/api/v2/projects/" + projectId + "/case-selections")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isString())
        .andExpect(jsonPath("$.count").value(2))
        .andExpect(jsonPath("$.digest").isString())
        .andExpect(jsonPath("$.expiresAt").exists())
        .andExpect(jsonPath("$.cases[0].version").isNumber());
  }

  @Test
  void createFilterSelectionAppliesSameFilterAndExclusions() throws Exception {
    JsonNode demo = demoProject();
    String projectId = demo.get("id").asText();
    JsonNode page = listCases(projectId, "?module=费率与时钟&size=100");
    String excluded = page.get("items").get(0).get("id").asText();
    int expected = page.get("total").asInt() - 1;

    var filter =
        new SelectionCreateRequest.CaseFilterRequest(
            null, null, "费率与时钟", null, null, null, null, null, null);
    var body = new SelectionCreateRequest("FILTER", null, filter, List.of(excluded));

    mvc.perform(
            post("/api/v2/projects/" + projectId + "/case-selections")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.count").value(expected));
  }

  @Test
  void selectionModeValidationRejectsMixedShapes() throws Exception {
    JsonNode demo = demoProject();
    String projectId = demo.get("id").asText();
    var filter =
        new SelectionCreateRequest.CaseFilterRequest(
            null, null, "费率与时钟", null, null, null, null, null, null);
    var body = new SelectionCreateRequest("EXPLICIT", List.of("1"), filter, null);
    mvc.perform(
            post("/api/v2/projects/" + projectId + "/case-selections")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void unauthenticatedReturns401() throws Exception {
    mvc.perform(get("/api/v2/projects/1/test-cases"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }
}
