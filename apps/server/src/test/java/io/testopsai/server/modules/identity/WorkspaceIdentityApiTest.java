package io.testopsai.server.modules.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(
    named = "MYSQL_PASSWORD",
    matches = ".+",
    disabledReason = "MySQL not configured")
class WorkspaceIdentityApiTest {
  private static final String ACCOUNTS = "/api/v3/platform/accounts";
  private static final String PROJECTS = "/api/v3/projects";
  private static final String PLATFORM = "/api/v3/platform/projects";
  private static final String TEMPORARY = "fixture-change-me";
  private static final String PASSWORD = "fixture-updated";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry registry) {
    AuthIntegrationTest.props(registry);
  }

  @Test
  void accountCreationRequiresAdminAndPasswordChangesRevokeTokens() throws Exception {
    String admin = login("admin", "admin123");
    String username = "workspace-" + UUID.randomUUID();
    var body = account(username);
    String key = UUID.randomUUID().toString();
    JsonNode created =
        body(request(post(ACCOUNTS), admin, key, body).andExpect(status().isCreated()));
    String id = created.get("userId").asText();
    assertThat(created.has("temporaryPassword")).isFalse();
    assertThat(created.get("mustChangePassword").asBoolean()).isTrue();
    request(post(ACCOUNTS), admin, key, body)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.userId").value(id));
    request(post(ACCOUNTS), admin, key, account("different-" + UUID.randomUUID()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

    String temporary = login(username, TEMPORARY);
    mvc.perform(get("/api/v3/account").header("Authorization", bearer(temporary)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mustChangePassword").value(true));
    mvc.perform(get(PROJECTS).header("Authorization", bearer(temporary)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    mvc.perform(get("/api/v1/users/me").header("Authorization", bearer(temporary)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

    String normal = activate(username, temporary);
    mvc.perform(get("/api/v3/account").header("Authorization", bearer(temporary)))
        .andExpect(status().isUnauthorized());
    request(post(ACCOUNTS), normal, null, account("other-" + UUID.randomUUID()))
        .andExpect(status().isForbidden());

    request(
            post(ACCOUNTS + "/" + id + "/reset"),
            admin,
            null,
            Map.of("temporaryPassword", TEMPORARY, "revision", 2))
        .andExpect(status().isNoContent());
    mvc.perform(get(PROJECTS).header("Authorization", bearer(normal)))
        .andExpect(status().isUnauthorized());
    String resetToken = login(username, TEMPORARY);
    request(patch(ACCOUNTS + "/" + id), admin, null, Map.of("enabled", false, "revision", 3))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false));
    mvc.perform(get("/api/v3/account").header("Authorization", bearer(resetToken)))
        .andExpect(status().isUnauthorized());
    request(
            post("/api/v1/auth/login"),
            null,
            null,
            Map.of("username", username, "password", TEMPORARY))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void projectAndHandoverUseMembershipInsteadOfPlatformPrivilege() throws Exception {
    String admin = login("admin", "admin123");
    Actor owner = newActor(admin);
    Actor dev = newActor(admin);
    Actor tester = newActor(admin);
    var newProject = Map.of("name", "仪表验收", "description", "fixture", "ownerId", owner.id());
    String key = UUID.randomUUID().toString();
    JsonNode created =
        body(request(post(PLATFORM), admin, key, newProject).andExpect(status().isCreated()));
    String id = created.get("projectId").asText();
    request(post(PLATFORM), admin, key, newProject)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.projectId").value(id));
    assertThat(created.get("myRole").isNull()).isTrue();
    request(post(PLATFORM), owner.token(), null, newProject).andExpect(status().isForbidden());
    request(
            post("/api/v2/projects"),
            owner.token(),
            null,
            Map.of("key", "LEGACY-ADMIN-ONLY", "name", "Fixture"))
        .andExpect(status().isForbidden());
    mvc.perform(get(PROJECTS + "/" + id).header("Authorization", bearer(admin)))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/v2/projects/" + id + "/test-cases").header("Authorization", bearer(admin)))
        .andExpect(status().isForbidden());
    request(
            patch(ACCOUNTS + "/" + owner.id()),
            admin,
            null,
            Map.of("enabled", false, "revision", 2))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("OWNER_TRANSFER_REQUIRED"));

    String project = PROJECTS + "/" + id;
    request(
            put(project + "/members/" + dev.id()),
            owner.token(),
            null,
            Map.of("revision", 0, "role", "DEV"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("DEV"));
    request(
            put(project + "/members/" + tester.id()),
            owner.token(),
            null,
            Map.of("revision", 0, "role", "TEST"))
        .andExpect(status().isOk());
    request(
            put(project + "/members/" + dev.id()),
            owner.token(),
            null,
            Map.of("revision", 0, "role", "TEST"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

    var pending =
        body(
            request(
                    post(project + "/owner-transfers"),
                    owner.token(),
                    null,
                    Map.of("newOwnerId", dev.id(), "formerRole", "TEST", "revision", 1))
                .andExpect(status().isCreated()));
    assertThat(pending.get("status").asText()).isEqualTo("PENDING");
    mvc.perform(get(project).header("Authorization", bearer(dev.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.myRole").value("DEV"));
    String transfer = PLATFORM + "/" + id + "/owner-transfers";
    request(
            post(transfer + "/" + pending.get("handoverId").asText() + "/decision"),
            admin,
            null,
            Map.of("decision", "APPROVE", "note", "", "revision", 1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"));
    mvc.perform(get(project).header("Authorization", bearer(dev.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.myRole").value("OWNER"));
    mvc.perform(get(project).header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.myRole").value("TEST"));
    request(
            post(transfer + "/proxy"),
            admin,
            null,
            Map.of(
                "newOwnerId",
                tester.id(),
                "formerRole",
                "DEV",
                "revision",
                2,
                "unavailableReason",
                ""))
        .andExpect(status().isBadRequest());
    request(
            post(transfer + "/proxy"),
            admin,
            null,
            Map.of(
                "newOwnerId",
                tester.id(),
                "formerRole",
                "DEV",
                "revision",
                2,
                "unavailableReason",
                "Fixture leave"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.mode").value("ADMIN_PROXY"));
    mvc.perform(get(project).header("Authorization", bearer(tester.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.myRole").value("OWNER"));
  }

  private Actor newActor(String admin) throws Exception {
    String username = "workspace-" + UUID.randomUUID();
    JsonNode created =
        body(
            request(post(ACCOUNTS), admin, null, account(username))
                .andExpect(status().isCreated()));
    return new Actor(
        created.get("userId").asText(), activate(username, login(username, TEMPORARY)));
  }

  private String activate(String username, String temporary) throws Exception {
    request(
            post("/api/v3/account/password"),
            temporary,
            null,
            Map.of("currentPassword", TEMPORARY, "newPassword", PASSWORD))
        .andExpect(status().isNoContent());
    return login(username, PASSWORD);
  }

  private ResultActions request(
      MockHttpServletRequestBuilder builder, String token, String key, Object payload)
      throws Exception {
    if (token != null) builder.header("Authorization", bearer(token));
    return mvc.perform(
        builder
            .header("Idempotency-Key", key == null ? UUID.randomUUID().toString() : key)
            .contentType("application/json")
            .content(json.writeValueAsBytes(payload)));
  }

  private String login(String username, String password) throws Exception {
    return body(request(
                post("/api/v1/auth/login"),
                null,
                null,
                Map.of("username", username, "password", password))
            .andExpect(status().isOk()))
        .get("accessToken")
        .asText();
  }

  private JsonNode body(ResultActions result) throws Exception {
    return json.readTree(result.andReturn().getResponse().getContentAsByteArray());
  }

  private static Map<String, String> account(String username) {
    return Map.of("username", username, "displayName", username, "temporaryPassword", TEMPORARY);
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }

  private record Actor(String id, String token) {}
}
