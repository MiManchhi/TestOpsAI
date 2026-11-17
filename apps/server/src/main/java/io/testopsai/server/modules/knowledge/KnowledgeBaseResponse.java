package io.testopsai.server.modules.knowledge;

import java.time.Instant;

record KnowledgeBaseResponse(
    Long id,
    String name,
    String description,
    Long ownerId,
    String visibility,
    String status,
    Instant createdAt,
    Instant updatedAt) {
  static KnowledgeBaseResponse from(KnowledgeBase kb) {
    return new KnowledgeBaseResponse(
        kb.id(),
        kb.name(),
        kb.description(),
        kb.ownerId(),
        kb.visibility(),
        kb.status(),
        kb.createdAt(),
        kb.updatedAt());
  }
}
