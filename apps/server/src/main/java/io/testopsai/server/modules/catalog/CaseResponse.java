package io.testopsai.server.modules.catalog;

import java.time.Instant;
import java.util.List;

public record CaseResponse(
    String id,
    String projectId,
    String suiteId,
    String stableKey,
    String title,
    String module,
    List<String> tags,
    String priority,
    String lifecycleStatus,
    String sourceType,
    String documentation,
    String expectedResult,
    SourceRefResponse sourceRef,
    List<CaseStep> steps,
    int version,
    Instant updatedAt) {

  public record SourceRefResponse(String path, int line, String revision) {}

  static CaseResponse from(ProjectCase c) {
    SourceRef ref = c.sourceRef();
    return new CaseResponse(
        String.valueOf(c.id()),
        String.valueOf(c.projectId()),
        c.suiteId() == null ? null : String.valueOf(c.suiteId()),
        c.stableKey(),
        c.title(),
        c.module(),
        c.tags(),
        c.priority(),
        c.lifecycleStatus(),
        c.sourceType(),
        c.documentation(),
        c.expectedResult(),
        ref == null ? null : new SourceRefResponse(ref.path(), ref.line(), ref.revision()),
        c.steps(),
        c.version(),
        c.updatedAt());
  }
}
