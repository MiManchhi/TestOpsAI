package io.testopsai.server.modules.assistant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testopsai.server.modules.identity.AppUser;
import io.testopsai.server.modules.knowledge.WorkspaceAnswerEvidence;
import io.testopsai.server.modules.knowledge.WorkspaceDocumentAccess;
import io.testopsai.server.modules.run.WorkspaceRunService;
import io.testopsai.server.platform.idempotency.DurableCommandReceipt;
import io.testopsai.server.platform.web.PageResponse;
import io.testopsai.server.platform.web.WorkspaceFailure;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class WorkspaceChatService {
  private static final String RESTRICTED = "相关资料权限已变化，请重新提问。";
  private static final String PROTECTED_TURN =
      """
    FROM workspace_chat_turn t JOIN workspace_chat c ON c.id=t.conversation_id
    WHERE c.project_id=? AND c.actor_id=? AND t.id=?
    """;
  private static final String LIVE_ACTOR =
      """
    AND EXISTS (SELECT 1 FROM project_members m JOIN users u ON u.id=m.user_id
      WHERE m.project_id=c.project_id AND m.user_id=c.actor_id AND m.role IN ('OWNER','DEV','TEST')
      AND u.enabled=TRUE AND u.must_change_password=FALSE)
    """;

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final WorkspaceDocumentAccess access;
  private final WorkspaceAnswerEvidence evidence;
  private final DurableCommandReceipt receipts;
  private final WorkspaceRunService runs;
  private final WorkspaceRunSelection selection;

  WorkspaceChatService(
      JdbcTemplate jdbc,
      ObjectMapper json,
      WorkspaceDocumentAccess access,
      WorkspaceAnswerEvidence evidence,
      DurableCommandReceipt receipts,
      WorkspaceRunService runs,
      WorkspaceRunSelection selection) {
    this.jdbc = jdbc;
    this.json = json;
    this.access = access;
    this.evidence = evidence;
    this.receipts = receipts;
    this.runs = runs;
    this.selection = selection;
  }

  @Transactional
  public WorkspaceChat.Turn start(
      AppUser actor, long project, String key, WorkspaceChat.Input input) {
    access.lock(project);
    access.member(actor, project);
    if (input == null
        || input.text() == null
        || input.text().length() > 20000
        || input.clientMessageId() == null
        || input.attachmentIds() == null
        || input.attachmentIds().size() > 4
        || input.attachmentIds().stream().anyMatch(java.util.Objects::isNull)
        || (input.text().isBlank() && input.attachmentIds().isEmpty())
        || new HashSet<>(input.attachmentIds()).size() != input.attachmentIds().size()) {
      throw new IllegalArgumentException("Invalid chat message or attachment list");
    }
    if (input.runId() != null
        && !"READY".equals(runs.detail(actor, project, input.runId()).parseStatus()))
      throw WorkspaceFailure.conflict("RUN_NOT_READY", "Robot results are not available yet");
    if (input.conversationId() != null)
      requireConversation(project, actor.id(), input.conversationId());
    return receipts.execute(
        actor.id(),
        "startTurn",
        project,
        key,
        input,
        WorkspaceChat.Turn.class,
        () -> createTurn(actor, project, input));
  }

  private WorkspaceChat.Turn createTurn(AppUser actor, long project, WorkspaceChat.Input input) {
    String conversation =
        input.conversationId() == null
            ? UUID.randomUUID().toString()
            : input.conversationId().toString();
    if (input.conversationId() == null) {
      var title = input.text().strip().replaceAll("\\s+", " ");
      if (title.isBlank()) title = "附件核对";
      if (title.codePointCount(0, title.length()) > 80)
        title = title.substring(0, title.offsetByCodePoints(0, 80));
      jdbc.update(
          "INSERT INTO workspace_chat VALUES (?,?,?,?,UTC_TIMESTAMP(6))",
          conversation,
          project,
          actor.id(),
          title);
    } else {
      if (has(
          "SELECT EXISTS(SELECT 1 FROM workspace_chat_turn WHERE conversation_id=? AND status IN ('QUEUED','RUNNING'))",
          conversation))
        throw WorkspaceFailure.conflict("TURN_ACTIVE", "An answer is already in progress");
      if (has(
          "SELECT EXISTS(SELECT 1 FROM workspace_chat_turn WHERE conversation_id=? AND client_message_id=?)",
          conversation,
          input.clientMessageId().toString()))
        throw WorkspaceFailure.conflict(
            "MESSAGE_EXISTS", "The message identifier was already used");
    }
    var choice = selection.resolve(actor, project, conversation, input);
    var files =
        input.attachmentIds().stream()
            .map(id -> evidence.claimFile(project, actor.id(), id))
            .toList();
    var attachmentDtos =
        files.stream()
            .map(
                file ->
                    Map.of(
                        "attachmentId",
                        file.attachmentId(),
                        "fileName",
                        file.logicalPath(),
                        "sha256",
                        file.sha256(),
                        "expiresAt",
                        file.expiresAt().toString()))
            .toList();
    String id = UUID.randomUUID().toString();
    jdbc.update(
        """
        INSERT INTO workspace_chat_turn
          (id,conversation_id,client_message_id,message,status,attachments,request_id,run_id)
        VALUES (?,?,?,?,'QUEUED',?,?,?)
        """,
        id,
        conversation,
        input.clientMessageId().toString(),
        input.text(),
        encode(attachmentDtos),
        UUID.randomUUID().toString(),
        choice.runId());
    for (var file : files) {
      jdbc.update("INSERT INTO workspace_chat_turn_file VALUES (?,?)", id, file.attachmentId());
    }
    if (choice.clarification() != null) {
      var answer = json.createObjectNode();
      answer.put("summary", choice.clarification());
      answer.put("robotStatus", "UNKNOWN");
      answer.putNull("robotDraft");
      answer.putArray("findings");
      answer.putArray("citations");
      answer.set("pending", json.valueToTree(choice.options()));
      jdbc.update(
          "UPDATE workspace_chat_turn SET status='COMPLETED', authorization_revision=?,authorized_versions=JSON_ARRAY(),answer=?,finished_at=UTC_TIMESTAMP(6) WHERE id=?",
          revision(project),
          encode(answer),
          id);
    }
    jdbc.update("UPDATE workspace_chat SET updated_at=UTC_TIMESTAMP(6) WHERE id=?", conversation);
    return new WorkspaceChat.Turn(
        conversation, id, choice.clarification() == null ? "QUEUED" : "COMPLETED");
  }

  public void requireMember(AppUser actor, long project) {
    access.member(actor, project);
  }

  @Transactional(readOnly = true)
  public PageResponse<WorkspaceChat.Conversation> history(
      AppUser actor, long project, int page, int size, String q) {
    access.member(actor, project);
    PageResponse.validatePagination(page, size);
    String search = q == null ? "" : q.strip();
    if (search.length() > 160) throw new IllegalArgumentException("Search is too long");
    String where = " FROM workspace_chat WHERE project_id=? AND actor_id=? AND LOCATE(?, title)>0";
    long total =
        jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, project, actor.id(), search);
    var items =
        jdbc.query(
            "SELECT id,title,updated_at"
                + where
                + " ORDER BY updated_at DESC,id DESC LIMIT ? OFFSET ?",
            (rs, n) ->
                new WorkspaceChat.Conversation(
                    rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant()),
            project,
            actor.id(),
            search,
            size,
            (long) page * size);
    return new PageResponse<>(items, total, page, size);
  }

  @Transactional(readOnly = true)
  public PageResponse<WorkspaceChat.Message> messages(
      AppUser actor, long project, UUID conversation, int page, int size) {
    access.member(actor, project);
    requireConversation(project, actor.id(), conversation);
    PageResponse.validatePagination(page, size);
    String id = conversation.toString();
    long revision = revision(project);
    long total =
        jdbc.queryForObject(
            """
      SELECT COUNT(*) + COALESCE(SUM(status='COMPLETED'),0) FROM workspace_chat_turn WHERE conversation_id=?
      """,
            Long.class,
            id);
    var items =
        jdbc.query(
            """
      SELECT * FROM (
        SELECT id AS turn_id,client_message_id AS message_id,created_at,message,attachments,
          NULL AS answer,authorization_revision,authorized_versions,0 AS assistant
        FROM workspace_chat_turn WHERE conversation_id=?
        UNION ALL
        SELECT id,id,finished_at,'',JSON_ARRAY(),answer,authorization_revision,authorized_versions,1
        FROM workspace_chat_turn WHERE conversation_id=? AND status='COMPLETED'
      ) AS messages ORDER BY created_at,assistant,turn_id LIMIT ? OFFSET ?
      """,
            (rs, n) -> {
              boolean assistant = rs.getBoolean("assistant");
              boolean canSee =
                  !assistant
                      || rs.getLong("authorization_revision") == revision
                      || evidence.allVisible(
                          project, actor.id(), tree(rs.getString("authorized_versions")));
              return new WorkspaceChat.Message(
                  rs.getString("message_id"),
                  assistant ? "ASSISTANT" : "USER",
                  assistant ? (canSee ? "" : RESTRICTED) : rs.getString("message"),
                  assistant && canSee ? tree(rs.getString("answer")) : null,
                  rs.getTimestamp("created_at").toInstant(),
                  tree(rs.getString("attachments")),
                  rs.getString("turn_id"));
            },
            id,
            id,
            size,
            (long) page * size);
    return new PageResponse<>(items, total, page, size);
  }

  @Transactional(readOnly = true)
  public WorkspaceChat.Turn turn(AppUser actor, long project, UUID id) {
    access.member(actor, project);
    return existingTurn(project, actor.id(), id);
  }

  private WorkspaceChat.Turn existingTurn(long project, long actor, UUID id) {
    return jdbc
        .query(
            "SELECT c.id,t.id,t.status " + PROTECTED_TURN,
            (rs, n) -> new WorkspaceChat.Turn(rs.getString(1), rs.getString(2), rs.getString(3)),
            project,
            actor,
            id.toString())
        .stream()
        .findFirst()
        .orElseThrow(WorkspaceFailure::notFound);
  }

  @Transactional
  public WorkspaceChat.Turn cancel(AppUser actor, long project, UUID turn, String key) {
    access.lock(project);
    access.member(actor, project);
    existingTurn(project, actor.id(), turn);
    return receipts.execute(
        actor.id(),
        "stopTurn",
        project,
        key,
        turn,
        WorkspaceChat.Turn.class,
        () -> {
          jdbc.update(
              """
        UPDATE workspace_chat_turn SET status='CANCELLED',finished_at=UTC_TIMESTAMP(6)
        WHERE id=? AND status IN ('QUEUED','RUNNING')
        """,
              turn.toString());
          return existingTurn(project, actor.id(), turn);
        });
  }

  @Transactional(readOnly = true)
  public List<StreamEvent> events(AppUser actor, long project, UUID turnId, int after) {
    access.member(actor, project);
    long revision = revision(project);
    return jdbc
        .query(
            "SELECT t.* " + PROTECTED_TURN,
            (rs, n) -> {
              String status = rs.getString("status");
              var events = new ArrayList<StreamEvent>();
              String id = turnId.toString();
              events.add(
                  new StreamEvent(
                      id, 1, "turn.started", rs.getTimestamp("created_at").toInstant(), Map.of()));
              var started = rs.getTimestamp("started_at");
              if (started != null)
                events.add(
                    new StreamEvent(
                        id, 2, "turn.stage", started.toInstant(), Map.of("stage", "COMPOSING")));
              var finished = rs.getTimestamp("finished_at");
              if (finished != null) {
                if (finished.toInstant().isBefore(Instant.now().minusSeconds(86400)))
                  throw new WorkspaceFailure(
                      org.springframework.http.HttpStatus.GONE,
                      "EVENT_REPLAY_EXPIRED",
                      "This turn's event replay has expired");

                boolean authorized =
                    rs.getLong("authorization_revision") == revision
                        || ("COMPLETED".equals(status)
                            && evidence.allVisible(
                                project, actor.id(), tree(rs.getString("authorized_versions"))));
                Map<String, Object> data;
                String name;
                if ("COMPLETED".equals(status) && authorized) {
                  name = "turn.completed";
                  data = Map.of("answer", tree(rs.getString("answer")));
                } else if ("CANCELLED".equals(status)) {
                  name = "turn.cancelled";
                  data = Map.of();
                } else {
                  name = "turn.failed";
                  boolean hidden = "COMPLETED".equals(status);
                  String code =
                      hidden
                          ? "AUTHORIZATION_CHANGED"
                          : publicFailureCode(rs.getString("failure_code"));
                  data =
                      Map.of(
                          "error",
                          Map.of(
                              "code",
                              code,
                              "message",
                              hidden ? RESTRICTED : failureMessage(code),
                              "requestId",
                              rs.getString("request_id")));
                }
                events.add(
                    new StreamEvent(id, started == null ? 2 : 3, name, finished.toInstant(), data));
              }
              return events.stream().filter(event -> event.sequence() > after).toList();
            },
            project,
            actor.id(),
            turnId.toString())
        .stream()
        .findFirst()
        .orElseThrow(WorkspaceFailure::notFound);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> citation(AppUser actor, long project, UUID id) {
    access.member(actor, project);
    var refs =
        jdbc.query(
            """
      SELECT x.source,t.run_id,t.authorized_versions,t.authorization_revision,p.auth_revision
      FROM workspace_chat_citation x JOIN workspace_chat_turn t ON t.id=x.turn_id
      JOIN workspace_chat c ON c.id=t.conversation_id JOIN projects p ON p.id=c.project_id
      WHERE x.id=? AND c.project_id=? AND c.actor_id=? AND t.status='COMPLETED'
      """,
            (rs, n) -> {
              if (rs.getLong(4) != rs.getLong(5)
                  && !evidence.allVisible(project, actor.id(), tree(rs.getString(3))))
                throw WorkspaceFailure.notFound();
              return citationView(
                  id.toString(),
                  project,
                  actor.id(),
                  rs.getObject(2, Long.class),
                  reference(tree(rs.getString(1))));
            },
            id.toString(),
            project,
            actor.id());
    return refs.stream().findFirst().orElseThrow(WorkspaceFailure::notFound);
  }

  @Transactional
  public WorkspaceChat.Job claim() {
    jdbc.update(
        """
      UPDATE workspace_chat_turn SET status='FAILED',finished_at=UTC_TIMESTAMP(6)
      WHERE status='RUNNING' AND started_at < UTC_TIMESTAMP(6)-INTERVAL 120 SECOND
      """);
    var jobs =
        jdbc.query(
            """
      SELECT t.id,c.id,c.project_id,c.actor_id,t.client_message_id,t.message,t.request_id,t.run_id
      FROM workspace_chat_turn t JOIN workspace_chat c ON c.id=t.conversation_id
      WHERE t.status='QUEUED' ORDER BY t.created_at,t.id LIMIT 1 FOR UPDATE SKIP LOCKED
      """,
            (rs, n) ->
                new WorkspaceChat.Job(
                    rs.getString(1),
                    rs.getString(2),
                    rs.getLong(3),
                    rs.getLong(4),
                    rs.getString(5),
                    rs.getString(6),
                    rs.getString(7),
                    rs.getObject(8, Long.class)));
    if (jobs.isEmpty()) return null;
    var job = jobs.getFirst();
    jdbc.update(
        "UPDATE workspace_chat_turn SET status='RUNNING',started_at=UTC_TIMESTAMP(6) WHERE id=?",
        job.turnId());
    return job;
  }

  @Transactional
  public WorkspaceChat.ScopeFacts authorize(WorkspaceChat.Job job) {
    access.lock(job.project());
    if (!active(job)) throw WorkspaceFailure.notFound();
    var run = job.run() == null ? null : runs.find(job.project(), job.run());
    String at = run == null ? null : evidence.basisIdAt(job.project(), run.executedAt());
    var versions = evidence.versions(job.project(), job.actor(), at);
    var ids = versions.stream().map(WorkspaceAnswerEvidence.Version::versionId).toList();
    // A follow-up can refer to the previous attachment set. New uploads replace that set.
    if (!has("SELECT EXISTS(SELECT 1 FROM workspace_chat_turn_file WHERE turn_id=?)", job.turnId()))
      jdbc.update(
          """
        INSERT INTO workspace_chat_turn_file (turn_id,attachment_id)
        SELECT ?, f.attachment_id FROM workspace_chat_turn_file f
        JOIN workspace_attachment a ON a.id=f.attachment_id
        WHERE f.turn_id=(SELECT t.id FROM workspace_chat_turn t WHERE t.conversation_id=? AND t.id<>? AND t.status='COMPLETED' ORDER BY created_at DESC,id DESC LIMIT 1)
          AND a.project_id=? AND a.uploaded_by=? AND a.expires_at>UTC_TIMESTAMP(6)
        """,
          job.turnId(),
          job.conversation(),
          job.turnId(),
          job.project(),
          job.actor());
    var files =
        jdbc.queryForList(
            "SELECT attachment_id FROM workspace_chat_turn_file WHERE turn_id=? ORDER BY attachment_id",
            String.class,
            job.turnId());
    long version = revision(job.project());
    jdbc.update(
        "UPDATE workspace_chat_turn SET authorization_revision=?,authorized_versions=? WHERE id=?",
        version,
        encode(ids),
        job.turnId());
    var scope =
        new WorkspaceChat.Scope(
            Long.toString(job.project()),
            job.turnId(),
            ids,
            job.run() == null ? List.of() : List.of(Long.toString(job.run())),
            files,
            version);
    return new WorkspaceChat.ScopeFacts(
        scope,
        evidence.basis(evidence.basisIdAt(job.project(), null), ids),
        evidence.basis(at, ids),
        run,
        run == null ? List.of() : runs.results(job.run(), 0, 100).items(),
        run == null ? List.of() : WorkspaceRunAssessment.logProofs(runs, job.run()),
        versions,
        files.stream()
            .map(UUID::fromString)
            .map(id -> evidence.claimFile(job.project(), job.actor(), id))
            .toList(),
        priorMessages(job, ids));
  }

  private List<WorkspaceChat.Message> priorMessages(
      WorkspaceChat.Job job, List<String> authorizedVersions) {
    var history = new ArrayList<WorkspaceChat.Message>();
    jdbc.query(
        """
        SELECT * FROM (SELECT id,client_message_id,message,attachments,answer,created_at,finished_at,authorized_versions
          FROM workspace_chat_turn WHERE conversation_id=? AND id<>? AND status='COMPLETED'
          ORDER BY created_at DESC,id DESC LIMIT 6) recent ORDER BY created_at,id
        """,
        (org.springframework.jdbc.core.RowCallbackHandler)
            rs -> {
              var id = rs.getString("id");
              var documents = tree(rs.getString("authorized_versions"));
              if (!documents.isArray()) return;
              for (var version : documents)
                if (!authorizedVersions.contains(version.asText())) return;
              history.add(
                  new WorkspaceChat.Message(
                      rs.getString("client_message_id"),
                      "USER",
                      shortText(rs.getString("message")),
                      null,
                      rs.getTimestamp("created_at").toInstant(),
                      tree(rs.getString("attachments")),
                      id));
              var answer =
                  (com.fasterxml.jackson.databind.node.ObjectNode) tree(rs.getString("answer"));
              // Citations are resolved against the current turn's grant, never replayed as fresh
              // evidence.
              answer.remove("citations");
              history.add(
                  new WorkspaceChat.Message(
                      id,
                      "ASSISTANT",
                      answer.path("summary").asText(),
                      answer,
                      rs.getTimestamp("finished_at").toInstant(),
                      json.createArrayNode(),
                      id));
            },
        job.conversation(),
        job.turnId());
    return history;
  }

  String shortText(String text) {
    return text.substring(
        0, text.offsetByCodePoints(0, Math.min(2500, text.codePointCount(0, text.length()))));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> resolve(
      long project,
      long actor,
      long revision,
      UUID turn,
      WorkspaceAnswerEvidence.EvidenceRef source) {
    if (revision(project) != revision) throw WorkspaceFailure.notFound();
    var allowed =
        jdbc
            .query(
                "SELECT t.authorized_versions,t.run_id "
                    + PROTECTED_TURN
                    + LIVE_ACTOR
                    + " AND t.status='RUNNING' AND t.authorization_revision=?",
                (rs, n) -> new EvidenceScope(tree(rs.getString(1)), rs.getObject(2, Long.class)),
                project,
                actor,
                turn.toString(),
                revision)
            .stream()
            .findFirst()
            .orElseThrow(WorkspaceFailure::notFound);
    if ("RUN_LOG".equals(source.resourceKind())) {
      var proof = logProof(allowed.run(), source);
      return Map.of("source", source, "excerpt", proof.text());
    }
    boolean documentAllowed = false;
    for (var id : allowed.versions())
      if (id.asText().equals(source.resourceId())) documentAllowed = true;
    if (!documentAllowed) throw WorkspaceFailure.notFound();
    var proof = evidence.proof(project, actor, source);
    return Map.of("source", source, "excerpt", proof.excerpt());
  }

  private record EvidenceScope(JsonNode versions, Long run) {}

  @Transactional
  public void complete(WorkspaceChat.Job job, WorkspaceChat.ScopeFacts facts, JsonNode candidate) {
    access.lock(job.project());
    if (!active(job)) return;
    if (revision(job.project()) != facts.scope().authRevision()) {
      fail(job);
      return;
    }
    if (facts.run() != null
        && candidate.path("findings").isArray()
        && !candidate.path("findings").isEmpty()) {
      var answer =
          WorkspaceRunAssessment.compare(json, evidence, job, facts, ref -> saveCitation(job, ref));
      storeAnswer(job, answer);
      return;
    }
    JsonNode summary = candidate.path("summary");
    JsonNode draft = candidate.path("robotDraft");
    JsonNode pending = candidate.path("pending");
    JsonNode sources = candidate.path("sources");
    if (!summary.isTextual()
        || summary.asText().isBlank()
        || summary.asText().length() > 5000
        || (!draft.isNull() && (!draft.isTextual() || draft.asText().length() > 20000))
        || !pending.isArray()
        || pending.size() > 20
        || !sources.isArray()
        || sources.size() > 12
        || !candidate.path("findings").isArray()
        || !candidate.path("findings").isEmpty()) {
      throw new IllegalArgumentException("AI returned an invalid answer");
    }
    for (var item : pending)
      if (!item.isTextual() || item.asText().isBlank() || item.asText().length() > 500)
        throw new IllegalArgumentException("Invalid pending item");
    var citations = json.createArrayNode();
    for (JsonNode item : sources) {
      var ref = reference(item);
      if ("DOCUMENT".equals(ref.resourceKind())) {
        if (!facts.scope().versionIds().contains(ref.resourceId()))
          throw WorkspaceFailure.notFound();
      } else if (!"RUN_LOG".equals(ref.resourceKind())
          || facts.logProofs().stream().noneMatch(p -> p.source().equals(ref))) {
        throw WorkspaceFailure.notFound();
      }
      citations.add(json.valueToTree(saveCitation(job, ref)));
    }
    var answer = json.createObjectNode();
    answer.put("summary", summary.asText());
    answer.put(
        "robotStatus",
        facts.run() == null ? "NOT_RUN" : WorkspaceRunAssessment.robotStatus(facts.run()));
    answer.putArray("findings");
    answer.set("robotDraft", draft);
    answer.set("pending", pending);
    answer.set("citations", citations);
    storeAnswer(job, answer);
  }

  private void storeAnswer(WorkspaceChat.Job job, JsonNode answer) {
    jdbc.update(
        """
      UPDATE workspace_chat_turn SET status='COMPLETED',answer=?,finished_at=UTC_TIMESTAMP(6)
      WHERE id=? AND status='RUNNING'
      """,
        encode(answer),
        job.turnId());
    jdbc.update(
        "UPDATE workspace_chat SET updated_at=UTC_TIMESTAMP(6) WHERE id=?", job.conversation());
  }

  static String publicFailureCode(String code) {
    if (code == null) return "ANSWER_UNAVAILABLE";
    return switch (code) {
      case "MODEL_NOT_CONFIGURED",
          "EMBEDDING_NOT_CONFIGURED",
          "ANSWER_MODEL_FAILED",
          "EMBEDDING_UNAVAILABLE",
          "ANSWER_PROVIDER_AUTH_FAILED",
          "EMBEDDING_PROVIDER_AUTH_FAILED",
          "ANSWER_TIMEOUT" ->
          code;
      default -> "ANSWER_UNAVAILABLE";
    };
  }

  static String failureMessage(String code) {
    return switch (code) {
      case "MODEL_NOT_CONFIGURED" -> "尚未配置聊天模型。请联系管理员配置模型后重试。";
      case "EMBEDDING_NOT_CONFIGURED" -> "尚未配置检索向量服务。请联系管理员配置后重试。";
      case "ANSWER_MODEL_FAILED" -> "聊天模型调用失败。请检查模型配置和连通性后重试。";
      case "EMBEDDING_UNAVAILABLE" -> "知识检索服务暂不可用，请稍后重试。";
      case "ANSWER_PROVIDER_AUTH_FAILED" -> "聊天模型拒绝了 API Key。请使用与 Base URL 匹配的百炼标准 API Key。";
      case "EMBEDDING_PROVIDER_AUTH_FAILED" -> "向量模型拒绝了 API Key。请使用与 Base URL 匹配的百炼标准 API Key。";
      case "ANSWER_TIMEOUT" -> "回答超时，请缩小问题范围后重试。";
      default -> "回答未完成，请稍后重试。";
    };
  }

  @Transactional
  public void fail(WorkspaceChat.Job job) {
    fail(job, "ANSWER_UNAVAILABLE");
  }

  @Transactional
  public void fail(WorkspaceChat.Job job, String cause) {
    jdbc.update(
        """
      UPDATE workspace_chat_turn SET status='FAILED',failure_code=?,finished_at=UTC_TIMESTAMP(6)
      WHERE id=? AND status='RUNNING'
      """,
        publicFailureCode(cause),
        job.turnId());
  }

  private Map<String, Object> saveCitation(
      WorkspaceChat.Job job, WorkspaceAnswerEvidence.EvidenceRef ref) {
    String id = UUID.randomUUID().toString();
    var view = citationView(id, job.project(), job.actor(), job.run(), ref);
    jdbc.update(
        "INSERT INTO workspace_chat_citation VALUES (?,?,?)", id, job.turnId(), encode(ref));
    return view;
  }

  private Map<String, Object> citationView(
      String id, long project, long actor, Long run, WorkspaceAnswerEvidence.EvidenceRef ref) {
    if ("RUN_LOG".equals(ref.resourceKind())) {
      var proof = logProof(run, ref);
      return Map.of(
          "citationId",
          id,
          "source",
          ref,
          "label",
          proof.name() + " · " + ref.locator(),
          "excerpt",
          proof.text(),
          "projectSource",
          Map.of(
              "kind",
              "RUN_LOG",
              "runId",
              Long.toString(run),
              "artifactId",
              Long.toString(proof.artifact()),
              "fromLine",
              proof.line()));
    }
    return citationView(id, evidence.proof(project, actor, ref));
  }

  private WorkspaceRunService.LogProof logProof(Long run, WorkspaceAnswerEvidence.EvidenceRef ref) {
    if (run == null
        || ref.resourceId() == null
        || !ref.resourceId().matches("[1-9][0-9]{0,18}")
        || ref.locator() == null
        || !ref.locator().matches("line [1-9][0-9]{0,6}")) throw WorkspaceFailure.notFound();
    var proof =
        runs.proof(
            run, Long.parseLong(ref.resourceId()), Integer.parseInt(ref.locator().substring(5)));
    if (!proof.hash().equals(ref.version()) || proof.text().isBlank())
      throw WorkspaceFailure.notFound();
    return proof;
  }

  private Map<String, Object> citationView(String id, WorkspaceAnswerEvidence.DocumentProof proof) {
    return Map.of(
        "citationId",
        id,
        "source",
        proof.source(),
        "label",
        proof.title() + " " + proof.label() + " · " + proof.locator(),
        "excerpt",
        proof.excerpt(),
        "projectSource",
        Map.of("kind", "DOCUMENT", "versionId", proof.versionId(), "locator", proof.locator()));
  }

  private void requireConversation(long project, long actor, UUID id) {
    if (!has(
        "SELECT EXISTS(SELECT 1 FROM workspace_chat WHERE id=? AND project_id=? AND actor_id=?)",
        id.toString(),
        project,
        actor)) throw WorkspaceFailure.notFound();
  }

  private boolean active(WorkspaceChat.Job job) {
    return has(
        "SELECT EXISTS(SELECT 1 " + PROTECTED_TURN + LIVE_ACTOR + " AND t.status='RUNNING')",
        job.project(),
        job.actor(),
        job.turnId());
  }

  private boolean has(String sql, Object... params) {
    return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, params));
  }

  private long revision(long project) {
    return jdbc.queryForObject(
        "SELECT auth_revision FROM projects WHERE id=?", Long.class, project);
  }

  private WorkspaceAnswerEvidence.EvidenceRef reference(JsonNode node) {
    return new WorkspaceAnswerEvidence.EvidenceRef(
        node.path("resourceKind").asText(),
        node.path("resourceId").asText(),
        node.path("version").asText(),
        node.path("locator").asText());
  }

  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Could not encode chat data", ex);
    }
  }

  private JsonNode tree(String value) {
    try {
      return json.readTree(value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Could not read chat data", ex);
    }
  }

  record StreamEvent(
      String turnId, int sequence, String event, Instant occurredAt, Map<String, Object> data) {}
}
