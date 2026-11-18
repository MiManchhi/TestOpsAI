package io.testopsai.server.modules.project;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectRepository {
  private final JdbcTemplate jdbc;

  public ProjectRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<Project> findById(Long id) {
    List<Project> list =
        jdbc.query(
            "SELECT id, project_key, name, description, created_at FROM projects WHERE id = ?",
            this::mapProject,
            id);
    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
  }

  public Optional<Project> findByKey(String key) {
    List<Project> list =
        jdbc.query(
            "SELECT id, project_key, name, description, created_at FROM projects WHERE project_key = ?",
            this::mapProject,
            key);
    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
  }

  public List<Project> findAuthorizedProjects(Long userId, boolean isAdmin) {
    if (isAdmin) {
      return jdbc.query(
          "SELECT id, project_key, name, description, created_at FROM projects ORDER BY id",
          this::mapProject);
    }
    return jdbc.query(
        "SELECT p.id, p.project_key, p.name, p.description, p.created_at"
            + " FROM projects p JOIN project_members pm ON p.id = pm.project_id"
            + " WHERE pm.user_id = ? ORDER BY p.id",
        this::mapProject,
        userId);
  }

  public boolean existsByKey(String key) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM projects WHERE project_key = ?", Integer.class, key);
    return count != null && count > 0;
  }

  public void insert(Project p) {
    jdbc.update(
        "INSERT INTO projects (project_key, name, description) VALUES (?, ?, ?)",
        p.projectKey(),
        p.name(),
        p.description());
  }

  public void update(Project p) {
    jdbc.update(
        "UPDATE projects SET name = ?, description = ? WHERE id = ?",
        p.name(),
        p.description(),
        p.id());
  }

  public Optional<String> getMemberRole(Long projectId, Long userId) {
    List<String> roles =
        jdbc.query(
            "SELECT role FROM project_members WHERE project_id = ? AND user_id = ?",
            (rs, rowNum) -> rs.getString("role"),
            projectId,
            userId);
    return roles.isEmpty() ? Optional.empty() : Optional.of(roles.get(0));
  }

  public List<ProjectMember> listMembers(Long projectId) {
    return jdbc.query(
        "SELECT project_id, user_id, role, created_at FROM project_members WHERE project_id = ?",
        this::mapMember,
        projectId);
  }

  public int countOwners(Long projectId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM project_members WHERE project_id = ? AND role = 'OWNER'",
            Integer.class,
            projectId);
    return count != null ? count : 0;
  }

  public void setMember(Long projectId, Long userId, String role) {
    jdbc.update(
        "INSERT INTO project_members (project_id, user_id, role) VALUES (?, ?, ?)"
            + " ON DUPLICATE KEY UPDATE role = VALUES(role)",
        projectId,
        userId,
        role);
  }

  public void removeMember(Long projectId, Long userId) {
    jdbc.update(
        "DELETE FROM project_members WHERE project_id = ? AND user_id = ?", projectId, userId);
  }

  public boolean isMember(Long projectId, Long userId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM project_members WHERE project_id = ? AND user_id = ?",
            Integer.class,
            projectId,
            userId);
    return count != null && count > 0;
  }

  private Project mapProject(ResultSet rs, int rowNum) throws SQLException {
    return new Project(
        rs.getLong("id"),
        rs.getString("project_key"),
        rs.getString("name"),
        rs.getString("description"),
        rs.getTimestamp("created_at").toInstant());
  }

  private ProjectMember mapMember(ResultSet rs, int rowNum) throws SQLException {
    return new ProjectMember(
        rs.getLong("project_id"),
        rs.getLong("user_id"),
        rs.getString("role"),
        rs.getTimestamp("created_at").toInstant());
  }
}
