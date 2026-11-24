package io.testopsai.server.modules.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import io.testopsai.server.modules.knowledge.WorkspaceAnswerEvidence;
import io.testopsai.server.modules.run.WorkspaceRunService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class WorkspaceChat {
  private WorkspaceChat() {}

  record Input(
      String text,
      UUID clientMessageId,
      UUID conversationId,
      List<UUID> attachmentIds,
      Long runId) {}

  record Turn(String conversationId, String turnId, String status) {}

  record Conversation(String conversationId, String title, Instant updatedAt) {}

  record Message(
      String messageId,
      String author,
      String text,
      JsonNode answer,
      Instant createdAt,
      JsonNode attachments,
      String turnId) {}

  record Job(
      String turnId,
      String conversation,
      long project,
      long actor,
      String messageId,
      String text,
      String requestId,
      Long run) {}

  record Scope(
      String projectId,
      String turnId,
      List<String> versionIds,
      List<String> runIds,
      List<String> attachmentIds,
      long authRevision) {}

  record Proof(WorkspaceAnswerEvidence.EvidenceRef source, String excerpt) {}

  record ScopeFacts(
      Scope scope,
      WorkspaceAnswerEvidence.Basis basis,
      WorkspaceAnswerEvidence.Basis basisAtRun,
      WorkspaceRunService.Run run,
      List<WorkspaceRunService.Result> runResults,
      List<Proof> logProofs,
      List<WorkspaceAnswerEvidence.Version> versions,
      List<WorkspaceAnswerEvidence.File> files,
      List<Message> history) {}
}
