package io.testopsai.server.modules.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.testopsai.server.modules.identity.AppUser;
import io.testopsai.server.modules.identity.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
      "spring.datasource.url=${MYSQL_URL}",
      "testopsai.jwt.secret=workspace-regression-secret-at-least-thirty-two-characters"
    })
@EnabledIfEnvironmentVariable(named = "MYSQL_URL", matches = ".+")
@Transactional
class WorkspaceChatContextTest {
  @Autowired WorkspaceChatService chat;
  @Autowired JdbcTemplate jdbc;
  @Autowired UserRepository users;
  @Autowired ObjectMapper json;
  AppUser actor;
  long project;
  UUID file;

  @BeforeEach
  void project() {
    actor = users.findByUsername("admin").orElseThrow();
    String key = UUID.randomUUID().toString();
    jdbc.update("INSERT INTO projects(project_key,name) VALUES (?,?)", key, "联调");
    project = jdbc.queryForObject("SELECT id FROM projects WHERE project_key=?", Long.class, key);
    jdbc.update(
        "INSERT INTO project_members(project_id,user_id,role) VALUES (?,?,'OWNER')",
        project,
        actor.id());
    file = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO workspace_attachment VALUES (?,?,?,?,?,1,UTC_TIMESTAMP(6)+INTERVAL 1 HOUR)",
        file.toString(),
        project,
        actor.id(),
        "case.robot",
        "a".repeat(64));
  }

  void addRun(String name, String timestamp) {
    jdbc.update(
        "INSERT INTO workspace_run(project_id,provided_by,name,executed_at,job_id,output_object_id,parse_status,total,passed) VALUES (?,?,?,?,?,?,'READY',1,1)",
        project,
        actor.id(),
        name,
        timestamp,
        UUID.randomUUID().toString(),
        file.toString());
  }

  WorkspaceChat.Turn turn(String text, String conversation, List<UUID> files) {
    return chat.start(
        actor,
        project,
        UUID.randomUUID().toString(),
        new WorkspaceChat.Input(
            text,
            UUID.randomUUID(),
            conversation == null ? null : UUID.fromString(conversation),
            files,
            null));
  }

  @Test
  void followupInheritsAuthorizedFilesAndPreservesDraftForRun() throws Exception {
    addRun("新复测", "2026-11-19 10:00:00");
    var t = turn("最近一次复测通过了吗？", null, List.of(file));
    var job = chat.claim();
    assertThat(job.turnId()).isEqualTo(t.turnId());
    var facts = chat.authorize(job);
    assertThat(facts.run().name()).isEqualTo("新复测");
    assertThat(facts.scope().attachmentIds()).containsExactly(file.toString());
    var draft =
        json.readTree(
            """
      {"summary":"检查拒绝请求","findings":[],"sources":[],"pending":[],"robotDraft":"*** Test Cases ***\\n拒绝时记录原因"}
      """);
    chat.complete(job, facts, draft);
    var next = turn("补上缺失步骤", t.conversationId(), List.of());
    job = chat.claim();
    assertThat(job.turnId()).isEqualTo(next.turnId());
    facts = chat.authorize(job);
    assertThat(facts.files()).hasSize(1);
    assertThat(facts.scope().runIds()).containsExactly(facts.run().runId());
    assertThat(facts.history())
        .extracting(WorkspaceChat.Message::author)
        .containsExactly("USER", "ASSISTANT");
    assertThat(facts.history().getLast().answer().path("robotDraft").asText()).contains("拒绝时记录原因");
    assertThat(facts.history().getLast().answer().path("robotStatus").asText()).isEqualTo("PASS");
    chat.fail(job, "MODEL_NOT_CONFIGURED");
    assertThat(
            chat.events(actor, project, UUID.fromString(job.turnId()), 0)
                .getLast()
                .data()
                .toString())
        .contains("MODEL_NOT_CONFIGURED");
  }

  @Test
  void ambiguityAsksForSelectionAndRevokedHistoryIsExcluded() throws Exception {
    addRun("甲", "2026-11-19 10:00:00");
    addRun("乙", "2026-11-19 10:00:00");
    var t = turn("最近一次复测符合要求吗？", null, List.of());
    var a =
        chat.messages(actor, project, UUID.fromString(t.conversationId()), 0, 10)
            .items()
            .getLast()
            .answer();
    assertThat(a.path("pending").size()).isEqualTo(2);
    assertThat(a.path("summary").asText()).contains("确认");
    var id =
        jdbc.queryForObject(
            "SELECT MIN(id) FROM workspace_run WHERE project_id=?", Long.class, project);
    jdbc.update(
        "UPDATE workspace_chat_turn SET authorized_versions=JSON_ARRAY('99999999') WHERE id=?",
        t.turnId());
    turn("运行 #" + id, t.conversationId(), List.of());
    var job = chat.claim();
    var facts = chat.authorize(job);
    assertThat(facts.run().name()).isEqualTo("甲");
    assertThat(facts.history()).isEmpty();
  }
}
