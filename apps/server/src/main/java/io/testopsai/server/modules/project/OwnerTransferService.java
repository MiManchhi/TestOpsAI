package io.testopsai.server.modules.project;

import io.testopsai.server.modules.identity.AppUser;
import io.testopsai.server.platform.idempotency.DurableCommandReceipt;
import io.testopsai.server.platform.web.PageResponse;
import io.testopsai.server.platform.web.WorkspaceFailure;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OwnerTransferService {
  private static final String SELECT =
      """
      SELECT BIN_TO_UUID(handover_id) AS id, project_id, old_owner_id, new_owner_id,
        former_role, mode, status, requested_by, decided_by, reason, revision, created_at
      FROM workspace_owner_transfer WHERE project_id=?
      """;
  private final JdbcTemplate jdbc;
  private final WorkspaceProjectService projects;
  private final DurableCommandReceipt receipts;

  OwnerTransferService(
      JdbcTemplate jdbc, WorkspaceProjectService projects, DurableCommandReceipt receipts) {
    this.jdbc = jdbc;
    this.projects = projects;
    this.receipts = receipts;
  }

  PageResponse<Handover> history(
      AppUser actor, long project, boolean platform, int page, int size) {
    if (platform) projects.read(actor, project, true);
    else projects.requireOwner(actor, project);
    PageResponse.validatePagination(page, size);
    long total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM workspace_owner_transfer WHERE project_id=?",
            Long.class,
            project);
    var items =
        jdbc.query(
            SELECT + " ORDER BY created_at DESC,handover_id LIMIT ? OFFSET ?",
            (rs, n) -> map(rs),
            project,
            size,
            (long) page * size);
    return new PageResponse<>(items, total, page, size);
  }

  @Transactional
  public Handover request(AppUser actor, long projectId, String key, TransferRequest body) {
    projects.lockProject(projectId);
    projects.requireOwner(actor, projectId);
    return receipts.execute(
        actor.id(),
        "requestOwnerTransfer",
        projectId,
        key,
        body,
        Handover.class,
        () -> {
          var project = projects.read(actor, projectId, false);
          WorkspaceFailure.checkRevision(body.revision(), project.revision());
          long next = successor(projectId, body.newOwnerId(), body.formerRole());
          ensureNoPending(projectId);
          return insert(actor, projectId, actor.id(), next, body.formerRole(), false, "");
        });
  }

  @Transactional
  public Handover decide(
      AppUser actor, long projectId, UUID handoverId, String key, Decision body) {
    WorkspaceProjectService.requireAdmin(actor);
    projects.lockProject(projectId);
    boolean approve = "APPROVE".equals(body.decision());
    if (!approve && !"RETURN".equals(body.decision())
        || body.note() == null
        || (!approve && body.note().isBlank())) {
      throw new IllegalArgumentException("A valid decision and return reason are required");
    }
    return receipts.execute(
        actor.id(),
        "decideOwnerTransfer",
        projectId,
        key,
        new DecisionCommand(handoverId, body),
        Handover.class,
        () -> {
          var pending = get(projectId, handoverId);
          WorkspaceFailure.checkRevision(body.revision(), pending.revision());
          if (!"PENDING".equals(pending.status()))
            throw WorkspaceFailure.conflict("VERSION_CONFLICT", "Already decided");
          if (approve) {
            var current = projects.read(actor, projectId, true);
            if (!current.owner().userId().equals(pending.oldOwnerId())) {
              throw WorkspaceFailure.conflict("VERSION_CONFLICT", "The project owner changed");
            }
            long next = successor(projectId, pending.newOwnerId(), pending.formerRole());
            replace(projectId, Long.parseLong(pending.oldOwnerId()), next, pending.formerRole());
          }
          jdbc.update(
              """
          UPDATE workspace_owner_transfer SET status=?, decided_by=?, reason=?, revision=revision+1
          WHERE project_id=? AND handover_id=UUID_TO_BIN(?)
          """,
              approve ? "APPROVED" : "RETURNED",
              actor.id(),
              body.note(),
              projectId,
              handoverId.toString());
          projects.audit(actor, "decideOwnerTransfer", projectId);
          return get(projectId, handoverId);
        });
  }

  @Transactional
  public Handover proxy(AppUser actor, long projectId, String key, ProxyRequest body) {
    WorkspaceProjectService.requireAdmin(actor);
    projects.lockProject(projectId);
    if (body.unavailableReason() == null || body.unavailableReason().isBlank()) {
      throw new IllegalArgumentException("The owner's unavailability reason is required");
    }
    return receipts.execute(
        actor.id(),
        "proxyOwnerTransfer",
        projectId,
        key,
        body,
        Handover.class,
        () -> {
          var project = projects.read(actor, projectId, true);
          WorkspaceFailure.checkRevision(body.revision(), project.revision());
          ensureNoPending(projectId);
          long next = successor(projectId, body.newOwnerId(), body.formerRole());
          long old = Long.parseLong(project.owner().userId());
          replace(projectId, old, next, body.formerRole());
          return insert(
              actor, projectId, old, next, body.formerRole(), true, body.unavailableReason());
        });
  }

  private long successor(long project, String id, String role) {
    WorkspaceProjectService.validateMemberRole(role);
    long user = WorkspaceProjectService.publicId(id);
    projects.lockEnabledUser(user);
    var membership = projects.findMember(project, user);
    if (membership == null
        || !("TEST".equals(membership.role()) || "DEV".equals(membership.role()))) {
      throw new IllegalArgumentException("Select an active TEST or DEV member of the project");
    }
    return user;
  }

  private void replace(long project, long old, long next, String formerRole) {
    jdbc.update(
        """
        UPDATE project_members SET role=?, revision=revision+1 WHERE project_id=? AND user_id=?
        """,
        formerRole,
        project,
        old);
    jdbc.update(
        """
        UPDATE project_members SET role='OWNER', revision=revision+1 WHERE project_id=? AND user_id=?
        """,
        project,
        next);
    jdbc.update(
        "DELETE FROM workspace_doc_reader WHERE project_id=? AND grant_kind='REVIEWER'", project);
    jdbc.update(
        """
        INSERT INTO workspace_doc_reader (project_id,document_id,user_id,grant_kind)
        SELECT project_id,id,?,'REVIEWER' FROM workspace_document WHERE project_id=? AND visibility='RESTRICTED'
        """,
        next,
        project);
    jdbc.update(
        "UPDATE projects SET revision=revision+1, auth_revision=auth_revision+1 WHERE id=?",
        project);
  }

  private void ensureNoPending(long project) {
    if (jdbc.queryForObject(
            """
        SELECT COUNT(*) FROM workspace_owner_transfer WHERE pending_project_id=?
        """,
            Long.class,
            project)
        != 0) {
      throw WorkspaceFailure.conflict(
          "OWNER_TRANSFER_PENDING", "The project has a pending handover");
    }
  }

  private Handover insert(
      AppUser actor,
      long project,
      long old,
      long next,
      String former,
      boolean proxy,
      String reason) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO workspace_owner_transfer
          (handover_id,project_id,old_owner_id,new_owner_id,former_role,mode,status,requested_by,decided_by,reason)
        VALUES (UUID_TO_BIN(?),?,?,?,?,?,?,?,?,?)
        """,
        id.toString(),
        project,
        old,
        next,
        former,
        proxy ? "ADMIN_PROXY" : "OWNER_REQUEST",
        proxy ? "APPROVED" : "PENDING",
        actor.id(),
        proxy ? actor.id() : null,
        reason);
    projects.audit(actor, proxy ? "proxyOwnerTransfer" : "requestOwnerTransfer", project);
    return get(project, id);
  }

  private Handover get(long project, UUID id) {
    return jdbc
        .query(
            SELECT + " AND handover_id=UUID_TO_BIN(?)", (rs, n) -> map(rs), project, id.toString())
        .stream()
        .findFirst()
        .orElseThrow(WorkspaceFailure::notFound);
  }

  private static Handover map(ResultSet rs) throws SQLException {
    return new Handover(
        rs.getString("id"),
        rs.getString("project_id"),
        rs.getString("old_owner_id"),
        rs.getString("new_owner_id"),
        rs.getString("former_role"),
        rs.getString("mode"),
        rs.getString("status"),
        rs.getString("requested_by"),
        rs.getString("decided_by"),
        rs.getString("reason"),
        rs.getLong("revision"),
        rs.getTimestamp("created_at").toInstant());
  }

  record TransferRequest(String newOwnerId, String formerRole, Long revision) {}

  record ProxyRequest(
      String newOwnerId, String formerRole, Long revision, String unavailableReason) {}

  record Decision(String decision, String note, Long revision) {}

  private record DecisionCommand(UUID handoverId, Decision body) {}

  record Handover(
      String handoverId,
      String projectId,
      String oldOwnerId,
      String newOwnerId,
      String formerRole,
      String mode,
      String status,
      String requestedBy,
      String decidedBy,
      String reason,
      long revision,
      Instant createdAt) {}
}
