package io.testopsai.server.modules.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectCaseRepository {
  private static final String SELECT_COLUMNS =
      "SELECT c.id, c.project_id, c.suite_id, c.stable_key, c.title, c.module, c.priority,"
          + " c.lifecycle_status, c.source_type, c.documentation, c.expected_result, c.source_path,"
          + " c.source_line, c.source_revision, c.steps_json, c.version, c.updated_at"
          + " FROM project_test_cases c";

  private static final Map<String, String> SORT_COLUMNS =
      Map.of("title", "c.title", "updatedAt", "c.updated_at");

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;

  public ProjectCaseRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  public long count(Long projectId, CaseFilter filter, List<Long> suiteIds) {
    Query where = buildWhere(projectId, filter, suiteIds, List.of());
    Long total =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM project_test_cases c" + where.sql(),
            Long.class,
            where.params().toArray());
    return total == null ? 0L : total;
  }

  public List<ProjectCase> findPage(
      Long projectId, CaseFilter filter, List<Long> suiteIds, String sort, int size, long offset) {
    Query where = buildWhere(projectId, filter, suiteIds, List.of());
    List<Object> params = new ArrayList<>(where.params());
    params.add(size);
    params.add(offset);
    List<ProjectCase> rows =
        jdbc.query(
            SELECT_COLUMNS + where.sql() + " " + orderBy(sort) + " LIMIT ? OFFSET ?",
            this::mapRow,
            params.toArray());
    return attachTags(rows);
  }

  /** Loads ordered case references for a selection snapshot (bounded by limit + 1). */
  public List<CaseSelection.CaseRef> findRefs(
      Long projectId,
      CaseFilter filter,
      List<Long> suiteIds,
      List<Long> excludedCaseIds,
      int limit) {
    Query where = buildWhere(projectId, filter, suiteIds, excludedCaseIds);
    List<Object> params = new ArrayList<>(where.params());
    params.add(limit);
    return jdbc.query(
        "SELECT c.id, c.version FROM project_test_cases c"
            + where.sql()
            + " ORDER BY c.id ASC LIMIT ?",
        (rs, rowNum) -> new CaseSelection.CaseRef(rs.getLong("id"), rs.getInt("version")),
        params.toArray());
  }

  public List<CaseSelection.CaseRef> findRefsByIds(Long projectId, List<Long> caseIds) {
    if (caseIds.isEmpty()) return List.of();
    String placeholders = String.join(",", caseIds.stream().map(id -> "?").toList());
    List<Object> params = new ArrayList<>();
    params.add(projectId);
    params.addAll(caseIds);
    return jdbc.query(
        "SELECT id, version FROM project_test_cases WHERE project_id = ? AND id IN ("
            + placeholders
            + ") ORDER BY id ASC",
        (rs, rowNum) -> new CaseSelection.CaseRef(rs.getLong("id"), rs.getInt("version")),
        params.toArray());
  }

  public Optional<ProjectCase> findById(Long projectId, Long caseId) {
    List<ProjectCase> rows =
        jdbc.query(
            SELECT_COLUMNS + " WHERE c.project_id = ? AND c.id = ?",
            this::mapRow,
            projectId,
            caseId);
    return rows.isEmpty() ? Optional.empty() : Optional.of(attachTags(rows).get(0));
  }

  public Long insert(
      Long projectId,
      String stableKey,
      String title,
      String module,
      String priority,
      String documentation,
      String expectedResult) {
    KeyHolder keys = new GeneratedKeyHolder();
    jdbc.update(
        connection -> {
          PreparedStatement ps =
              connection.prepareStatement(
                  "INSERT INTO project_test_cases"
                      + " (project_id, stable_key, title, module, priority, lifecycle_status,"
                      + "  source_type, documentation, expected_result, steps_json, version)"
                      + " VALUES (?, ?, ?, ?, ?, 'ACTIVE', 'MANUAL', ?, ?, '[]', 1)",
                  Statement.RETURN_GENERATED_KEYS);
          ps.setLong(1, projectId);
          ps.setString(2, stableKey);
          ps.setString(3, title);
          ps.setString(4, module);
          ps.setString(5, priority);
          ps.setString(6, documentation);
          ps.setString(7, expectedResult);
          return ps;
        },
        keys);
    Number key = keys.getKey();
    if (key == null) throw new IllegalStateException("Insert did not return a generated id");
    return key.longValue();
  }

  public int updateWithVersionCas(
      Long projectId,
      Long caseId,
      int expectedVersion,
      String title,
      String module,
      String priority,
      String lifecycleStatus,
      String documentation,
      String expectedResult) {
    return jdbc.update(
        "UPDATE project_test_cases SET title = ?, module = ?, priority = ?, lifecycle_status = ?,"
            + " documentation = ?, expected_result = ?, version = version + 1"
            + " WHERE project_id = ? AND id = ? AND version = ?",
        title,
        module,
        priority,
        lifecycleStatus,
        documentation,
        expectedResult,
        projectId,
        caseId,
        expectedVersion);
  }

  public void replaceTags(Long caseId, List<String> tags) {
    jdbc.update("DELETE FROM project_case_tags WHERE case_id = ?", caseId);
    for (String tag : tags) {
      jdbc.update("INSERT INTO project_case_tags (case_id, tag) VALUES (?, ?)", caseId, tag);
    }
  }

  private List<ProjectCase> attachTags(List<ProjectCase> rows) {
    if (rows.isEmpty()) return rows;
    List<Long> ids = rows.stream().map(ProjectCase::id).toList();
    String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
    Map<Long, List<String>> tagsByCase = new LinkedHashMap<>();
    jdbc.query(
        "SELECT case_id, tag FROM project_case_tags WHERE case_id IN ("
            + placeholders
            + ")"
            + " ORDER BY case_id, tag",
        rs -> {
          tagsByCase
              .computeIfAbsent(rs.getLong("case_id"), k -> new ArrayList<>())
              .add(rs.getString("tag"));
        },
        ids.toArray());
    return rows.stream()
        .map(
            row ->
                new ProjectCase(
                    row.id(),
                    row.projectId(),
                    row.suiteId(),
                    row.stableKey(),
                    row.title(),
                    row.module(),
                    List.copyOf(tagsByCase.getOrDefault(row.id(), List.of())),
                    row.priority(),
                    row.lifecycleStatus(),
                    row.sourceType(),
                    row.documentation(),
                    row.expectedResult(),
                    row.sourceRef(),
                    row.steps(),
                    row.version(),
                    row.updatedAt()))
        .toList();
  }

  private String orderBy(String sort) {
    String[] parts = sort.split(",");
    String column = SORT_COLUMNS.get(parts[0].trim());
    String direction = parts.length > 1 ? parts[1].trim().toUpperCase() : "ASC";
    return "ORDER BY " + column + " " + direction + ", c.id ASC";
  }

  private Query buildWhere(
      Long projectId, CaseFilter filter, List<Long> suiteIds, List<Long> excludedCaseIds) {
    StringBuilder sql = new StringBuilder(" WHERE c.project_id = ?");
    List<Object> params = new ArrayList<>();
    params.add(projectId);

    if (suiteIds != null && !suiteIds.isEmpty()) {
      sql.append(" AND c.suite_id IN (")
          .append(String.join(",", suiteIds.stream().map(id -> "?").toList()))
          .append(")");
      params.addAll(suiteIds);
    }
    if (filter.module() != null && !filter.module().isBlank()) {
      sql.append(" AND c.module = ?");
      params.add(filter.module());
    }
    if (filter.query() != null && !filter.query().isBlank()) {
      String pattern = "%" + escapeLike(filter.query().toLowerCase()) + "%";
      sql.append(
          " AND (LOWER(c.title) LIKE ? ESCAPE '\\' OR LOWER(c.documentation) LIKE ? ESCAPE '\\')");
      params.add(pattern);
      params.add(pattern);
    }
    if (filter.priority() != null) {
      sql.append(" AND c.priority = ?");
      params.add(filter.priority());
    }
    if (filter.lifecycleStatus() != null) {
      sql.append(" AND c.lifecycle_status = ?");
      params.add(filter.lifecycleStatus());
    }
    if (filter.sourceType() != null) {
      sql.append(" AND c.source_type = ?");
      params.add(filter.sourceType());
    }
    List<String> tags = filter.tags();
    if (tags != null && !tags.isEmpty()) {
      if ("ANY".equals(filter.tagsMatch())) {
        sql.append(
                " AND EXISTS (SELECT 1 FROM project_case_tags t WHERE t.case_id = c.id AND t.tag IN (")
            .append(String.join(",", tags.stream().map(t -> "?").toList()))
            .append("))");
        params.addAll(tags);
      } else {
        for (String tag : tags) {
          sql.append(
              " AND EXISTS (SELECT 1 FROM project_case_tags t WHERE t.case_id = c.id AND t.tag = ?)");
          params.add(tag);
        }
      }
    }
    if (excludedCaseIds != null && !excludedCaseIds.isEmpty()) {
      sql.append(" AND c.id NOT IN (")
          .append(String.join(",", excludedCaseIds.stream().map(id -> "?").toList()))
          .append(")");
      params.addAll(excludedCaseIds);
    }
    return new Query(sql.toString(), params);
  }

  private String escapeLike(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private ProjectCase mapRow(ResultSet rs, int rowNum) throws SQLException {
    long suiteId = rs.getLong("suite_id");
    boolean suiteWasNull = rs.wasNull();
    String path = rs.getString("source_path");
    SourceRef sourceRef = null;
    if (path != null) {
      sourceRef = new SourceRef(path, rs.getInt("source_line"), rs.getString("source_revision"));
    }
    return new ProjectCase(
        rs.getLong("id"),
        rs.getLong("project_id"),
        suiteWasNull ? null : suiteId,
        rs.getString("stable_key"),
        rs.getString("title"),
        rs.getString("module"),
        List.of(),
        rs.getString("priority"),
        rs.getString("lifecycle_status"),
        rs.getString("source_type"),
        nullToEmpty(rs.getString("documentation")),
        nullToEmpty(rs.getString("expected_result")),
        sourceRef,
        parseSteps(rs.getString("steps_json")),
        rs.getInt("version"),
        rs.getTimestamp("updated_at").toInstant());
  }

  private List<CaseStep> parseSteps(String json) {
    if (json == null || json.isBlank()) return List.of();
    try {
      return objectMapper.readValue(json, new TypeReference<List<CaseStep>>() {});
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Corrupt steps_json in project_test_cases", e);
    }
  }

  private String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private record Query(String sql, List<Object> params) {}
}
