package io.testopsai.server.modules.catalog;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SuiteRepository {
  private final JdbcTemplate jdbc;

  public SuiteRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<Suite> findById(Long projectId, Long suiteId) {
    List<Suite> rows =
        jdbc.query(
            "SELECT s.id, s.project_id, s.parent_id, s.name, s.source_path, s.revision,"
                + " (SELECT COUNT(*) FROM project_test_cases c"
                + "   WHERE c.suite_id = s.id AND c.lifecycle_status = 'ACTIVE') AS case_count"
                + " FROM suites s WHERE s.project_id = ? AND s.id = ?",
            this::mapRow,
            projectId,
            suiteId);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  public long count(Long projectId, Long parentId) {
    Long total =
        parentId == null
            ? jdbc.queryForObject(
                "SELECT COUNT(*) FROM suites WHERE project_id = ?", Long.class, projectId)
            : jdbc.queryForObject(
                "SELECT COUNT(*) FROM suites WHERE project_id = ? AND parent_id = ?",
                Long.class,
                projectId,
                parentId);
    return total == null ? 0L : total;
  }

  public List<Suite> findPage(Long projectId, Long parentId, int size, long offset) {
    String base =
        "SELECT s.id, s.project_id, s.parent_id, s.name, s.source_path, s.revision,"
            + " (SELECT COUNT(*) FROM project_test_cases c"
            + "   WHERE c.suite_id = s.id AND c.lifecycle_status = 'ACTIVE') AS case_count"
            + " FROM suites s WHERE s.project_id = ?";
    if (parentId == null) {
      return jdbc.query(
          base + " ORDER BY s.name ASC, s.id ASC LIMIT ? OFFSET ?",
          this::mapRow,
          projectId,
          size,
          offset);
    }
    return jdbc.query(
        base + " AND s.parent_id = ? ORDER BY s.name ASC, s.id ASC LIMIT ? OFFSET ?",
        this::mapRow,
        projectId,
        parentId,
        size,
        offset);
  }

  /** Returns the suite id and all transitive descendant ids inside the project. */
  public List<Long> findSubtreeIds(Long projectId, Long rootId) {
    return jdbc.query(
        "WITH RECURSIVE tree AS ("
            + " SELECT id FROM suites WHERE id = ? AND project_id = ?"
            + " UNION ALL"
            + " SELECT s.id FROM suites s JOIN tree t ON s.parent_id = t.id"
            + "  WHERE s.project_id = ?)"
            + " SELECT id FROM tree",
        (rs, rowNum) -> rs.getLong("id"),
        rootId,
        projectId,
        projectId);
  }

  private Suite mapRow(ResultSet rs, int rowNum) throws SQLException {
    long parentId = rs.getLong("parent_id");
    boolean parentWasNull = rs.wasNull();
    return new Suite(
        rs.getLong("id"),
        rs.getLong("project_id"),
        parentWasNull ? null : parentId,
        rs.getString("name"),
        rs.getString("source_path"),
        rs.getString("revision"),
        rs.getLong("case_count"));
  }
}
