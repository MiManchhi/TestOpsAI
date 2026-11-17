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
            "SELECT id, username, password_hash, created_at FROM users WHERE username = ?",
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
        new AppUser(user.id(), user.username(), user.passwordHash(), roles, user.createdAt()));
  }

  private AppUser mapUser(ResultSet rs) throws SQLException {
    return new AppUser(
        rs.getLong("id"),
        rs.getString("username"),
        rs.getString("password_hash"),
        List.of(),
        rs.getTimestamp("created_at").toInstant());
  }
}
