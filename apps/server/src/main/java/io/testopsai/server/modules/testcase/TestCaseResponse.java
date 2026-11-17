package io.testopsai.server.modules.testcase;

import java.time.Instant;

record TestCaseResponse(
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
    Instant updatedAt) {
  static TestCaseResponse from(TestCase tc) {
    return new TestCaseResponse(
        tc.id(),
        tc.projectKey(),
        tc.module(),
        tc.title(),
        tc.tags(),
        tc.priority(),
        tc.expectedResult(),
        tc.ownerId(),
        tc.status(),
        tc.createdAt(),
        tc.updatedAt());
  }
}
