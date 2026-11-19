package io.testopsai.server.modules.catalog;

import io.testopsai.server.modules.identity.AppUser;
import io.testopsai.server.platform.security.CurrentUserResolver;
import io.testopsai.server.platform.web.PageResponse;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Contract 1.0.0 case catalog: suites, project test cases and immutable selection snapshots. */
@RestController
@RequestMapping("/api/v2/projects/{projectId}")
public class CaseCatalogController {
  private final CaseCatalogService service;
  private final CurrentUserResolver currentUserResolver;

  public CaseCatalogController(
      CaseCatalogService service, CurrentUserResolver currentUserResolver) {
    this.service = service;
    this.currentUserResolver = currentUserResolver;
  }

  @GetMapping("/test-suites")
  public ResponseEntity<PageResponse<SuiteResponse>> listSuites(
      @PathVariable String projectId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String parentId,
      Authentication auth) {
    AppUser user = currentUserResolver.resolve(auth);
    return ResponseEntity.ok(service.listSuites(user, projectId, page, size, parentId));
  }

  @GetMapping("/test-cases")
  public ResponseEntity<PageResponse<CaseResponse>> listCases(
      @PathVariable String projectId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String suiteId,
      @RequestParam(required = false) Boolean includeDescendants,
      @RequestParam(required = false) String module,
      @RequestParam(required = false) String query,
      @RequestParam(required = false) List<String> tags,
      @RequestParam(required = false) String tagsMatch,
      @RequestParam(required = false) String priority,
      @RequestParam(required = false) String lifecycleStatus,
      @RequestParam(required = false) String sourceType,
      Authentication auth) {
    AppUser user = currentUserResolver.resolve(auth);
    SelectionCreateRequest.CaseFilterRequest filter =
        new SelectionCreateRequest.CaseFilterRequest(
            suiteId,
            includeDescendants,
            module,
            query,
            tags,
            tagsMatch,
            priority,
            lifecycleStatus,
            sourceType);
    return ResponseEntity.ok(service.listCases(user, projectId, filter, sort, page, size));
  }

  @PostMapping("/test-cases")
  public ResponseEntity<CaseResponse> createCase(
      @PathVariable String projectId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody CreateCaseRequest request,
      Authentication auth) {
    AppUser user = currentUserResolver.resolve(auth);
    CaseResponse created = service.createCase(user, projectId, idempotencyKey, request);
    return ResponseEntity.created(
            URI.create("/api/v2/projects/" + projectId + "/test-cases/" + created.id()))
        .body(created);
  }

  @GetMapping("/test-cases/{caseId}")
  public ResponseEntity<CaseResponse> getCase(
      @PathVariable String projectId, @PathVariable String caseId, Authentication auth) {
    AppUser user = currentUserResolver.resolve(auth);
    return ResponseEntity.ok(service.getCase(user, projectId, caseId));
  }

  @PatchMapping("/test-cases/{caseId}")
  public ResponseEntity<CaseResponse> updateCase(
      @PathVariable String projectId,
      @PathVariable String caseId,
      @RequestBody PatchCaseRequest request,
      Authentication auth) {
    AppUser user = currentUserResolver.resolve(auth);
    return ResponseEntity.ok(service.updateCase(user, projectId, caseId, request));
  }

  @PostMapping("/case-selections")
  public ResponseEntity<SelectionResponse> createSelection(
      @PathVariable String projectId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody SelectionCreateRequest request,
      Authentication auth) {
    AppUser user = currentUserResolver.resolve(auth);
    SelectionResponse selection = service.createSelection(user, projectId, idempotencyKey, request);
    return ResponseEntity.created(
            URI.create("/api/v2/projects/" + projectId + "/case-selections/" + selection.id()))
        .body(selection);
  }
}
