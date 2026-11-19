package io.testopsai.server.modules.catalog;

import java.time.Instant;
import java.util.List;

record CaseSelection(
    String id, Long projectId, int count, List<CaseRef> cases, String digest, Instant expiresAt) {

  record CaseRef(Long caseId, int version) {}
}
