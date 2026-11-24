package io.testopsai.server.modules.run;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.testopsai.server.modules.identity.AppUser;
import io.testopsai.server.modules.knowledge.WorkspaceAttachmentService;
import io.testopsai.server.modules.knowledge.WorkspaceDocumentAccess;
import io.testopsai.server.platform.idempotency.DurableCommandReceipt;
import io.testopsai.server.platform.web.PageResponse;
import io.testopsai.server.platform.web.WorkspaceFailure;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceRunService {
  private static final String DISPLAY =
      "SELECT r.*,u.display_name FROM workspace_run r JOIN users u ON u.id=r.provided_by WHERE r.project_id=?";
  private final JdbcTemplate jdbc;
  private final WorkspaceAttachmentService files;
  private final WorkspaceDocumentAccess access;
  private final DurableCommandReceipt receipts;

  WorkspaceRunService(
      JdbcTemplate jdbc,
      WorkspaceAttachmentService files,
      WorkspaceDocumentAccess access,
      DurableCommandReceipt receipts) {
    this.jdbc = jdbc;
    this.files = files;
    this.access = access;
    this.receipts = receipts;
  }

  @Transactional
  public Run register(AppUser actor, long project, String key, Registration request) {
    access.lock(project);
    access.member(actor, project);
    if (request.name() == null
        || request.name().isBlank()
        || request.name().length() > 160
        || request.outputAttachmentId() == null
        || request.executedAt() == null
        || request.logAttachmentIds() == null
        || request.logAttachmentIds().size() > 10
        || new HashSet<>(request.logAttachmentIds()).size() != request.logAttachmentIds().size()) {
      throw new IllegalArgumentException("Invalid run registration");
    }
    if (request.executedAt().isBefore(Instant.parse("1000-01-01T00:00:00Z"))
        || request.executedAt().isAfter(Instant.parse("9999-12-31T23:59:59Z")))
      throw new IllegalArgumentException("Run timestamp is outside the supported range");
    return receipts.execute(
        actor.id(),
        "registerRun",
        project,
        key,
        request,
        Run.class,
        () -> insert(actor, project, request));
  }

  private Run insert(AppUser actor, long project, Registration request) {
    var output = files.claimPrivate(actor, project, request.outputAttachmentId());
    if (!output.fileName().equalsIgnoreCase("output.xml"))
      throw new IllegalArgumentException("Select the Robot Framework output.xml");
    var logs =
        request.logAttachmentIds().stream()
            .map(id -> files.claimPrivate(actor, project, id))
            .toList();
    for (var log : logs) {
      if (!log.fileName().toLowerCase(java.util.Locale.ROOT).matches(".+\\.(log|txt)"))
        throw new IllegalArgumentException("Log files must use .txt or .log");
    }
    var generated = new GeneratedKeyHolder();
    jdbc.update(
        connection -> {
          var sql =
              connection.prepareStatement(
                  """
        INSERT INTO workspace_run (project_id,provided_by,name,executed_at,job_id,output_object_id,parse_status)
        VALUES (?,?,?,?,?,?,'QUEUED')
        """,
                  Statement.RETURN_GENERATED_KEYS);
          sql.setLong(1, project);
          sql.setLong(2, actor.id());
          sql.setString(3, request.name().strip());
          sql.setTimestamp(4, Timestamp.from(request.executedAt()));
          sql.setString(5, UUID.randomUUID().toString());
          sql.setString(6, output.attachmentId());
          return sql;
        },
        generated);
    long run = java.util.Objects.requireNonNull(generated.getKey()).longValue();
    jdbc.update(
        "INSERT INTO workspace_run_artifact (run_id,attachment_id,kind) VALUES (?,?,'OUTPUT_XML')",
        run,
        output.attachmentId());
    for (var log : logs)
      jdbc.update(
          "INSERT INTO workspace_run_artifact (run_id,attachment_id,kind) VALUES (?,?,'LOG_TEXT')",
          run,
          log.attachmentId());
    return find(project, run);
  }

  @Transactional(readOnly = true)
  public PageResponse<Run> list(AppUser actor, long project, int page, int size, String q) {
    access.member(actor, project);
    PageResponse.validatePagination(page, size);
    String query = q == null ? "" : q.strip();
    if (query.length() > 160) throw new IllegalArgumentException("Search is too long");
    long count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM workspace_run WHERE project_id=? AND LOCATE(?,name)>0",
            Long.class,
            project,
            query);
    var rows =
        jdbc.query(
            DISPLAY
                + " AND LOCATE(?,r.name)>0 ORDER BY r.executed_at DESC,r.id DESC LIMIT ? OFFSET ?",
            WorkspaceRunService::mapRun,
            project,
            query,
            size,
            (long) page * size);
    return new PageResponse<>(rows, count, page, size);
  }

  @Transactional(readOnly = true)
  public Run detail(AppUser actor, long project, long run) {
    access.member(actor, project);
    return find(project, run);
  }

  public Run find(long project, long run) {
    return jdbc.query(DISPLAY + " AND r.id=?", WorkspaceRunService::mapRun, project, run).stream()
        .findFirst()
        .orElseThrow(WorkspaceFailure::notFound);
  }

  private static Run mapRun(ResultSet rs, int row) throws SQLException {
    return new Run(
        rs.getString("id"),
        rs.getString("name"),
        rs.getTimestamp("executed_at").toInstant(),
        new Provider(rs.getString("provided_by"), rs.getString("display_name")),
        rs.getString("parse_status"),
        rs.getString("output_object_id"),
        rs.getInt("total"),
        rs.getInt("passed"),
        rs.getInt("failed"),
        rs.getInt("skipped"),
        rs.getLong("revision"),
        rs.getString("error_code"));
  }

  @Transactional(readOnly = true)
  public PageResponse<Result> results(AppUser actor, long project, long run, int page, int size) {
    detail(actor, project, run);
    return results(run, page, size);
  }

  public PageResponse<Result> results(long run, int page, int size) {
    PageResponse.validatePagination(page, size);
    long count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM workspace_run_result WHERE run_id=?", Long.class, run);
    var rows =
        jdbc.query(
            """
      SELECT id,case_name,suite,robot_status,artifact_id
      FROM workspace_run_result WHERE run_id=? ORDER BY ordinal LIMIT ? OFFSET ?
      """,
            (rs, n) ->
                new Result(
                    rs.getString(1),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getString(4),
                    rs.getString(5)),
            run,
            size,
            (long) page * size);
    return new PageResponse<>(rows, count, page, size);
  }

  @Transactional(readOnly = true)
  public PageResponse<Artifact> artifacts(
      AppUser actor, long project, long run, int page, int size) {
    detail(actor, project, run);
    PageResponse.validatePagination(page, size);
    long count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM workspace_run_artifact WHERE run_id=?", Long.class, run);
    var rows =
        jdbc.query(
            """
      SELECT a.id,f.file_name,a.kind,f.sha256
      FROM workspace_run_artifact a JOIN workspace_attachment f ON f.id=a.attachment_id
      WHERE a.run_id=? ORDER BY a.id LIMIT ? OFFSET ?
      """,
            (rs, n) ->
                new Artifact(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)),
            run,
            size,
            (long) page * size);
    return new PageResponse<>(rows, count, page, size);
  }

  @Transactional(readOnly = true)
  public Download download(AppUser actor, long project, long run, long artifact) {
    detail(actor, project, run);
    var stored = artifact(run, artifact);
    return new Download(
        files.object(UUID.fromString(stored.attachmentId())), stored.name(), stored.bytes());
  }

  @Transactional(readOnly = true)
  public PageResponse<LogLine> logLines(
      AppUser actor, long project, long run, long id, int page, int size, int fromLine) {
    detail(actor, project, run);
    PageResponse.validatePagination(page, size);
    if (fromLine < 1) throw new IllegalArgumentException("Line range must be positive");
    var entry = artifact(run, id);
    if (!entry.kind().equals("LOG_TEXT")) throw WorkspaceFailure.notFound();
    String text = log(entry);
    long total = text.lines().skip(fromLine - 1L).count();
    long offset = (long) page * size + fromLine - 1;
    var rows = new ArrayList<LogLine>();
    text.lines()
        .skip(offset)
        .limit(size)
        .forEach(line -> rows.add(new LogLine(Math.toIntExact(offset + rows.size() + 1), line)));
    return new PageResponse<>(rows, total, page, size);
  }

  private Stored artifact(long run, long artifactId) {
    return jdbc
        .query(
            """
      SELECT a.id,a.attachment_id,a.kind,f.file_name,f.size_bytes,f.sha256
      FROM workspace_run_artifact a JOIN workspace_attachment f ON f.id=a.attachment_id
      WHERE a.run_id=? AND a.id=?
      """,
            WorkspaceRunService::mapStored,
            run,
            artifactId)
        .stream()
        .findFirst()
        .orElseThrow(WorkspaceFailure::notFound);
  }

  private static Stored mapStored(ResultSet rs, int index) throws SQLException {
    return new Stored(
        rs.getLong(1),
        rs.getString(2),
        rs.getString(3),
        rs.getString(4),
        rs.getLong(5),
        rs.getString(6));
  }

  public List<LogProof> evidenceLines(long run) {
    var entries =
        jdbc.query(
            """
      SELECT a.id,a.attachment_id,a.kind,f.file_name,f.size_bytes,f.sha256
      FROM workspace_run_artifact a JOIN workspace_attachment f ON f.id=a.attachment_id
      WHERE a.run_id=? AND a.kind='LOG_TEXT' ORDER BY a.id
      """,
            WorkspaceRunService::mapStored,
            run);
    var measurements = new ArrayList<LogProof>();
    var diagnostics = new ArrayList<LogProof>();
    var context = new ArrayList<LogProof>();
    var diagnostic =
        java.util.regex.Pattern.compile(
            "(?i)\\b(?:error|warn|fail|exception|denied|reject|timeout)\\w*|错误|拒绝|失败|异常|超时");
    var key = java.util.regex.Pattern.compile("(?i)response_time|response[ -]time|响应时间|抄读耗时");
    for (var entry : entries) {
      int number = 0;
      for (var line : (Iterable<String>) log(entry).lines()::iterator) {
        number++;
        if (line.isBlank() || line.length() > 1000) continue;
        var proof = new LogProof(run, entry.id(), entry.name(), entry.hash(), number, line);
        if (key.matcher(line).find()) {
          if (measurements.size() < 50) measurements.add(proof);
        } else if (diagnostic.matcher(line).find()) {
          if (diagnostics.size() < 30) diagnostics.add(proof);
        } else if (context.size() < 20) context.add(proof);
      }
    }
    measurements.addAll(diagnostics);
    measurements.addAll(context);
    return measurements;
  }

  public LogProof proof(long run, long artifactId, int line) {
    var entry = artifact(run, artifactId);
    if (!entry.kind().equals("LOG_TEXT") || line < 1) throw WorkspaceFailure.notFound();
    String text =
        log(entry).lines().skip(line - 1L).findFirst().orElseThrow(WorkspaceFailure::notFound);
    return new LogProof(run, entry.id(), entry.name(), entry.hash(), line, text);
  }

  private String log(Stored file) {
    try (var stream = files.object(UUID.fromString(file.attachmentId())).getInputStream()) {
      return new String(stream.readNBytes(5 * 1024 * 1024), StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new WorkspaceFailure(
          HttpStatus.SERVICE_UNAVAILABLE, "OBJECT_STORE_UNAVAILABLE", "Log is unavailable");
    }
  }

  @Transactional
  public ParseJob claim() {
    jdbc.update(
        """
      UPDATE workspace_run SET parse_status='QUEUED',revision=revision+1,updated_at=UTC_TIMESTAMP(6),started_at=NULL
      WHERE parse_status='PROCESSING' AND started_at<UTC_TIMESTAMP(6)-INTERVAL 150 SECOND
      """);
    var rows =
        jdbc.query(
            """
      SELECT r.id,r.project_id,r.provided_by,r.job_id,r.revision,p.auth_revision,
        a.id, f.id,f.file_name,f.sha256,f.size_bytes
      FROM workspace_run r JOIN projects p ON p.id=r.project_id
      JOIN workspace_run_artifact a ON a.run_id=r.id AND a.kind='OUTPUT_XML'
      JOIN workspace_attachment f ON f.id=r.output_object_id
      WHERE r.parse_status='QUEUED' ORDER BY r.updated_at,r.id LIMIT 1 FOR UPDATE SKIP LOCKED
      """,
            (rs, n) ->
                new ParseJob(
                    rs.getLong(1),
                    rs.getLong(2),
                    rs.getLong(3),
                    rs.getString(4),
                    rs.getLong(5) + 1,
                    rs.getLong(6),
                    rs.getLong(7),
                    rs.getString(8),
                    rs.getString(10),
                    rs.getLong(11)));
    if (rows.isEmpty()) return null;
    var job = rows.getFirst();
    jdbc.update(
        """
      UPDATE workspace_run SET parse_status='PROCESSING',revision=?,updated_at=UTC_TIMESTAMP(6),started_at=UTC_TIMESTAMP(6)
      WHERE id=?
      """,
        job.revision(),
        job.run());
    return job;
  }

  @Transactional
  public void complete(ParseJob job, JsonNode response) {
    if (jdbc.update(
            """
        UPDATE workspace_run SET updated_at=UTC_TIMESTAMP(6) WHERE id=? AND revision=? AND parse_status='PROCESSING'
        """,
            job.run(),
            job.revision())
        != 1) return;
    JsonNode rows = response.path("results");
    if (!rows.isArray() || rows.size() > 10000)
      throw new IllegalArgumentException("Invalid parser response");
    int passed = 0, failed = 0, skipped = 0, position = 0;
    var data = new ArrayList<Object[]>();
    for (var row : rows) {
      String status = row.path("status").asText();
      switch (status) {
        case "PASS" -> passed++;
        case "FAIL" -> failed++;
        case "SKIP" -> skipped++;
        default -> throw new IllegalArgumentException("Invalid Robot result status");
      }
      String name = row.path("caseName").asText();
      String suite = row.path("suitePath").asText();
      if (name.isBlank() || name.length() > 160 || suite.isBlank() || suite.length() > 512)
        throw new IllegalArgumentException("Invalid Robot result name");
      data.add(new Object[] {job.run(), job.artifact(), ++position, name, suite, status});
    }
    var counts = response.path("counts");
    if (counts.path("total").asInt(-1) != position
        || counts.path("pass").asInt(-1) != passed
        || counts.path("fail").asInt(-1) != failed
        || counts.path("skip").asInt(-1) != skipped)
      throw new IllegalArgumentException("Parser counters do not match individual results");
    jdbc.batchUpdate(
        """
      INSERT INTO workspace_run_result (run_id,artifact_id,ordinal,case_name,suite,robot_status)
      VALUES (?,?,?,?,?,?)
      """,
        data);
    jdbc.update(
        """
      UPDATE workspace_run SET parse_status='READY',revision=revision+1,total=?,passed=?,failed=?,skipped=?,
        updated_at=UTC_TIMESTAMP(6) WHERE id=?
      """,
        position,
        passed,
        failed,
        skipped,
        job.run());
  }

  @Transactional
  public void fail(ParseJob job, String code) {
    jdbc.update(
        """
      UPDATE workspace_run SET parse_status='FAILED',revision=revision+1,error_code=?,updated_at=UTC_TIMESTAMP(6)
      WHERE id=? AND revision=? AND parse_status='PROCESSING'
      """,
        code,
        job.run(),
        job.revision());
  }

  public record Run(
      String runId,
      String name,
      Instant executedAt,
      Provider providedBy,
      String parseStatus,
      String outputObjectId,
      int total,
      @JsonProperty("pass") int passed,
      @JsonProperty("fail") int failed,
      @JsonProperty("skip") int skipped,
      long revision,
      String errorCode) {}

  public record Provider(String userId, String displayName) {}

  public record Registration(
      String name, Instant executedAt, UUID outputAttachmentId, List<UUID> logAttachmentIds) {}

  public record Result(
      String resultId, String caseName, String suite, String robotStatus, String artifactId) {}

  public record Artifact(String artifactId, String name, String kind, String sha256) {}

  public record LogLine(int line, String text) {}

  public record LogProof(
      long run, long artifact, String name, String hash, int line, String text) {}

  public record Download(Resource resource, String name, long bytes) {}

  record ParseJob(
      long run,
      long project,
      long actor,
      String jobId,
      long revision,
      long authRevision,
      long artifact,
      String objectId,
      String sha256,
      long sizeBytes) {}

  private record Stored(
      long id, String attachmentId, String kind, String name, long bytes, String hash) {}
}
