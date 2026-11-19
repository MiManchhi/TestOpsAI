package io.testopsai.server.modules.catalog;

import java.util.List;

/** Server-side case filter; the same object serves the HTTP list and (later) the AI tool query. */
record CaseFilter(
    Long suiteId,
    boolean includeDescendants,
    String module,
    String query,
    List<String> tags,
    String tagsMatch,
    String priority,
    String lifecycleStatus,
    String sourceType) {}
