package io.testopsai.server.modules.catalog;

import java.util.List;

public record PatchCaseRequest(
    Integer version,
    String title,
    String module,
    List<String> tags,
    String priority,
    String lifecycleStatus,
    String documentation,
    String expectedResult) {}
