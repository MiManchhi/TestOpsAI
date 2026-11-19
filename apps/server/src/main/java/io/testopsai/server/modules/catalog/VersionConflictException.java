package io.testopsai.server.modules.catalog;

public class VersionConflictException extends RuntimeException {
  public VersionConflictException(String caseId, int expectedVersion) {
    super("Case " + caseId + " was modified; expected version " + expectedVersion);
  }
}
