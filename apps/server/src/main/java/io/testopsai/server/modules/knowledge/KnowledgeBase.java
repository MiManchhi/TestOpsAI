package io.testopsai.server.modules.knowledge;

import java.time.Instant;

record KnowledgeBase(
    Long id,
    String name,
    String description,
    Long ownerId,
    String visibility,
    String status,
    Instant createdAt,
    Instant updatedAt) {}
