package io.testopsai.server.platform.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testopsai.server.platform.security.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.function.Supplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DurableCommandReceipt {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final byte[] secret;

  DurableCommandReceipt(JdbcTemplate jdbc, ObjectMapper json, JwtProperties properties) {
    this.jdbc = jdbc;
    this.json = json;
    this.secret = properties.secret().getBytes(StandardCharsets.UTF_8);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public <T> T execute(
      long actor,
      String operation,
      long target,
      String key,
      Object request,
      Class<T> resultType,
      Supplier<T> command) {
    if (!UUID.fromString(key).toString().equalsIgnoreCase(key)) {
      throw new IllegalArgumentException("Idempotency-Key must be a UUID");
    }
    byte[] digest = digest(request);
    jdbc.update(
        """
        INSERT INTO workspace_command_receipt
          (actor_id, operation, target_id, command_key, request_digest, expires_at)
        VALUES (?, ?, ?, UUID_TO_BIN(?), ?, UTC_TIMESTAMP(6) + INTERVAL 24 HOUR)
        ON DUPLICATE KEY UPDATE command_key = command_key
        """,
        actor,
        operation,
        target,
        key,
        digest);
    var row =
        jdbc.queryForObject(
            """
        SELECT request_digest, response_json, expires_at <= UTC_TIMESTAMP(6) AS expired
        FROM workspace_command_receipt
        WHERE actor_id=? AND operation=? AND target_id=? AND command_key=UUID_TO_BIN(?)
        FOR UPDATE
        """,
            (rs, n) -> map(rs),
            actor,
            operation,
            target,
            key);
    if (row == null) throw new IllegalStateException("Command receipt not reserved");
    if (!row.expired() && !MessageDigest.isEqual(row.digest(), digest)) {
      throw new IdempotencyConflictException(key);
    }
    if (!row.expired() && row.response() != null) return decode(row.response(), resultType);

    T result = command.get();
    jdbc.update(
        """
        UPDATE workspace_command_receipt SET request_digest=?, response_json=?,
          expires_at=UTC_TIMESTAMP(6) + INTERVAL 24 HOUR
        WHERE actor_id=? AND operation=? AND target_id=? AND command_key=UUID_TO_BIN(?)
        """,
        digest,
        encode(result),
        actor,
        operation,
        target,
        key);
    return result;
  }

  private byte[] digest(Object request) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      mac.update("TestOps AI workspace receipt\0".getBytes(StandardCharsets.UTF_8));
      return mac.doFinal(encode(request).getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("HMAC unavailable", ex);
    }
  }

  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Unable to encode command receipt");
    }
  }

  private <T> T decode(String value, Class<T> type) {
    try {
      return json.readValue(value, type);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Unable to read command receipt");
    }
  }

  private static Receipt map(ResultSet rs) throws SQLException {
    return new Receipt(
        rs.getBytes("request_digest"), rs.getString("response_json"), rs.getBoolean("expired"));
  }

  private record Receipt(byte[] digest, String response, boolean expired) {}
}
