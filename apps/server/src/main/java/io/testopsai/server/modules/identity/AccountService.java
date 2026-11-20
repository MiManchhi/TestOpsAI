package io.testopsai.server.modules.identity;

import io.testopsai.server.platform.error.RequestIdFilter;
import io.testopsai.server.platform.idempotency.DurableCommandReceipt;
import io.testopsai.server.platform.web.PageResponse;
import io.testopsai.server.platform.web.WorkspaceFailure;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.slf4j.MDC;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AccountService {
  private static final String ADMIN_USERS =
      """
      EXISTS (SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id
              WHERE ur.user_id=u.id AND r.role_code='ADMIN')
      """;
  private static final String ACCOUNT_COLUMNS =
      """
      id, username, COALESCE(display_name, username) AS display_name, enabled,
      must_change_password, revision
      """;
  private final JdbcTemplate jdbc;
  private final PasswordEncoder passwords;
  private final DurableCommandReceipt receipts;

  AccountService(JdbcTemplate jdbc, PasswordEncoder passwords, DurableCommandReceipt receipts) {
    this.jdbc = jdbc;
    this.passwords = passwords;
    this.receipts = receipts;
  }

  PageResponse<Account> listAccounts(AppUser actor, int page, int size) {
    requireAdmin(actor);
    PageResponse.validatePagination(page, size);
    long total =
        jdbc.queryForObject("SELECT COUNT(*) FROM users u WHERE NOT " + ADMIN_USERS, Long.class);
    var rows =
        jdbc.query(
            "SELECT "
                + ACCOUNT_COLUMNS
                + " FROM users u WHERE NOT "
                + ADMIN_USERS
                + " ORDER BY u.id LIMIT ? OFFSET ?",
            (rs, n) -> map(rs),
            size,
            (long) page * size);
    return new PageResponse<>(rows, total, page, size);
  }

  @Transactional
  public Account createAccount(AppUser actor, String key, AccountSeed body) {
    requireAdmin(actor);
    String username = requiredText(body.username(), 160, "username");
    String displayName = requiredText(body.displayName(), 160, "displayName");
    validatePassword(body.temporaryPassword());
    return receipts.execute(
        actor.id(),
        "createAccount",
        0,
        key,
        body,
        Account.class,
        () -> {
          var generated = new GeneratedKeyHolder();
          String hash = passwords.encode(body.temporaryPassword());
          try {
            jdbc.update(
                connection -> {
                  var stmt =
                      connection.prepareStatement(
                          """
              INSERT INTO users (username, display_name, password_hash, must_change_password)
              VALUES (?, ?, ?, TRUE)
              """,
                          Statement.RETURN_GENERATED_KEYS);
                  stmt.setString(1, username);
                  stmt.setString(2, displayName);
                  stmt.setString(3, hash);
                  return stmt;
                },
                generated);
          } catch (DuplicateKeyException ex) {
            throw WorkspaceFailure.conflict("ACCOUNT_EXISTS", "Username is already in use");
          }
          long id = generated.getKey().longValue();
          audit(actor.id(), "createAccount", id);
          return findAccount(id);
        });
  }

  @Transactional
  public Account setAccountStatus(AppUser actor, long id, String key, AccountStatus body) {
    requireAdmin(actor);
    requireManagedAccount(id);
    if (body.enabled() == null) throw new IllegalArgumentException("enabled is required");
    return receipts.execute(
        actor.id(),
        "setAccountStatus",
        id,
        key,
        body,
        Account.class,
        () -> {
          Account target = lockManagedAccount(id);
          WorkspaceFailure.checkRevision(body.revision(), target.revision());
          if (!body.enabled() && ownsProject(id)) {
            throw WorkspaceFailure.conflict(
                "OWNER_TRANSFER_REQUIRED", "Transfer the owner's projects first");
          }
          if (body.enabled() != target.enabled()) {
            jdbc.update(
                """
            UPDATE users SET enabled=?, revision=revision+1, credential_revision=credential_revision+1
            WHERE id=?
            """,
                body.enabled(),
                id);
            audit(actor.id(), "setAccountStatus", id);
          }
          return findAccount(id);
        });
  }

  @Transactional
  public void resetPassword(AppUser actor, long id, String key, TempPassword body) {
    requireAdmin(actor);
    requireManagedAccount(id);
    validatePassword(body.temporaryPassword());
    receipts.execute(
        actor.id(),
        "resetPassword",
        id,
        key,
        body,
        Void.class,
        () -> {
          Account target = lockManagedAccount(id);
          WorkspaceFailure.checkRevision(body.revision(), target.revision());
          storePassword(id, body.temporaryPassword(), true);
          audit(actor.id(), "resetPassword", id);
          return null;
        });
  }

  @Transactional
  public void changePassword(AppUser actor, String key, PasswordChange body) {
    validatePassword(body.currentPassword());
    validatePassword(body.newPassword());
    var live =
        jdbc.queryForObject(
            """
        SELECT password_hash, enabled, credential_revision FROM users WHERE id=? FOR UPDATE
        """,
            (rs, n) -> new Credential(rs.getString(1), rs.getBoolean(2), rs.getLong(3)),
            actor.id());
    if (live == null || !live.enabled() || live.revision() != actor.credentialRevision()) {
      throw new AccessDeniedException("Account credentials changed");
    }
    receipts.execute(
        actor.id(),
        "changePassword",
        actor.id(),
        key,
        body,
        Void.class,
        () -> {
          if (!passwords.matches(body.currentPassword(), live.hash())) {
            throw new IllegalArgumentException("The current password is incorrect");
          }
          if (body.currentPassword().equals(body.newPassword())) {
            throw new IllegalArgumentException("Choose a different password");
          }
          storePassword(actor.id(), body.newPassword(), false);
          audit(actor.id(), "changePassword", actor.id());
          return null;
        });
  }

  private void storePassword(long id, String raw, boolean temporary) {
    jdbc.update(
        """
        UPDATE users SET password_hash=?, must_change_password=?, revision=revision+1,
          credential_revision=credential_revision+1 WHERE id=?
        """,
        passwords.encode(raw),
        temporary,
        id);
  }

  private Account findAccount(long id) {
    return jdbc
        .query("SELECT " + ACCOUNT_COLUMNS + " FROM users WHERE id=?", (rs, n) -> map(rs), id)
        .stream()
        .findFirst()
        .orElseThrow(WorkspaceFailure::notFound);
  }

  private void requireManagedAccount(long id) {
    if (jdbc.queryForObject(
            "SELECT COUNT(*) FROM users u WHERE id=? AND NOT " + ADMIN_USERS, Long.class, id)
        == 0) throw WorkspaceFailure.notFound();
  }

  private Account lockManagedAccount(long id) {
    return jdbc
        .query(
            "SELECT "
                + ACCOUNT_COLUMNS
                + " FROM users u WHERE id=? AND NOT "
                + ADMIN_USERS
                + " FOR UPDATE",
            (rs, n) -> map(rs),
            id)
        .stream()
        .findFirst()
        .orElseThrow(WorkspaceFailure::notFound);
  }

  private boolean ownsProject(long id) {
    return !jdbc.query(
            """
        SELECT project_id FROM project_members WHERE user_id=? AND role='OWNER' LIMIT 1 FOR SHARE
        """,
            (rs, n) -> rs.getLong(1),
            id)
        .isEmpty();
  }

  private void audit(long actor, String operation, long id) {
    jdbc.update(
        """
        INSERT INTO workspace_audit_event (actor_id, operation, target_id, request_id)
        VALUES (?, ?, ?, ?)
        """,
        actor,
        operation,
        id,
        MDC.get(RequestIdFilter.MDC_KEY));
  }

  private static Account map(ResultSet rs) throws SQLException {
    return new Account(
        rs.getString("id"),
        rs.getString("username"),
        rs.getString("display_name"),
        rs.getBoolean("enabled"),
        rs.getBoolean("must_change_password"),
        rs.getLong("revision"));
  }

  private static void requireAdmin(AppUser actor) {
    if (!actor.roles().contains("ADMIN"))
      throw new AccessDeniedException("Platform admin required");
  }

  private static String requiredText(String value, int max, String field) {
    if (value == null || value.isBlank() || value.codePointCount(0, value.length()) > max) {
      throw new IllegalArgumentException(
          field + " is required and must fit " + max + " characters");
    }
    return value.strip();
  }

  private static void validatePassword(String password) {
    if (password == null
        || password.isEmpty()
        || password.codePointCount(0, password.length()) > 128) {
      throw new IllegalArgumentException("Password must have 1 to 128 characters");
    }
  }

  record AccountSeed(String username, String displayName, String temporaryPassword) {}

  record AccountStatus(Boolean enabled, Long revision) {}

  record TempPassword(String temporaryPassword, Long revision) {}

  record PasswordChange(String currentPassword, String newPassword) {}

  private record Credential(String hash, boolean enabled, long revision) {}
}
