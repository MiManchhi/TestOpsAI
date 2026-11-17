package io.testopsai.server.modules.testcase;

record CreateTestCaseRequest(
    String projectKey,
    String module,
    String title,
    String tags,
    String priority,
    String expectedResult) {}
