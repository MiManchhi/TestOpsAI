package io.testopsai.server.modules.catalog;

record Suite(
    Long id,
    Long projectId,
    Long parentId,
    String name,
    String sourcePath,
    String revision,
    long caseCount) {}
