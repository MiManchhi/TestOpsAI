package io.testopsai.server.modules.testcase;

import java.time.Instant;

record TestCase(
    Long id,
    String projectKey,
    String module,
    String title,
    String tags,
    String priority,
    String expectedResult,
    Long ownerId,
    String status,
    Instant createdAt,
    Instant updatedAt) {}
