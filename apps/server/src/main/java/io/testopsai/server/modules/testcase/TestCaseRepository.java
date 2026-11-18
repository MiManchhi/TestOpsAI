package io.testopsai.server.modules.testcase;

import io.testopsai.server.platform.web.PageResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TestCaseRepository {
  private static final Set<String> SORT_FIELDS =
      Set.of("createdAt", "updatedAt", "title", "priority");
  private static final Set<String> SORT_FIELD_COLUMNS =
      Set.of("created_at", "updated_at", "title", "priority");

  private final JdbcTemplate jdbc;

  TestCaseRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  Optional<TestCase> findById(Long id) {
    List<TestCase> list =
        jdbc.query(
            "SELECT id, project_key, module, title, tags, priority, expected_result, owner_id,"
                + " status, created_at, updated_at FROM test_cases WHERE id = ?",
            this::mapRow,
            id);
    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
  }

  PageResponse<TestCaseResponse> findAll(
      String projectKey,
      String module,
      String priority,
      String status,
      String sort,
      int page,
      int size) {
    StringBuilder where = new StringBuilder("WHERE 1=1");
    List<Object> params = new ArrayList<>();

    if (status != null && !status.isBlank()) {
      where.append(" AND status = ?");
      params.add(status);
    } else {
      where.append(" AND status = 'ACTIVE'");
    }
    if (projectKey != null && !projectKey.isBlank()) {
      where.append(" AND project_key = ?");
      params.add(projectKey);
    }
    if (module != null && !module.isBlank()) {
      where.append(" AND module LIKE ?");
      params.add("%" + module + "%");
    }
    if (priority != null && !priority.isBlank()) {
      where.append(" AND priority = ?");
      params.add(priority);
    }

    // Count
    String countSql = "SELECT COUNT(*) FROM test_cases " + where;
    Long totalObj = jdbc.queryForObject(countSql, Long.class, params.toArray());
    long total = totalObj != null ? totalObj : 0L;

    // Sort validation
    String orderBy = "ORDER BY created_at DESC";
    if (sort != null && !sort.isBlank()) {
      String[] parts = sort.split(",");
      String field = parts[0].trim();
      String dir = parts.length > 1 ? parts[1].trim().toUpperCase() : "DESC";
      String column = toColumn(field);
      if (column != null && ("ASC".equals(dir) || "DESC".equals(dir))) {
        orderBy = "ORDER BY " + column + " " + dir;
      }
    }

    int offset = page * size;
    String querySql =
        "SELECT id, project_key, module, title, tags, priority, expected_result, owner_id,"
            + " status, created_at, updated_at FROM test_cases "
            + where
            + " "
            + orderBy
            + " LIMIT ? OFFSET ?";
    List<Object> queryParams = new ArrayList<>(params);
    queryParams.add(size);
    queryParams.add(offset);

    List<TestCase> items = jdbc.query(querySql, this::mapRow, queryParams.toArray());
    List<TestCaseResponse> responses = items.stream().map(TestCaseResponse::from).toList();
    return new PageResponse<>(responses, total, page, size);
  }

  void insert(TestCase tc) {
    jdbc.update(
        "INSERT INTO test_cases (project_key, module, title, tags, priority, expected_result,"
            + " owner_id, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        tc.projectKey(),
        tc.module(),
        tc.title(),
        tc.tags(),
        tc.priority(),
        tc.expectedResult(),
        tc.ownerId(),
        tc.status());
  }

  void update(TestCase tc) {
    jdbc.update(
        "UPDATE test_cases SET title = ?, module = ?, tags = ?, priority = ?, expected_result = ?,"
            + " status = ? WHERE id = ?",
        tc.title(),
        tc.module(),
        tc.tags(),
        tc.priority(),
        tc.expectedResult(),
        tc.status(),
        tc.id());
  }

  boolean isValidSortField(String field) {
    if (field == null) return false;
    return SORT_FIELDS.contains(field.trim());
  }

  private String toColumn(String field) {
    if (field == null) return null;
    return switch (field.trim()) {
      case "createdAt" -> "created_at";
      case "updatedAt" -> "updated_at";
      case "title" -> "title";
      case "priority" -> "priority";
      default -> null;
    };
  }

  private TestCase mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new TestCase(
        rs.getLong("id"),
        rs.getString("project_key"),
        rs.getString("module"),
        rs.getString("title"),
        rs.getString("tags"),
        rs.getString("priority"),
        rs.getString("expected_result"),
        rs.getLong("owner_id"),
        rs.getString("status"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }
}
