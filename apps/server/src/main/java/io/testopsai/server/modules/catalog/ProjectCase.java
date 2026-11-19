package io.testopsai.server.modules.catalog;

import java.time.Instant;
import java.util.List;

record ProjectCase(
    Long id,
    Long projectId,
    Long suiteId,
    String stableKey,
    String title,
    String module,
    List<String> tags,
    String priority,
    String lifecycleStatus,
    String sourceType,
    String documentation,
    String expectedResult,
    SourceRef sourceRef,
    List<CaseStep> steps,
    int version,
    Instant updatedAt) {}
