package io.testopsai.server.modules.catalog;

public class RobotCaseReadOnlyException extends RuntimeException {
  public RobotCaseReadOnlyException(String caseId) {
    super("Case " + caseId + " is Robot-sourced and read-only; re-import the source file instead");
  }
}
