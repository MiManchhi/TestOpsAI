package io.testopsai.server.platform.idempotency;

public class IdempotencyConflictException extends RuntimeException {
  public IdempotencyConflictException(String key) {
    super("Idempotency key reused with different request: " + key);
  }
}
