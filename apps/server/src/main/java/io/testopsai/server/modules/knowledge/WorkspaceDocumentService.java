package io.testopsai.server.modules.knowledge;

import io.testopsai.server.modules.identity.AppUser;
import io.testopsai.server.platform.error.RequestIdFilter;
import io.testopsai.server.platform.idempotency.DurableCommandReceipt;
import io.testopsai.server.platform.web.PageResponse;
import io.testopsai.server.platform.web.WorkspaceFailure;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class WorkspaceDocumentService {
  private static final Set<String> KINDS = Set.of("TENDER", "SPEC", "KEYWORDS");
  private static final Set<String> REVIEWS = Set.of("PENDING", "APPROVED", "RETURNED");
  private static final String ROW =
      """
      SELECT d.id AS document_id,v.id AS version_id,d.title,d.kind,v.version_label,
        v.review_status,v.index_status,d.visibility,v.submitted_by,
        COALESCE(u.display_name,u.username) AS submitted_name,v.revision,v.reason,v.created_at,v.error_code
      """;
  private static final String JOIN =
      """
      FROM workspace_doc_version v JOIN workspace_document d ON d.id=v.document_id
      JOIN users u ON u.id=v.submitted_by
      WHERE d.project_id=?
      """;
  private static final String FILTER =
      """
      AND (
        (v.review_status IN ('PENDING','RETURNED') AND (v.submitted_by=? OR ?))
        OR (v.review_status='APPROVED' AND (d.visibility='PROJECT' OR EXISTS (
          SELECT 1 FROM workspace_doc_reader acl
          WHERE acl.project_id=d.project_id AND acl.document_id=d.id AND acl.user_id=?
        )))
      )
      """;
  private final JdbcTemplate jdbc;
  private final WorkspaceDocumentAccess access;
  private final WorkspaceAttachmentService attachments;
  private final DurableCommandReceipt receipts;

  WorkspaceDocumentService(
      JdbcTemplate jdbc,
      WorkspaceDocumentAccess access,
      WorkspaceAttachmentService attachments,
      DurableCommandReceipt receipts) {
    this.jdbc = jdbc;
    this.access = access;
    this.attachments = attachments;
    this.receipts = receipts;
  }

  PageResponse<WorkspaceDocument> findDocuments(
      AppUser actor, long project, int page, int size, String query, String review) {
    boolean owner = access.member(actor, project);
    PageResponse.validatePagination(page, size);
    if (review != null && !REVIEWS.contains(review))
      throw new IllegalArgumentException("Invalid review status");
    String term = query == null ? "" : query.strip();
    var args = new ArrayList<Object>(List.of(project, actor.id(), owner, actor.id(), term));
    String where = JOIN + FILTER + " AND LOCATE(?,d.title)>0";
    if (review != null) {
      where += " AND v.review_status=?";
      args.add(review);
    }
    long total =
        jdbc.queryForObject("SELECT COUNT(DISTINCT d.id) " + where, Long.class, args.toArray());
    args.add(size);
    args.add((long) page * size);
    String ranked =
        ROW + ", ROW_NUMBER() OVER (PARTITION BY d.id ORDER BY v.id DESC) AS rank_number " + where;
    var items =
        jdbc.query(
            "SELECT * FROM ("
                + ranked
                + ") visible WHERE rank_number=1"
                + " ORDER BY version_id DESC LIMIT ? OFFSET ?",
            (rs, n) -> map(rs),
            args.toArray());
    return new PageResponse<>(items, total, page, size);
  }

  PageResponse<WorkspaceDocument> listVersions(
      AppUser actor, long project, long doc, int page, int size) {
    boolean owner = access.member(actor, project);
    PageResponse.validatePagination(page, size);
    String where = JOIN + FILTER + " AND d.id=?";
    long total =
        jdbc.queryForObject(
            "SELECT COUNT(*) " + where, Long.class, project, actor.id(), owner, actor.id(), doc);
    if (total == 0) throw WorkspaceFailure.notFound();
    var items =
        jdbc.query(
            ROW + where + " ORDER BY v.id DESC LIMIT ? OFFSET ?",
            (rs, n) -> map(rs),
            project,
            actor.id(),
            owner,
            actor.id(),
            doc,
            size,
            (long) page * size);
    return new PageResponse<>(items, total, page, size);
  }

  WorkspaceDocument version(AppUser actor, long project, long version) {
    boolean owner = access.member(actor, project);
    return jdbc
        .query(
            ROW + JOIN + FILTER + " AND v.id=?",
            (rs, n) -> map(rs),
            project,
            actor.id(),
            owner,
            actor.id(),
            version)
        .stream()
        .findFirst()
        .orElseThrow(WorkspaceFailure::notFound);
  }

  WorkspaceAttachmentService.StoredAttachment original(AppUser actor, long project, long version) {
    version(actor, project, version);
    String key =
        jdbc.queryForObject(
            "SELECT attachment_id FROM workspace_doc_version WHERE id=?", String.class, version);
    return attachments.attachment(UUID.fromString(key));
  }

  WorkspaceDocument.Content read(
      AppUser actor, long project, long id, String cursor, String locator) {
    var doc = version(actor, project, id);
    if (!"READY".equals(doc.indexStatus())) throw WorkspaceFailure.notFound();
    if (cursor != null && locator != null)
      throw new IllegalArgumentException("Provide a cursor or locator, not both");
    int offset = 0;
    if (cursor != null) {
      if (!cursor.matches(id + "\\.[0-9]{1,6}"))
        throw new IllegalArgumentException("Invalid content cursor");
      offset = Integer.parseInt(cursor.substring(cursor.indexOf('.') + 1));
    }
    if (locator != null) {
      offset =
          jdbc
              .query(
                  "SELECT ordinal FROM workspace_document_block WHERE version_id=? AND locator=?",
                  (rs, n) -> rs.getInt(1),
                  id,
                  locator)
              .stream()
              .findFirst()
              .orElseThrow(WorkspaceFailure::notFound);
    }
    int limit = 50;
    List<WorkspaceDocument.Block> blocks =
        jdbc.query(
            """
        SELECT locator,content FROM workspace_document_block WHERE version_id=? AND ordinal>=?
        ORDER BY ordinal LIMIT ?
        """,
            (rs, n) -> new WorkspaceDocument.Block(rs.getString(1), rs.getString(2)),
            id,
            offset,
            limit + 1);
    return new WorkspaceDocument.Content(
        Long.toString(id),
        blocks.size() > limit ? blocks.subList(0, limit) : blocks,
        blocks.size() > limit ? id + "." + (offset + limit) : null);
  }

  @Transactional
  public WorkspaceDocument submit(
      AppUser actor, long project, String command, WorkspaceDocument.Submission body) {
    access.lock(project);
    access.member(actor, project);
    String title = text(body.title(), "title");
    String label = text(body.versionLabel(), "versionLabel");
    String kind = body.kind();
    if (kind == null || !KINDS.contains(kind) || body.changeNote() == null) {
      throw new IllegalArgumentException("kind and changeNote are required");
    }
    UUID attachment = parseUuid(body.attachmentId());
    Long documentId = body.documentId() == null ? null : positiveId(body.documentId());
    if (documentId != null) requireCanSubmit(actor, project, documentId);
    return receipts.execute(
        actor.id(),
        "submitVersion",
        project,
        command,
        body,
        WorkspaceDocument.class,
        () -> {
          attachments.claimDocument(actor, project, attachment);
          long doc;
          if (documentId == null) {
            doc =
                insert(
                    """
            INSERT INTO workspace_document (project_id,title,kind) VALUES (?,?,?)
            """,
                    project,
                    title,
                    kind);
          } else {
            String key =
                jdbc.queryForObject(
                    "SELECT kind FROM workspace_document WHERE id=?", String.class, documentId);
            if (!kind.equals(key))
              throw new IllegalArgumentException("A version keeps its document type");
            doc = documentId;
          }
          try {
            long id =
                insert(
                    """
            INSERT INTO workspace_doc_version (document_id,attachment_id,version_label,submitted_by,change_note,review_job_id,reason)
            VALUES (?,?,?,?,?,UUID(),'')
            """,
                    doc,
                    attachment.toString(),
                    label,
                    actor.id(),
                    body.changeNote());
            audit(actor, "submitVersion", id);
            return version(actor, project, id);
          } catch (DuplicateKeyException ex) {
            throw WorkspaceFailure.conflict(
                "DOCUMENT_VERSION_EXISTS", "The version label or attachment is already used");
          }
        });
  }

  @Transactional
  public WorkspaceDocument review(
      AppUser actor, long project, long id, String command, WorkspaceDocument.Review body) {
    access.lock(project);
    access.owner(actor, project);
    boolean approves = "APPROVE".equals(body.decision());
    if ((!approves && !"RETURN".equals(body.decision()))
        || body.note() == null
        || (!approves && body.note().isBlank())) {
      throw new IllegalArgumentException("A valid decision and a reason for returns are required");
    }
    version(actor, project, id);
    return receipts.execute(
        actor.id(),
        "reviewVersion",
        id,
        command,
        body,
        WorkspaceDocument.class,
        () -> {
          var current = version(actor, project, id);
          WorkspaceFailure.checkRevision(body.revision(), current.revision());
          if (!"PENDING".equals(current.reviewStatus())) {
            throw WorkspaceFailure.conflict("VERSION_CONFLICT", "The version was already reviewed");
          }
          jdbc.update(
              """
          UPDATE workspace_doc_version SET review_status=?, index_status=?, reviewed_by=?, reviewed_at=UTC_TIMESTAMP(6), reason=?, revision=revision+1
          WHERE id=?
          """,
              approves ? "APPROVED" : "RETURNED",
              approves ? "QUEUED" : "NOT_STARTED",
              actor.id(),
              body.note(),
              id);
          if (approves) {
            enqueue(project, id, actor.id());
            access.advanceAuthorization(project);
          }
          audit(actor, "reviewVersion", id);
          return version(actor, project, id);
        });
  }

  @Transactional
  public WorkspaceDocument.Receipt retry(AppUser actor, long project, long id, String command) {
    access.lock(project);
    access.owner(actor, project);
    version(actor, project, id);
    return receipts.execute(
        actor.id(),
        "retryIndex",
        id,
        command,
        List.of(project, id),
        WorkspaceDocument.Receipt.class,
        () -> {
          var current = version(actor, project, id);
          if (!"APPROVED".equals(current.reviewStatus())
              || !"FAILED".equals(current.indexStatus())) {
            throw WorkspaceFailure.conflict(
                "VERSION_CONFLICT", "Only approved failed versions can be retried");
          }
          jdbc.update(
              "UPDATE workspace_doc_version SET index_status='QUEUED',error_code=NULL WHERE id=?",
              id);
          String job = enqueue(project, id, actor.id());
          audit(actor, "retryIndex", id);
          return new WorkspaceDocument.Receipt(job, "ACCEPTED");
        });
  }

  WorkspaceDocument.Readers readers(AppUser actor, long project, long doc) {
    access.owner(actor, project);
    return loadReaders(project, doc);
  }

  @Transactional
  public WorkspaceDocument.Readers setReaders(
      AppUser actor, long project, long doc, String command, WorkspaceDocument.Readers body) {
    access.lock(project);
    access.owner(actor, project);
    loadReaders(project, doc);
    List<String> ids = body.userIds();
    boolean restricted = "RESTRICTED".equals(body.visibility());
    if (ids == null
        || (restricted ? ids.isEmpty() : (!"PROJECT".equals(body.visibility()) || !ids.isEmpty()))
        || new java.util.HashSet<>(ids).size() != ids.size()) {
      throw new IllegalArgumentException(
          "Provide distinct project readers for restricted documents");
    }
    return receipts.execute(
        actor.id(),
        "setReaders",
        doc,
        command,
        body,
        WorkspaceDocument.Readers.class,
        () -> {
          var current = loadReaders(project, doc);
          WorkspaceFailure.checkRevision(body.revision(), current.revision());
          jdbc.update(
              "DELETE FROM workspace_doc_reader WHERE project_id=? AND document_id=?",
              project,
              doc);
          if (restricted) {
            for (String value : ids) {
              long reader = positiveId(value);
              if (reader == actor.id()) continue;
              if (jdbc.update(
                      """
              INSERT INTO workspace_doc_reader (project_id,document_id,user_id,grant_kind)
              SELECT project_id,?,user_id,'EXPLICIT' FROM project_members
              WHERE project_id=? AND user_id=? AND role IN ('TEST','DEV')
              """,
                      doc,
                      project,
                      reader)
                  != 1) {
                throw new IllegalArgumentException("Each reader must be a project member");
              }
            }
            jdbc.update(
                """
            INSERT INTO workspace_doc_reader (project_id,document_id,user_id,grant_kind)
            VALUES (?,?,?,'REVIEWER')
            """,
                project,
                doc,
                actor.id());
          }
          jdbc.update(
              "UPDATE workspace_document SET visibility=?,revision=revision+1 WHERE id=?",
              body.visibility(),
              doc);
          access.advanceAuthorization(project);
          audit(actor, "setReaders", doc);
          return loadReaders(project, doc);
        });
  }

  private WorkspaceDocument.Readers loadReaders(long project, long doc) {
    return jdbc
        .query(
            """
        SELECT visibility,revision FROM workspace_document WHERE project_id=? AND id=?
        """,
            (rs, n) -> {
              String visibility = rs.getString(1);
              long revision = rs.getLong(2);
              var ids =
                  jdbc.query(
                      """
              SELECT DISTINCT user_id FROM workspace_doc_reader WHERE project_id=? AND document_id=? ORDER BY user_id
              """,
                      (row, i) -> row.getString(1),
                      project,
                      doc);
              return new WorkspaceDocument.Readers(visibility, ids, revision);
            },
            project,
            doc)
        .stream()
        .findFirst()
        .orElseThrow(WorkspaceFailure::notFound);
  }

  private void requireCanSubmit(AppUser actor, long project, long doc) {
    if (jdbc.query(
            ROW + JOIN + FILTER + " AND d.id=? LIMIT 1",
            (rs, n) -> map(rs),
            project,
            actor.id(),
            access.member(actor, project),
            actor.id(),
            doc)
        .isEmpty()) {
      throw WorkspaceFailure.notFound();
    }
  }

  private String enqueue(long project, long version, long actor) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO workspace_index_job (id,project_id,version_id,actor_id) VALUES (?,?,?,?)",
        id,
        project,
        version,
        actor);
    return id;
  }

  private long insert(String sql, Object... args) {
    var holder = new GeneratedKeyHolder();
    jdbc.update(
        connection -> {
          var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
          return statement;
        },
        holder);
    return holder.getKey().longValue();
  }

  static WorkspaceDocument map(ResultSet rs) throws SQLException {
    return new WorkspaceDocument(
        rs.getString("document_id"),
        rs.getString("version_id"),
        rs.getString("title"),
        rs.getString("kind"),
        rs.getString("version_label"),
        rs.getString("review_status"),
        rs.getString("index_status"),
        rs.getString("visibility"),
        new WorkspaceDocument.Submitter(
            rs.getString("submitted_by"), rs.getString("submitted_name")),
        rs.getLong("revision"),
        rs.getString("reason"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getString("error_code"));
  }

  static String text(String value, String field) {
    if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > 160) {
      throw new IllegalArgumentException(field + " is required and must fit 160 characters");
    }
    return value.strip();
  }

  static long positiveId(String value) {
    if (value == null || !value.matches("[1-9][0-9]{0,18}"))
      throw new IllegalArgumentException("Invalid resource identifier");
    return Long.parseLong(value);
  }

  static UUID parseUuid(String value) {
    if (value == null) throw new IllegalArgumentException("A UUID is required");
    UUID result = UUID.fromString(value);
    if (!result.toString().equalsIgnoreCase(value))
      throw new IllegalArgumentException("Invalid UUID");
    return result;
  }

  void audit(AppUser actor, String action, long id) {
    jdbc.update(
        """
        INSERT INTO workspace_audit_event (actor_id,operation,target_id,request_id) VALUES (?,?,?,?)
        """,
        actor.id(),
        action,
        id,
        MDC.get(RequestIdFilter.MDC_KEY));
  }
}
