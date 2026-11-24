package io.testopsai.server.modules.knowledge;

import io.testopsai.server.platform.web.WorkspaceFailure;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceAnswerEvidence {
  private static final String ALLOWED =
      """
      FROM workspace_doc_version v JOIN workspace_document d ON d.id=v.document_id
      JOIN workspace_attachment a ON a.id=v.attachment_id
      WHERE d.project_id=? AND v.review_status='APPROVED' AND v.index_status='READY'
      AND (d.visibility='PROJECT' OR EXISTS (
        SELECT 1 FROM workspace_doc_reader r WHERE r.project_id=d.project_id
        AND r.document_id=d.id AND r.user_id=?
      ))
      """;

  private final JdbcTemplate jdbc;
  private final WorkspaceAttachmentService attachments;

  WorkspaceAnswerEvidence(JdbcTemplate jdbc, WorkspaceAttachmentService attachments) {
    this.jdbc = jdbc;
    this.attachments = attachments;
  }

  public List<Version> versions(long project, long actor, String previousBasis) {
    return jdbc.query(
        """
        SELECT v.id,d.project_id,a.sha256,v.version_label
        """
            + ALLOWED
            + """
        AND (v.id=(SELECT MAX(v2.id) FROM workspace_doc_version v2
          WHERE v2.document_id=d.id AND v2.review_status='APPROVED' AND v2.index_status='READY')
          OR EXISTS (SELECT 1 FROM workspace_basis_version bv JOIN workspace_basis b ON b.id=bv.basis_id
            WHERE bv.version_id=v.id AND b.project_id=d.project_id AND (b.effective_to IS NULL OR b.id=?)))
        ORDER BY v.id
        """,
        (rs, i) -> new Version(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)),
        project,
        actor,
        previousBasis);
  }

  public String basisIdAt(long project, Instant at) {
    String sql = "SELECT id FROM workspace_basis WHERE project_id=? ";
    var values =
        at == null
            ? jdbc.queryForList(sql + "AND effective_to IS NULL", String.class, project)
            : jdbc.queryForList(
                sql + "AND effective_from<=? AND (effective_to IS NULL OR effective_to>?)",
                String.class,
                project,
                java.sql.Timestamp.from(at),
                java.sql.Timestamp.from(at));
    return values.stream().findFirst().orElse(null);
  }

  public Basis basis(String id, List<String> allowed) {
    if (id == null) return null;
    String label =
        jdbc.queryForObject("SELECT label FROM workspace_basis WHERE id=?", String.class, id);
    var all =
        jdbc.queryForList(
            "SELECT version_id FROM workspace_basis_version WHERE basis_id=? ORDER BY ordinal",
            String.class,
            id);
    var visible = all.stream().filter(allowed::contains).toList();
    return visible.isEmpty()
        ? null
        : new Basis(id, label, visible, visible.size() == all.size() ? "COMPLETE" : "PARTIAL");
  }

  public List<DocumentProof> responseClauses(long project, long actor, List<String> versions) {
    var all = new java.util.ArrayList<DocumentProof>();
    for (var version : versions)
      all.addAll(
          jdbc.query(
              "SELECT d.title,v.id,v.version_label,b.locator,b.content "
                  + ALLOWED.replace(
                      "JOIN workspace_attachment a",
                      "JOIN workspace_document_block b ON b.version_id=v.id JOIN workspace_attachment a")
                  + " AND v.id=? AND CHAR_LENGTH(b.content)<=4000"
                  + " AND (b.content LIKE '%内响应%' OR b.content LIKE '%响应时间%' OR b.content LIKE '%response time%')"
                  + " ORDER BY b.ordinal",
              (rs, n) ->
                  new DocumentProof(
                      new EvidenceRef(
                          "DOCUMENT", rs.getString(2), rs.getString(3), rs.getString(4)),
                      rs.getString(5),
                      rs.getString(1),
                      rs.getString(2),
                      rs.getString(3),
                      rs.getString(4)),
              project,
              actor,
              version));
    return all;
  }

  public boolean allVisible(long project, long actor, com.fasterxml.jackson.databind.JsonNode ids) {
    if (ids == null || !ids.isArray()) return false;
    if (ids.isEmpty()) return true;
    long count =
        jdbc.queryForObject(
            "SELECT COUNT(*) " + ALLOWED + " AND JSON_CONTAINS(?,JSON_QUOTE(CAST(v.id AS CHAR)))",
            Long.class,
            project,
            actor,
            ids.toString());
    return count == ids.size();
  }

  public DocumentProof proof(long project, long actor, EvidenceRef ref) {
    if (ref == null
        || !"DOCUMENT".equals(ref.resourceKind())
        || ref.resourceId() == null
        || !ref.resourceId().matches("[1-9][0-9]{0,18}")
        || ref.locator() == null
        || !ref.locator().matches("(line|page|paragraph) [1-9][0-9]{0,5}( part [1-9][0-9]{0,5})?"))
      throw WorkspaceFailure.notFound();
    return jdbc
        .query(
            "SELECT d.title,v.id,v.version_label,b.locator,b.content "
                + (ALLOWED.replace(
                    "JOIN workspace_attachment a",
                    "JOIN workspace_document_block b ON b.version_id=v.id JOIN workspace_attachment a"))
                + " AND v.id=? AND v.version_label=? AND (b.locator=? OR b.locator=CONCAT(?, ' part 1'))"
                + " ORDER BY CHAR_LENGTH(b.locator) LIMIT 1",
            (rs, n) ->
                new DocumentProof(
                    ref,
                    rs.getString(5),
                    rs.getString(1),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getString(4)),
            project,
            actor,
            ref.resourceId(),
            ref.version(),
            ref.locator(),
            ref.locator())
        .stream()
        .findFirst()
        .orElseThrow(WorkspaceFailure::notFound);
  }

  public File claimFile(long project, long actor, UUID id) {
    if (id == null
        || !Boolean.TRUE.equals(
            jdbc.queryForObject(
                """
        SELECT EXISTS(SELECT 1 FROM workspace_attachment
        WHERE id=? AND project_id=? AND uploaded_by=? AND expires_at>UTC_TIMESTAMP(6))
        """,
                Boolean.class,
                id.toString(),
                project,
                actor))) throw WorkspaceFailure.notFound();
    var stored = attachments.attachment(id);
    if (!stored
        .fileName()
        .toLowerCase(java.util.Locale.ROOT)
        .matches(".+\\.(robot|resource|txt|log|xml|md|markdown)"))
      throw new IllegalArgumentException("Use a Robot or UTF-8 text attachment");
    if (stored.sizeBytes() > 262144L)
      throw new IllegalArgumentException("Chat attachment exceeds 256 KiB");
    return file(id);
  }

  public File file(UUID id) {
    var stored = attachments.attachment(id);
    return new File(
        stored.attachmentId(),
        stored.attachmentId(),
        stored.fileName(),
        stored.sizeBytes(),
        stored.sha256(),
        stored.expiresAt());
  }

  public record EvidenceRef(
      String resourceKind, String resourceId, String version, String locator) {}

  public record Version(
      String versionId, String knowledgeBaseId, String contentHash, String versionLabel) {}

  public record Basis(
      String basisId, String label, List<String> visibleVersionIds, String coverage) {}

  public record DocumentProof(
      EvidenceRef source,
      String excerpt,
      String title,
      String versionId,
      String label,
      String locator) {}

  public record File(
      String attachmentId,
      String objectId,
      String logicalPath,
      long sizeBytes,
      String sha256,
      Instant expiresAt) {}
}
