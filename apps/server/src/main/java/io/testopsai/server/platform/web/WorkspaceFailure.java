package io.testopsai.server.platform.web;

import org.springframework.http.HttpStatus;

/** A public workspace error with a stable, client-safe code. */
public final class WorkspaceFailure extends RuntimeException {
  private final HttpStatus status;
  private final String code;

  public WorkspaceFailure(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }

  public static WorkspaceFailure notFound() {
    return new WorkspaceFailure(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
  }

  public static WorkspaceFailure conflict(String code, String message) {
    return new WorkspaceFailure(HttpStatus.CONFLICT, code, message);
  }

  public static void checkRevision(Long expected, long actual) {
    if (expected == null || expected < 1)
      throw new IllegalArgumentException("revision is required");
    if (expected != actual) throw conflict("VERSION_CONFLICT", "The resource has changed");
  }
}
