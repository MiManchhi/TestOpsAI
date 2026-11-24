package io.testopsai.server.modules.knowledge;

import java.time.Instant;
import java.util.List;

record WorkspaceDocument(
    String documentId,
    String versionId,
    String title,
    String kind,
    String versionLabel,
    String reviewStatus,
    String indexStatus,
    String visibility,
    Submitter submittedBy,
    long revision,
    String reason,
    Instant createdAt,
    String errorCode) {
  record Submitter(String userId, String displayName) {}

  record Submission(
      String title,
      String kind,
      String versionLabel,
      String attachmentId,
      String documentId,
      String changeNote) {}

  record Review(String decision, String note, Long revision) {}

  record Readers(String visibility, List<String> userIds, Long revision) {}

  record Block(String locator, String text) {}

  record Content(String versionId, List<Block> blocks, String nextCursor) {}

  record Basis(
      String basisId,
      String label,
      List<String> versionIds,
      Instant effectiveFrom,
      Instant effectiveTo,
      long revision) {}

  record BasisSet(String label, List<String> versionIds, Long expectedRevision) {}

  record Receipt(String id, String state) {}

  record Job(
      String jobId,
      String projectId,
      String operation,
      String stage,
      String status,
      String errorCode,
      Instant updatedAt) {}
}
