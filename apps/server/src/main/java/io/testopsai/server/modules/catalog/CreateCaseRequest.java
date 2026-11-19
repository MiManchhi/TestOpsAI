package io.testopsai.server.modules.catalog;

import java.util.List;

public record CreateCaseRequest(
    String title,
    String module,
    List<String> tags,
    String priority,
    String documentation,
    String expectedResult) {}
