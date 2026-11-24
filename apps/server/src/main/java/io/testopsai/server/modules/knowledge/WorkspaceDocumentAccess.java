package io.testopsai.server.modules.knowledge;

import io.testopsai.server.modules.identity.AppUser;
import io.testopsai.server.platform.web.WorkspaceFailure;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceDocumentAccess {
  static final String MEMBER_ROLES = "('OWNER','TEST','DEV')";
  private final JdbcTemplate jdbc;

  WorkspaceDocumentAccess(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public boolean member(AppUser actor, long project) {
    List<String> roles =
        jdbc.query(
            """
        SELECT role FROM project_members WHERE project_id=? AND user_id=?
        AND role IN ('OWNER','TEST','DEV')
        """,
            (rs, index) -> rs.getString(1),
            project,
            actor.id());
    if (roles.isEmpty()) throw WorkspaceFailure.notFound();
    return "OWNER".equals(roles.getFirst());
  }

  void owner(AppUser actor, long project) {
    if (!member(actor, project)) throw new AccessDeniedException("Project owner required");
  }

  public void lock(long project) {
    if (jdbc.query(
            "SELECT id FROM projects WHERE id=? FOR UPDATE", (rs, i) -> rs.getLong(1), project)
        .isEmpty()) {
      throw WorkspaceFailure.notFound();
    }
  }

  void advanceAuthorization(long project) {
    jdbc.update("UPDATE projects SET auth_revision=auth_revision+1 WHERE id=?", project);
  }
}
