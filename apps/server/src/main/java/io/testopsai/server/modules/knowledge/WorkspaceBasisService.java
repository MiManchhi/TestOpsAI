package io.testopsai.server.modules.knowledge;

import io.testopsai.server.modules.identity.AppUser;
import io.testopsai.server.platform.idempotency.DurableCommandReceipt;
import io.testopsai.server.platform.web.PageResponse;
import io.testopsai.server.platform.web.WorkspaceFailure;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class WorkspaceBasisService {
  private final JdbcTemplate jdbc;
  private final WorkspaceDocumentAccess access;
  private final WorkspaceDocumentService documents;
  private final DurableCommandReceipt receipts;

  WorkspaceBasisService(
      JdbcTemplate jdbc,
      WorkspaceDocumentAccess access,
      WorkspaceDocumentService documents,
      DurableCommandReceipt receipts) {
    this.jdbc = jdbc;
    this.access = access;
    this.documents = documents;
    this.receipts = receipts;
  }

  PageResponse<WorkspaceDocument.Basis> list(AppUser actor, long project, int page, int size) {
    access.owner(actor, project);
    PageResponse.validatePagination(page, size);
    long total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM workspace_basis WHERE project_id=?", Long.class, project);
    var rows =
        jdbc.query(
            """
        SELECT id,label,revision,effective_from,effective_to FROM workspace_basis WHERE project_id=?
        ORDER BY revision DESC LIMIT ? OFFSET ?
        """,
            (rs, i) -> row(rs),
            project,
            size,
            (long) page * size);
    return new PageResponse<>(rows.stream().map(this::basis).toList(), total, page, size);
  }

  @Transactional
  public WorkspaceDocument.Basis publish(
      AppUser actor, long project, String command, WorkspaceDocument.BasisSet body) {
    access.lock(project);
    access.owner(actor, project);
    String label = WorkspaceDocumentService.text(body.label(), "label");
    List<String> ids = body.versionIds();
    if (ids == null
        || ids.isEmpty()
        || ids.size() > 1000
        || new HashSet<>(ids).size() != ids.size()
        || body.expectedRevision() == null
        || body.expectedRevision() < 0) {
      throw new IllegalArgumentException(
          "Provide distinct version identifiers and the expected revision");
    }
    return receipts.execute(
        actor.id(),
        "setBasis",
        project,
        command,
        body,
        WorkspaceDocument.Basis.class,
        () -> {
          Row prior =
              jdbc
                  .query(
                      """
          SELECT id,label,revision,effective_from,effective_to FROM workspace_basis WHERE active_project_id=?
          """,
                      (rs, i) -> row(rs),
                      project)
                  .stream()
                  .findFirst()
                  .orElse(null);
          long revision = prior == null ? 0 : prior.revision();
          if (revision != body.expectedRevision())
            throw WorkspaceFailure.conflict("VERSION_CONFLICT", "The current basis changed");
          var distinctDocuments = new HashSet<String>();
          var selected = new ArrayList<Long>();
          for (String id : ids) {
            long version = WorkspaceDocumentService.positiveId(id);
            var doc = documents.version(actor, project, version);
            if (!"READY".equals(doc.indexStatus()) || !distinctDocuments.add(doc.documentId())) {
              throw new IllegalArgumentException("Choose one approved, ready version per document");
            }
            selected.add(version);
          }
          Instant now = jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)", Timestamp.class).toInstant();
          if (prior != null && !now.isAfter(prior.from())) now = prior.from().plusNanos(1000);
          Timestamp effective = Timestamp.from(now);
          if (prior != null)
            jdbc.update(
                "UPDATE workspace_basis SET effective_to=? WHERE id=?", effective, prior.id());
          var key = new GeneratedKeyHolder();
          try {
            jdbc.update(
                connection -> {
                  var statement =
                      connection.prepareStatement(
                          """
              INSERT INTO workspace_basis (project_id,label,revision,published_by,effective_from)
              VALUES (?,?,?,?,?)
              """,
                          Statement.RETURN_GENERATED_KEYS);
                  statement.setLong(1, project);
                  statement.setString(2, label);
                  statement.setLong(3, revision + 1);
                  statement.setLong(4, actor.id());
                  statement.setTimestamp(5, effective);
                  return statement;
                },
                key);
          } catch (DuplicateKeyException ex) {
            throw WorkspaceFailure.conflict(
                "BASIS_LABEL_EXISTS", "The project already has this basis label");
          }
          long id = key.getKey().longValue();
          for (int ordinal = 0; ordinal < selected.size(); ordinal++) {
            jdbc.update(
                "INSERT INTO workspace_basis_version (basis_id,ordinal,version_id) VALUES (?,?,?)",
                id,
                ordinal,
                selected.get(ordinal));
          }
          access.advanceAuthorization(project);
          documents.audit(actor, "setBasis", id);
          return new WorkspaceDocument.Basis(
              Long.toString(id), label, List.copyOf(ids), now, null, revision + 1);
        });
  }

  private WorkspaceDocument.Basis basis(Row row) {
    var ids =
        jdbc.query(
            "SELECT version_id FROM workspace_basis_version WHERE basis_id=? ORDER BY ordinal",
            (rs, i) -> rs.getString(1),
            row.id());
    return new WorkspaceDocument.Basis(
        Long.toString(row.id()), row.label(), ids, row.from(), row.to(), row.revision());
  }

  private static Row row(ResultSet rs) throws SQLException {
    Timestamp end = rs.getTimestamp("effective_to");
    return new Row(
        rs.getLong("id"),
        rs.getString("label"),
        rs.getLong("revision"),
        rs.getTimestamp("effective_from").toInstant(),
        end == null ? null : end.toInstant());
  }

  private record Row(long id, String label, long revision, Instant from, Instant to) {}
}
