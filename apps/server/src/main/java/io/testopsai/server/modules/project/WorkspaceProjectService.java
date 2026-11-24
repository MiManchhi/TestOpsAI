package io.testopsai.server.modules.project;

import io.testopsai.server.modules.identity.AppUser;
import io.testopsai.server.platform.error.RequestIdFilter;
import io.testopsai.server.platform.idempotency.DurableCommandReceipt;
import io.testopsai.server.platform.web.PageResponse;
import io.testopsai.server.platform.web.WorkspaceFailure;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class WorkspaceProjectService {
  private static final String PROJECTS =
      """
      FROM projects p
      JOIN project_members owner ON owner.owner_project_id=p.id
      JOIN users u ON u.id=owner.user_id
      LEFT JOIN project_members m ON m.project_id=p.id AND m.user_id=?
        AND m.role IN ('OWNER','TEST','DEV')
      """;
  private static final String PROJECT_COLUMNS =
      """
      SELECT p.id, p.name, COALESCE(p.description,'') AS description, p.revision,
        u.id AS owner_id, COALESCE(u.display_name,u.username) AS owner_name, m.role AS my_role
      """;
  private final JdbcTemplate jdbc;
  private final DurableCommandReceipt receipts;

  WorkspaceProjectService(JdbcTemplate jdbc, DurableCommandReceipt receipts) {
    this.jdbc = jdbc;
    this.receipts = receipts;
  }

  PageResponse<WorkspaceProject> projects(AppUser actor, boolean platform, int page, int size) {
    if (platform) requireAdmin(actor);
    PageResponse.validatePagination(page, size);
    String from = PROJECTS + (platform ? "" : " WHERE m.user_id IS NOT NULL");
    long total = jdbc.queryForObject("SELECT COUNT(*) " + from, Long.class, actor.id());
    var items =
        jdbc.query(
            PROJECT_COLUMNS + from + " ORDER BY p.id LIMIT ? OFFSET ?",
            (rs, n) -> project(rs),
            actor.id(),
            size,
            (long) page * size);
    return new PageResponse<>(items, total, page, size);
  }

  WorkspaceProject read(AppUser actor, long id, boolean platform) {
    if (platform) requireAdmin(actor);
    return jdbc
        .query(
            PROJECT_COLUMNS
                + PROJECTS
                + " WHERE p.id=?"
                + (platform ? "" : " AND m.user_id IS NOT NULL"),
            (rs, n) -> project(rs),
            actor.id(),
            id)
        .stream()
        .findFirst()
        .orElseThrow(WorkspaceFailure::notFound);
  }

  @Transactional
  public WorkspaceProject create(AppUser actor, String key, ProjectCreate body) {
    requireAdmin(actor);
    validateProject(body.name(), body.description());
    long owner = publicId(body.ownerId());
    return receipts.execute(
        actor.id(),
        "platformCreate",
        0,
        key,
        body,
        WorkspaceProject.class,
        () -> {
          lockEnabledUser(owner);
          var holder = new GeneratedKeyHolder();
          jdbc.update(
              connection -> {
                var stmt =
                    connection.prepareStatement(
                        "INSERT INTO projects (project_key,name,description) VALUES (?,?,?)",
                        Statement.RETURN_GENERATED_KEYS);
                stmt.setString(
                    1, "P-" + UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT));
                stmt.setString(2, body.name().strip());
                stmt.setString(3, body.description());
                return stmt;
              },
              holder);
          long id = holder.getKey().longValue();
          jdbc.update(
              "INSERT INTO project_members (project_id,user_id,role) VALUES (?,?,'OWNER')",
              id,
              owner);
          audit(actor, "platformCreate", id);
          return read(actor, id, true);
        });
  }

  @Transactional
  public WorkspaceProject edit(AppUser actor, long projectId, String key, ProjectEdit body) {
    lockProject(projectId);
    requireOwner(actor, projectId);
    validateProject(body.name(), body.description());
    return receipts.execute(
        actor.id(),
        "editProject",
        projectId,
        key,
        body,
        WorkspaceProject.class,
        () -> {
          long revision =
              jdbc.queryForObject(
                  "SELECT revision FROM projects WHERE id=?", Long.class, projectId);
          WorkspaceFailure.checkRevision(body.revision(), revision);
          jdbc.update(
              "UPDATE projects SET name=?, description=?, revision=revision+1 WHERE id=?",
              body.name().strip(),
              body.description(),
              projectId);
          audit(actor, "editProject", projectId);
          return read(actor, projectId, false);
        });
  }

  PageResponse<WorkspaceProject.Person> candidates(
      AppUser actor, Long projectId, boolean successors, int page, int size, String query) {
    boolean platform = projectId == null || successors;
    if (platform) requireAdmin(actor);
    if (projectId != null) {
      if (successors) read(actor, projectId, true);
      else requireOwner(actor, projectId);
    }
    PageResponse.validatePagination(page, size);
    String q = query == null ? "" : query.strip();
    String from =
        " FROM users u WHERE u.enabled"
            + " AND (LOCATE(?,u.username)>0 OR LOCATE(?,COALESCE(u.display_name,u.username))>0)";
    var args = new java.util.ArrayList<Object>(java.util.List.of(q, q));
    if (projectId != null) {
      from +=
          (successors ? " AND EXISTS" : " AND NOT EXISTS")
              + """
          (SELECT 1 FROM project_members m WHERE m.project_id=? AND m.user_id=u.id
            AND m.role IN ('OWNER','TEST','DEV')
          """;
      from += successors ? " AND m.role<>'OWNER')" : ")";
      args.add(projectId);
    }
    long total = jdbc.queryForObject("SELECT COUNT(*)" + from, Long.class, args.toArray());
    args.add(size);
    args.add((long) page * size);
    var people =
        jdbc.query(
            "SELECT u.id, COALESCE(u.display_name,u.username) AS display_name"
                + from
                + " ORDER BY u.id LIMIT ? OFFSET ?",
            (rs, n) -> person(rs),
            args.toArray());
    return new PageResponse<>(people, total, page, size);
  }

  PageResponse<WorkspaceProject.Member> members(AppUser actor, long projectId, int page, int size) {
    requireOwner(actor, projectId);
    PageResponse.validatePagination(page, size);
    String from =
        """
        FROM project_members m JOIN users u ON u.id=m.user_id
        WHERE m.project_id=? AND m.role IN ('OWNER','TEST','DEV')
        """;
    long total = jdbc.queryForObject("SELECT COUNT(*) " + from, Long.class, projectId);
    var items =
        jdbc.query(
            "SELECT u.id, COALESCE(u.display_name,u.username) AS display_name, m.role, m.revision "
                + from
                + " ORDER BY u.id LIMIT ? OFFSET ?",
            (rs, n) -> member(rs),
            projectId,
            size,
            (long) page * size);
    return new PageResponse<>(items, total, page, size);
  }

  @Transactional
  public WorkspaceProject.Member saveMember(
      AppUser actor, long projectId, long userId, String key, MemberWrite body) {
    lockProject(projectId);
    requireOwner(actor, projectId);
    validateMemberRole(body.role());
    if (body.revision() == null || body.revision() < 0)
      throw new IllegalArgumentException("revision is required");
    return receipts.execute(
        actor.id(),
        "saveMember",
        projectId,
        key,
        new MemberCommand(userId, body.revision(), body.role()),
        WorkspaceProject.Member.class,
        () -> {
          lockEnabledUser(userId);
          var existing = findMember(projectId, userId);
          if (existing != null && "OWNER".equals(existing.role()))
            throw new LastOwnerException(projectId);
          long revision =
              existing == null || !isWorkspaceRole(existing.role()) ? 0 : existing.revision();
          if (revision != body.revision())
            throw WorkspaceFailure.conflict("VERSION_CONFLICT", "The member has changed");
          jdbc.update(
              """
          INSERT INTO project_members (project_id,user_id,role) VALUES (?,?,?)
          ON DUPLICATE KEY UPDATE role=?, revision=revision+1
          """,
              projectId,
              userId,
              body.role(),
              body.role());
          bumpAuthorization(projectId);
          audit(actor, "saveMember", userId);
          return findMember(projectId, userId);
        });
  }

  @Transactional
  public void removeMember(AppUser actor, long projectId, long userId, long revision, String key) {
    lockProject(projectId);
    requireOwner(actor, projectId);
    receipts.execute(
        actor.id(),
        "removeMember",
        projectId,
        key,
        new MemberCommand(userId, revision, null),
        Void.class,
        () -> {
          var target = findMember(projectId, userId);
          if (target == null || !isWorkspaceRole(target.role())) throw WorkspaceFailure.notFound();
          if ("OWNER".equals(target.role())) throw new LastOwnerException(projectId);
          WorkspaceFailure.checkRevision(revision, target.revision());
          jdbc.update(
              "DELETE FROM project_members WHERE project_id=? AND user_id=?", projectId, userId);
          bumpAuthorization(projectId);
          audit(actor, "removeMember", userId);
          return null;
        });
  }

  void lockProject(long id) {
    if (jdbc.query("SELECT id FROM projects WHERE id=? FOR UPDATE", (rs, n) -> rs.getLong(1), id)
        .isEmpty()) {
      throw WorkspaceFailure.notFound();
    }
  }

  void requireOwner(AppUser actor, long projectId) {
    var project = read(actor, projectId, false);
    if (!"OWNER".equals(project.myRole()))
      throw new AccessDeniedException("Project owner required");
  }

  void lockEnabledUser(long id) {
    var enabled =
        jdbc.query(
            "SELECT enabled FROM users WHERE id=? FOR UPDATE", (rs, n) -> rs.getBoolean(1), id);
    if (enabled.isEmpty() || !enabled.getFirst()) throw WorkspaceFailure.notFound();
  }

  void bumpAuthorization(long id) {
    jdbc.update("UPDATE projects SET auth_revision=auth_revision+1 WHERE id=?", id);
  }

  WorkspaceProject.Member findMember(long projectId, long userId) {
    return jdbc
        .query(
            """
        SELECT u.id, COALESCE(u.display_name,u.username) AS display_name, m.role, m.revision
        FROM project_members m JOIN users u ON u.id=m.user_id
        WHERE m.project_id=? AND m.user_id=?
        """,
            (rs, n) -> member(rs),
            projectId,
            userId)
        .stream()
        .findFirst()
        .orElse(null);
  }

  void audit(AppUser actor, String operation, long id) {
    jdbc.update(
        """
        INSERT INTO workspace_audit_event (actor_id,operation,target_id,request_id)
        VALUES (?,?,?,?)
        """,
        actor.id(),
        operation,
        id,
        MDC.get(RequestIdFilter.MDC_KEY));
  }

  private static WorkspaceProject project(ResultSet rs) throws SQLException {
    return new WorkspaceProject(
        rs.getString("id"),
        rs.getString("name"),
        rs.getString("description"),
        new WorkspaceProject.Person(rs.getString("owner_id"), rs.getString("owner_name")),
        rs.getLong("revision"),
        rs.getString("my_role"));
  }

  private static WorkspaceProject.Person person(ResultSet rs) throws SQLException {
    return new WorkspaceProject.Person(rs.getString("id"), rs.getString("display_name"));
  }

  private static WorkspaceProject.Member member(ResultSet rs) throws SQLException {
    return new WorkspaceProject.Member(
        rs.getString("id"),
        rs.getString("display_name"),
        rs.getString("role"),
        rs.getLong("revision"));
  }

  static void requireAdmin(AppUser actor) {
    if (!actor.roles().contains("ADMIN"))
      throw new AccessDeniedException("Platform admin required");
  }

  static void validateMemberRole(String role) {
    if (!"TEST".equals(role) && !"DEV".equals(role))
      throw new IllegalArgumentException("role must be TEST or DEV");
  }

  static long publicId(String value) {
    if (value == null || !value.matches("[1-9][0-9]{0,18}"))
      throw new IllegalArgumentException("Invalid resource ID");
    return Long.parseLong(value);
  }

  private static boolean isWorkspaceRole(String role) {
    return "OWNER".equals(role) || "TEST".equals(role) || "DEV".equals(role);
  }

  private static void validateProject(String name, String description) {
    if (name == null
        || name.isBlank()
        || name.codePointCount(0, name.length()) > 160
        || description == null) {
      throw new IllegalArgumentException(
          "name and description are required; name must fit 160 characters");
    }
  }

  record ProjectCreate(String name, String description, String ownerId) {}

  record ProjectEdit(String name, String description, Long revision) {}

  record MemberWrite(String role, Long revision) {}

  private record MemberCommand(long userId, long revision, String role) {}
}
