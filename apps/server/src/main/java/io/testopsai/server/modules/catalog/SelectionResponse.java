package io.testopsai.server.modules.catalog;

import java.time.Instant;
import java.util.List;

public record SelectionResponse(
    String id,
    String projectId,
    int count,
    List<CaseVersionResponse> cases,
    String digest,
    Instant expiresAt) {

  public record CaseVersionResponse(String caseId, int version) {}

  static SelectionResponse from(CaseSelection s) {
    return new SelectionResponse(
        s.id(),
        String.valueOf(s.projectId()),
        s.count(),
        s.cases().stream()
            .map(c -> new CaseVersionResponse(String.valueOf(c.caseId()), c.version()))
            .toList(),
        s.digest(),
        s.expiresAt());
  }
}
