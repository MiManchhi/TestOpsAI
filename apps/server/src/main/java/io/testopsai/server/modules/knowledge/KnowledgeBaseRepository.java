package io.testopsai.server.modules.knowledge;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeBaseRepository {
  private final JdbcTemplate jdbc;

  KnowledgeBaseRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  Optional<KnowledgeBase> findById(Long id) {
    List<KnowledgeBase> list =
        jdbc.query(
            "SELECT id, name, description, owner_id, visibility, status, created_at, updated_at"
                + " FROM knowledge_bases WHERE id = ?",
            this::mapRow,
            id);
    return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
  }

  PageResponse<KnowledgeBaseResponse> findAll(int page, int size) {
    Long totalObj =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM knowledge_bases WHERE status = 'ACTIVE'", Long.class);
    long total = totalObj != null ? totalObj : 0L;
    int offset = page * size;
    List<KnowledgeBase> items =
        jdbc.query(
            "SELECT id, name, description, owner_id, visibility, status, created_at, updated_at"
                + " FROM knowledge_bases WHERE status = 'ACTIVE' ORDER BY created_at DESC LIMIT ?"
                + " OFFSET ?",
            this::mapRow,
            size,
            offset);
    List<KnowledgeBaseResponse> responses =
        items.stream().map(KnowledgeBaseResponse::from).toList();
    return new PageResponse<>(responses, total, page, size);
  }

  boolean existsByOwnerAndName(Long ownerId, String name) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM knowledge_bases WHERE owner_id = ? AND name = ?",
            Integer.class,
            ownerId,
            name);
    return count != null && count > 0;
  }

  boolean existsByOwnerAndNameExcluding(Long ownerId, String name, Long excludeId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM knowledge_bases WHERE owner_id = ? AND name = ? AND id != ?",
            Integer.class,
            ownerId,
            name,
            excludeId);
    return count != null && count > 0;
  }

  void insert(KnowledgeBase kb) {
    jdbc.update(
        "INSERT INTO knowledge_bases (name, description, owner_id, visibility, status) VALUES (?, ?, ?, ?, ?)",
        kb.name(),
        kb.description(),
        kb.ownerId(),
        kb.visibility(),
        kb.status());
  }

  void update(KnowledgeBase kb) {
    jdbc.update(
        "UPDATE knowledge_bases SET name = ?, description = ?, visibility = ?, status = ? WHERE id = ?",
        kb.name(),
        kb.description(),
        kb.visibility(),
        kb.status(),
        kb.id());
  }

  boolean hasMembership(Long kbId, Long userId, String accessLevel) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM kb_members WHERE kb_id = ? AND user_id = ? AND (access_level = ? OR access_level = 'WRITE')",
            Integer.class,
            kbId,
            userId,
            accessLevel);
    return count != null && count > 0;
  }

  private KnowledgeBase mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new KnowledgeBase(
        rs.getLong("id"),
        rs.getString("name"),
        rs.getString("description"),
        rs.getLong("owner_id"),
        rs.getString("visibility"),
        rs.getString("status"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }
}
