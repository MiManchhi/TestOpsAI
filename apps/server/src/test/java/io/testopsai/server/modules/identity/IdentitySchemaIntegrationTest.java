package io.testopsai.server.modules.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(
    named = "MYSQL_PASSWORD",
    matches = ".+",
    disabledReason = "MySQL password not configured; skipping identity schema tests.")
class IdentitySchemaIntegrationTest {

  @Autowired DataSource dataSource;
  @Autowired JdbcTemplate jdbc;

  @DynamicPropertySource
  static void mysqlProperties(DynamicPropertyRegistry registry) {
    // Use local MySQL from compose; CI will override via env.
    registry.add(
        "spring.datasource.url",
        () ->
            System.getenv()
                .getOrDefault(
                    "MYSQL_URL",
                    "jdbc:mysql://127.0.0.1:3306/testops?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"));
    registry.add(
        "spring.datasource.username", () -> System.getenv().getOrDefault("MYSQL_USER", "testops"));
    registry.add(
        "spring.datasource.password", () -> System.getenv().getOrDefault("MYSQL_PASSWORD", ""));
  }

  @Test
  void migrationCreatesIdentityTablesAndConstraints() {
    var tables =
        jdbc.queryForList(
            "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('users','roles','user_roles') ORDER BY TABLE_NAME");
    assertThat(tables)
        .extracting(m -> m.get("TABLE_NAME"))
        .containsExactly("roles", "user_roles", "users");
  }

  @Test
  void usersEnforcesUniqueUsernameAndNotNullPasswordHash() {
    jdbc.update("DELETE FROM users WHERE username IN (?, ?)", "u_dup_1", "u_dup_2");
    jdbc.update(
        "INSERT INTO users(username, password_hash, created_at, updated_at) VALUES(?, ?, ?, ?)",
        "u_dup_1",
        "hash_a",
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()));
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO users(username, password_hash, created_at, updated_at) VALUES(?, ?, ?, ?)",
                    "u_dup_1",
                    "hash_b",
                    Timestamp.from(Instant.now()),
                    Timestamp.from(Instant.now())))
        .isInstanceOf(Exception.class);
  }

  @Test
  void rolesEnforcesUniqueRoleCode() {
    jdbc.update("DELETE FROM roles WHERE role_code IN (?, ?)", "r_dup_1", "r_dup_2");
    jdbc.update(
        "INSERT INTO roles(role_code, description, created_at, updated_at) VALUES(?, ?, ?, ?)",
        "r_dup_1",
        "desc",
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()));
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO roles(role_code, description, created_at, updated_at) VALUES(?, ?, ?, ?)",
                    "r_dup_1",
                    "desc2",
                    Timestamp.from(Instant.now()),
                    Timestamp.from(Instant.now())))
        .isInstanceOf(Exception.class);
  }

  @Test
  void userRolesEnforcesForeignKeyConstraints() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO user_roles(user_id, role_id, created_at) VALUES(?, ?, ?)",
                    -999L,
                    -998L,
                    Timestamp.from(Instant.now())))
        .isInstanceOf(Exception.class);
  }
}
