package io.testopsai.server.modules.catalog;

import java.util.List;

public record SelectionCreateRequest(
    String mode, List<String> caseIds, CaseFilterRequest filter, List<String> excludedCaseIds) {

  public record CaseFilterRequest(
      String suiteId,
      Boolean includeDescendants,
      String module,
      String query,
      List<String> tags,
      String tagsMatch,
      String priority,
      String lifecycleStatus,
      String sourceType) {}
}
