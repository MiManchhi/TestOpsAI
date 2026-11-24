package io.testopsai.server.modules.knowledge;

import io.testopsai.server.platform.web.WorkspaceFailure;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/objects")
class WorkspaceObjectController {
  private final WorkspaceServiceIdentity service;
  private final WorkspaceAttachmentService attachments;
  private final JdbcTemplate jdbc;

  WorkspaceObjectController(
      WorkspaceServiceIdentity service, WorkspaceAttachmentService attachments, JdbcTemplate jdbc) {
    this.service = service;
    this.attachments = attachments;
    this.jdbc = jdbc;
  }

  @GetMapping("/{objectId}/content")
  ResponseEntity<Resource> readAuthorizedObject(
      @PathVariable UUID objectId,
      @RequestHeader(value = "Authorization", defaultValue = "") String authorization,
      @RequestHeader(value = "X-Delegation-Token", defaultValue = "") String delegation) {
    var grant = service.readDelegation(authorization, delegation);
    if (!(grant.get("objectIds") instanceof List<?> ids)
        || !ids.contains(objectId.toString())
        || !(grant.get("scope") instanceof Map<?, ?> scope)
        || !(scope.get("documentIds") instanceof List<?> versions)
        || !(scope.get("knowledgeBaseIds") instanceof List<?> bases))
      throw WorkspaceFailure.notFound();
    String project = grant.get("projectId", String.class);
    String actor = grant.get("actorId", String.class);
    Number revision = grant.get("authorizationVersion", Number.class);
    String job = grant.get("operationId", String.class);
    if (project == null
        || actor == null
        || revision == null
        || !bases.contains(project)
        || !project.equals(scope.get("projectId"))
        || !actor.equals(scope.get("actorId"))) {
      throw WorkspaceFailure.notFound();
    }
    if (versions.isEmpty() && scope.get("runIds") instanceof List<?> runs && !runs.isEmpty()) {
      if (runs.size() != 1
          || !Boolean.TRUE.equals(
              jdbc.queryForObject(
                  """
          SELECT EXISTS(SELECT 1 FROM workspace_run r JOIN projects p ON p.id=r.project_id
          JOIN project_members m ON m.project_id=r.project_id AND m.user_id=r.provided_by
          JOIN users u ON u.id=r.provided_by
          WHERE r.job_id=? AND r.project_id=? AND r.provided_by=? AND r.id=? AND p.auth_revision=?
          AND r.parse_status='PROCESSING' AND r.output_object_id=? AND m.role IN ('OWNER','TEST','DEV')
          AND u.enabled=TRUE AND u.must_change_password=FALSE
          AND r.started_at > UTC_TIMESTAMP(6)-INTERVAL 150 SECOND)
          """,
                  Boolean.class,
                  job,
                  project,
                  actor,
                  runs.getFirst(),
                  revision.longValue(),
                  objectId.toString()))) throw WorkspaceFailure.notFound();
      return objectResponse(objectId);
    }
    if (versions.isEmpty()) {
      if (!Boolean.TRUE.equals(
          jdbc.queryForObject(
              """
          SELECT EXISTS(SELECT 1 FROM workspace_chat_turn_file f
          JOIN workspace_attachment a ON a.id=f.attachment_id
          JOIN workspace_chat_turn t ON t.id=f.turn_id
          JOIN workspace_chat c ON c.id=t.conversation_id
          JOIN projects p ON p.id=c.project_id
          JOIN users u ON u.id=c.actor_id
          JOIN project_members m ON m.project_id=c.project_id AND m.user_id=c.actor_id
          WHERE f.attachment_id=? AND t.id=? AND c.project_id=? AND c.actor_id=?
          AND a.project_id=c.project_id AND a.uploaded_by=c.actor_id AND a.expires_at>UTC_TIMESTAMP(6)
          AND t.status='RUNNING' AND p.auth_revision=? AND t.authorization_revision=p.auth_revision
          AND m.role IN ('OWNER','TEST','DEV') AND u.enabled=TRUE AND u.must_change_password=FALSE)
          """,
              Boolean.class,
              objectId.toString(),
              job,
              project,
              actor,
              revision.longValue()))) throw WorkspaceFailure.notFound();
      return objectResponse(objectId);
    }
    var matches =
        jdbc.query(
            """
        SELECT v.id FROM workspace_index_job j JOIN workspace_doc_version v ON v.id=j.version_id
        JOIN workspace_document d ON d.id=v.document_id AND d.project_id=j.project_id
        JOIN projects p ON p.id=d.project_id
        WHERE j.id=? AND j.project_id=? AND j.actor_id=? AND p.auth_revision=?
        AND j.status='PROCESSING' AND j.lease_until>UTC_TIMESTAMP(6)
        AND v.review_status='APPROVED' AND v.index_status='PROCESSING' AND v.attachment_id=?
        """,
            (rs, i) -> rs.getString(1),
            job,
            WorkspaceDocumentService.positiveId(project),
            WorkspaceDocumentService.positiveId(actor),
            revision.longValue(),
            objectId.toString());
    if (matches.isEmpty() || !versions.contains(matches.getFirst()))
      throw WorkspaceFailure.notFound();
    return objectResponse(objectId);
  }

  private ResponseEntity<Resource> objectResponse(UUID objectId) {
    var entry = attachments.attachment(objectId);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .contentLength(entry.sizeBytes())
        .cacheControl(CacheControl.noStore())
        .body(attachments.object(objectId));
  }
}
