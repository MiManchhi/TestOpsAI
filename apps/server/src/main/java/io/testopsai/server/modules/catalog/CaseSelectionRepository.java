package io.testopsai.server.modules.catalog;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CaseSelectionRepository {
  private final JdbcTemplate jdbc;

  public CaseSelectionRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(
      String id,
      Long projectId,
      Long createdBy,
      String mode,
      int caseCount,
      String digest,
      String filterJson,
      Instant expiresAt) {
    jdbc.update(
        "INSERT INTO case_selections"
            + " (id, project_id, created_by, selection_mode, case_count, digest, filter_json,"
            + "  expires_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        id,
        projectId,
        createdBy,
        mode,
        caseCount,
        digest,
        filterJson,
        Timestamp.from(expiresAt));
  }

  public void insertItems(String selectionId, List<CaseSelection.CaseRef> refs) {
    List<Object[]> batch = new ArrayList<>();
    for (CaseSelection.CaseRef ref : refs) {
      batch.add(new Object[] {selectionId, ref.caseId(), ref.version()});
    }
    jdbc.batchUpdate(
        "INSERT INTO selection_items (selection_id, case_id, case_version) VALUES (?, ?, ?)",
        batch);
  }

  public Optional<CaseSelection> findById(String selectionId) {
    List<CaseSelection> rows =
        jdbc.query(
            "SELECT id, project_id, case_count, digest, expires_at FROM case_selections WHERE id = ?",
            (rs, rowNum) ->
                new CaseSelection(
                    rs.getString("id"),
                    rs.getLong("project_id"),
                    rs.getInt("case_count"),
                    loadItems(rs.getString("id")),
                    rs.getString("digest"),
                    rs.getTimestamp("expires_at").toInstant()),
            selectionId);
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  private List<CaseSelection.CaseRef> loadItems(String selectionId) {
    return jdbc.query(
        "SELECT case_id, case_version FROM selection_items WHERE selection_id = ? ORDER BY case_id",
        (rs, rowNum) -> new CaseSelection.CaseRef(rs.getLong("case_id"), rs.getInt("case_version")),
        selectionId);
  }
}
