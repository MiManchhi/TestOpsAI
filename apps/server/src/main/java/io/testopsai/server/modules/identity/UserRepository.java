package io.testopsai.server.modules.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
  private final JdbcTemplate jdbc;

  UserRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<AppUser> findByUsername(String username) {
    var users =
        jdbc.query(
            """
            SELECT id, username, COALESCE(display_name, username) AS display_name, password_hash,
                   created_at, enabled, must_change_password, revision, credential_revision
            FROM users WHERE username = ?
            """,
            (rs, rowNum) -> mapUser(rs),
            username);
    if (users.isEmpty()) return Optional.empty();
    AppUser user = users.get(0);
    List<String> roles =
        jdbc.query(
            "SELECT r.role_code FROM roles r JOIN user_roles ur ON r.id = ur.role_id WHERE ur.user_id = ?",
            (rs, rowNum) -> rs.getString("role_code"),
            user.id());
    return Optional.of(
        new AppUser(
            user.id(),
            user.username(),
            user.displayName(),
            user.passwordHash(),
            roles,
            user.createdAt(),
            user.enabled(),
            user.mustChangePassword(),
            user.revision(),
            user.credentialRevision()));
  }

  private AppUser mapUser(ResultSet rs) throws SQLException {
    return new AppUser(
        rs.getLong("id"),
        rs.getString("username"),
        rs.getString("display_name"),
        rs.getString("password_hash"),
        List.of(),
        rs.getTimestamp("created_at").toInstant(),
        rs.getBoolean("enabled"),
        rs.getBoolean("must_change_password"),
        rs.getLong("revision"),
        rs.getLong("credential_revision"));
  }
}
