package io.testopsai.server.modules.catalog;

public record SuiteResponse(
    String id,
    String projectId,
    String parentId,
    String name,
    String sourcePath,
    String revision,
    long caseCount) {
  static SuiteResponse from(Suite s) {
    return new SuiteResponse(
        String.valueOf(s.id()),
        String.valueOf(s.projectId()),
        s.parentId() == null ? null : String.valueOf(s.parentId()),
        s.name(),
        s.sourcePath(),
        s.revision(),
        s.caseCount());
  }
}
