package io.testopsai.server.modules.testcase;

record UpdateTestCaseRequest(
    String title,
    String module,
    String tags,
    String priority,
    String expectedResult,
    String status) {}
