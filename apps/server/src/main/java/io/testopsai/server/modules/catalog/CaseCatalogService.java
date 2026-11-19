package io.testopsai.server.modules.catalog;

import io.testopsai.server.modules.identity.AppUser;
import io.testopsai.server.modules.project.ProjectService;
import io.testopsai.server.platform.idempotency.IdempotencyService;
import io.testopsai.server.platform.web.PageResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Project-scoped Robot/manual case catalog. Every read and write is resolved through the project
 * aggregate so that cross-project access fails as "not found"; role checks follow the frozen
 * contract (VIEWER reads, EDITOR writes).
 */
@Service
public class CaseCatalogService {
  static final int MAX_SELECTION = 1000;
  private static final Duration SELECTION_TTL = Duration.ofHours(24);
  private static final Set<String> PRIORITIES = Set.of("P0", "P1", "P2", "P3");
  private static final Set<String> LIFECYCLE_STATUSES = Set.of("ACTIVE", "ARCHIVED");
  private static final Set<String> SOURCE_TYPES = Set.of("ROBOT", "MANUAL");
  private static final Set<String> TAG_MATCH_MODES = Set.of("ALL", "ANY");
  private static final Set<String> SORTS =
      Set.of("title,asc", "title,desc", "updatedAt,asc", "updatedAt,desc");
  private static final String DEFAULT_SORT = "title,asc";
  private static final String DEFAULT_LIFECYCLE = "ACTIVE";
  private static final String DEFAULT_TAGS_MATCH = "ALL";

  private final ProjectService projectService;
  private final SuiteRepository suites;
  private final ProjectCaseRepository cases;
  private final CaseSelectionRepository selections;
  private final IdempotencyService idempotency;

  public CaseCatalogService(
      ProjectService projectService,
      SuiteRepository suites,
      ProjectCaseRepository cases,
      CaseSelectionRepository selections,
      IdempotencyService idempotency) {
    this.projectService = projectService;
    this.suites = suites;
    this.cases = cases;
    this.selections = selections;
    this.idempotency = idempotency;
  }

  public PageResponse<SuiteResponse> listSuites(
      AppUser user, String projectIdRaw, int page, int size, String parentIdRaw) {
    Long projectId = requireProject(projectIdRaw);
    projectService.requireMembership(projectId, user);
    PageResponse.validatePagination(page, size);
    Long parentId = parentIdRaw == null ? null : parseId(parentIdRaw);
    if (parentId != null && suites.findById(projectId, parentId).isEmpty()) {
      throw new SuiteNotFoundException(parentIdRaw);
    }
    long total = suites.count(projectId, parentId);
    long offset = (long) page * size;
    List<SuiteResponse> items =
        total == 0 || offset >= total
            ? List.of()
            : suites.findPage(projectId, parentId, size, offset).stream()
                .map(SuiteResponse::from)
                .toList();
    return new PageResponse<>(items, total, page, size);
  }

  public PageResponse<CaseResponse> listCases(
      AppUser user,
      String projectIdRaw,
      SelectionCreateRequest.CaseFilterRequest requestedFilter,
      String sortRaw,
      int page,
      int size) {
    Long projectId = requireProject(projectIdRaw);
    projectService.requireMembership(projectId, user);
    PageResponse.validatePagination(page, size);
    CaseFilter filter = normalizeFilter(projectId, requestedFilter);
    String sort = normalizeSort(sortRaw);
    List<Long> suiteIds = resolveSuiteScope(projectId, filter);
    long total = cases.count(projectId, filter, suiteIds);
    long offset = (long) page * size;
    List<CaseResponse> items =
        total == 0 || offset >= total
            ? List.of()
            : cases.findPage(projectId, filter, suiteIds, sort, size, offset).stream()
                .map(CaseResponse::from)
                .toList();
    return new PageResponse<>(items, total, page, size);
  }

  public CaseResponse getCase(AppUser user, String projectIdRaw, String caseIdRaw) {
    Long projectId = requireProject(projectIdRaw);
    projectService.requireMembership(projectId, user);
    Long caseId = parseId(caseIdRaw);
    return cases
        .findById(projectId, caseId)
        .map(CaseResponse::from)
        .orElseThrow(() -> new CaseNotFoundException(caseIdRaw));
  }

  @Transactional
  public CaseResponse createCase(
      AppUser user, String projectIdRaw, String idempotencyKey, CreateCaseRequest request) {
    Long projectId = requireProject(projectIdRaw);
    projectService.requireRole(projectId, user, "EDITOR");
    UUID key = requireIdempotencyKey(idempotencyKey);
    String title = requireText(request.title(), "title", 256);
    String module = requireText(request.module(), "module", 128);
    String priority = requireEnum(request.priority(), PRIORITIES, "priority");
    String expectedResult = requireOptionalText(request.expectedResult(), "expectedResult", 4000);
    String documentation = requireOptionalText(request.documentation(), "documentation", 10000);
    List<String> tags = normalizeTags(request.tags());

    String requestHash =
        sha256Hex(
            String.join(
                "\u0001",
                title,
                module,
                priority,
                documentation,
                expectedResult,
                String.join(",", tags.stream().sorted().toList())));

    Optional<IdempotencyService.StoredResult> replay =
        idempotency.find(user.id(), "createTestCase", key.toString(), requestHash);
    if (replay.isPresent() && "CASE".equals(replay.get().resourceType())) {
      Long replayId = parseId(replay.get().resourceId());
      return cases
          .findById(projectId, replayId)
          .map(CaseResponse::from)
          .orElseThrow(() -> new CaseNotFoundException(replay.get().resourceId()));
    }

    String stableKey = "manual:" + UUID.randomUUID();
    Long caseId =
        cases.insert(projectId, stableKey, title, module, priority, documentation, expectedResult);
    cases.replaceTags(caseId, tags);
    idempotency.store(
        user.id(), "createTestCase", key.toString(), requestHash, "CASE", String.valueOf(caseId));
    return cases
        .findById(projectId, caseId)
        .map(CaseResponse::from)
        .orElseThrow(() -> new IllegalStateException("Created case not readable"));
  }

  @Transactional
  public CaseResponse updateCase(
      AppUser user, String projectIdRaw, String caseIdRaw, PatchCaseRequest request) {
    Long projectId = requireProject(projectIdRaw);
    projectService.requireRole(projectId, user, "EDITOR");
    Long caseId = parseId(caseIdRaw);
    ProjectCase existing =
        cases.findById(projectId, caseId).orElseThrow(() -> new CaseNotFoundException(caseIdRaw));
    if ("ROBOT".equals(existing.sourceType())) {
      throw new RobotCaseReadOnlyException(caseIdRaw);
    }
    Integer expectedVersion = request.version();
    if (expectedVersion == null || expectedVersion < 1) {
      throw new IllegalArgumentException("version is required and must be >= 1");
    }
    String title =
        request.title() == null ? existing.title() : requireText(request.title(), "title", 256);
    String module =
        request.module() == null
            ? existing.module()
            : requireOptionalText(request.module(), "module", 128);
    String priority =
        request.priority() == null
            ? existing.priority()
            : requireEnum(request.priority(), PRIORITIES, "priority");
    String lifecycleStatus =
        request.lifecycleStatus() == null
            ? existing.lifecycleStatus()
            : requireEnum(request.lifecycleStatus(), LIFECYCLE_STATUSES, "lifecycleStatus");
    String documentation =
        request.documentation() == null
            ? existing.documentation()
            : requireOptionalText(request.documentation(), "documentation", 10000);
    String expectedResult =
        request.expectedResult() == null
            ? existing.expectedResult()
            : requireOptionalText(request.expectedResult(), "expectedResult", 4000);

    int updated =
        cases.updateWithVersionCas(
            projectId,
            caseId,
            expectedVersion,
            title,
            module,
            priority,
            lifecycleStatus,
            documentation,
            expectedResult);
    if (updated == 0) {
      throw new VersionConflictException(caseIdRaw, expectedVersion);
    }
    if (request.tags() != null) {
      cases.replaceTags(caseId, normalizeTags(request.tags()));
    }
    return cases
        .findById(projectId, caseId)
        .map(CaseResponse::from)
        .orElseThrow(() -> new CaseNotFoundException(caseIdRaw));
  }

  @Transactional
  public SelectionResponse createSelection(
      AppUser user, String projectIdRaw, String idempotencyKey, SelectionCreateRequest request) {
    Long projectId = requireProject(projectIdRaw);
    projectService.requireMembership(projectId, user);
    UUID key = requireIdempotencyKey(idempotencyKey);

    String mode = request.mode();
    if (!"EXPLICIT".equals(mode) && !"FILTER".equals(mode)) {
      throw new IllegalArgumentException("mode must be EXPLICIT or FILTER");
    }
    boolean hasCaseIds = request.caseIds() != null;
    boolean hasFilter = request.filter() != null;
    if ("EXPLICIT".equals(mode)) {
      if (!hasCaseIds || request.caseIds().isEmpty()) {
        throw new IllegalArgumentException("EXPLICIT selection requires a non-empty caseIds");
      }
      if (hasFilter) {
        throw new IllegalArgumentException("EXPLICIT selection must not include filter");
      }
      if (request.excludedCaseIds() != null && !request.excludedCaseIds().isEmpty()) {
        throw new IllegalArgumentException("EXPLICIT selection must not include excludedCaseIds");
      }
    } else if (!hasFilter) {
      throw new IllegalArgumentException("FILTER selection requires filter");
    } else if (hasCaseIds) {
      throw new IllegalArgumentException("FILTER selection must not include caseIds");
    }

    List<String> excludedRaw =
        request.excludedCaseIds() == null ? List.of() : request.excludedCaseIds();
    List<Long> excluded = parseIdList(excludedRaw, "excludedCaseIds");

    String requestHash;
    List<CaseSelection.CaseRef> refs;
    String filterJson = null;

    if ("EXPLICIT".equals(mode)) {
      List<Long> caseIds = parseIdList(request.caseIds(), "caseIds");
      if (caseIds.size() != new LinkedHashSet<>(caseIds).size()) {
        throw new IllegalArgumentException("caseIds must not contain duplicates");
      }
      requestHash = sha256Hex("EXPLICIT\u0001" + joinedIds(caseIds));
      List<CaseSelection.CaseRef> found = cases.findRefsByIds(projectId, caseIds);
      if (found.size() != caseIds.size()) {
        Set<Long> present =
            new LinkedHashSet<>(found.stream().map(CaseSelection.CaseRef::caseId).toList());
        Long missing =
            caseIds.stream().filter(id -> !present.contains(id)).findFirst().orElse(caseIds.get(0));
        throw new CaseNotFoundException(String.valueOf(missing));
      }
      refs = found;
    } else {
      CaseFilter filter = normalizeFilter(projectId, request.filter());
      List<Long> suiteIds = resolveSuiteScope(projectId, filter);
      requestHash =
          sha256Hex("FILTER\u0001" + canonicalFilter(filter) + "\u0001" + joinedIds(excluded));
      filterJson = canonicalFilter(filter);
      List<CaseSelection.CaseRef> found =
          cases.findRefs(projectId, filter, suiteIds, excluded, MAX_SELECTION + 1);
      if (found.size() > MAX_SELECTION) {
        throw new SelectionTooLargeException(found.size());
      }
      refs = found;
    }

    Optional<IdempotencyService.StoredResult> replay =
        idempotency.find(user.id(), "createCaseSelection", key.toString(), requestHash);
    if (replay.isPresent() && "SELECTION".equals(replay.get().resourceType())) {
      Optional<CaseSelection> stored = selections.findById(replay.get().resourceId());
      if (stored.isPresent()) return SelectionResponse.from(stored.get());
    }

    String selectionId = UUID.randomUUID().toString();
    Instant expiresAt = Instant.now().plus(SELECTION_TTL);
    String digest = digestOf(refs);
    selections.insert(
        selectionId, projectId, user.id(), mode, refs.size(), digest, filterJson, expiresAt);
    selections.insertItems(selectionId, refs);
    idempotency.store(
        user.id(), "createCaseSelection", key.toString(), requestHash, "SELECTION", selectionId);
    return new SelectionResponse(
        selectionId,
        String.valueOf(projectId),
        refs.size(),
        refs.stream()
            .map(
                r ->
                    new SelectionResponse.CaseVersionResponse(
                        String.valueOf(r.caseId()), r.version()))
            .toList(),
        digest,
        expiresAt);
  }

  private Long requireProject(String projectIdRaw) {
    Long projectId = parseId(projectIdRaw);
    projectService.getProjectOrThrow(projectId);
    return projectId;
  }

  private CaseFilter normalizeFilter(
      Long projectId, SelectionCreateRequest.CaseFilterRequest requested) {
    if (requested == null) {
      return new CaseFilter(
          null, true, null, null, List.of(), DEFAULT_TAGS_MATCH, null, DEFAULT_LIFECYCLE, null);
    }
    Long suiteId = requested.suiteId() == null ? null : parseId(requested.suiteId());
    if (suiteId != null && suites.findById(projectId, suiteId).isEmpty()) {
      throw new SuiteNotFoundException(requested.suiteId());
    }
    String priority =
        requested.priority() == null
            ? null
            : requireEnum(requested.priority(), PRIORITIES, "priority");
    String lifecycleStatus =
        requested.lifecycleStatus() == null
            ? DEFAULT_LIFECYCLE
            : requireEnum(requested.lifecycleStatus(), LIFECYCLE_STATUSES, "lifecycleStatus");
    String sourceType =
        requested.sourceType() == null
            ? null
            : requireEnum(requested.sourceType(), SOURCE_TYPES, "sourceType");
    String tagsMatch =
        requested.tagsMatch() == null
            ? DEFAULT_TAGS_MATCH
            : requireEnum(requested.tagsMatch(), TAG_MATCH_MODES, "tagsMatch");
    return new CaseFilter(
        suiteId,
        requested.includeDescendants() == null || requested.includeDescendants(),
        trimToNull(requested.module()),
        trimToNull(requested.query()),
        normalizeTags(requested.tags()),
        tagsMatch,
        priority,
        lifecycleStatus,
        sourceType);
  }

  private List<Long> resolveSuiteScope(Long projectId, CaseFilter filter) {
    if (filter.suiteId() == null) return null;
    if (!filter.includeDescendants()) return List.of(filter.suiteId());
    return suites.findSubtreeIds(projectId, filter.suiteId());
  }

  private String normalizeSort(String sortRaw) {
    if (sortRaw == null || sortRaw.isBlank()) return DEFAULT_SORT;
    String normalized = sortRaw.trim();
    if (!SORTS.contains(normalized)) {
      throw new IllegalArgumentException(
          "sort must be one of title,asc | title,desc | updatedAt,asc | updatedAt,desc");
    }
    return normalized;
  }

  private UUID requireIdempotencyKey(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("Idempotency-Key header is required");
    }
    try {
      return UUID.fromString(raw.trim());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Idempotency-Key must be a UUID");
    }
  }

  private List<String> normalizeTags(List<String> tags) {
    if (tags == null) return List.of();
    LinkedHashSet<String> unique = new LinkedHashSet<>();
    for (String raw : tags) {
      if (raw == null) continue;
      String tag = raw.trim();
      if (tag.isEmpty()) continue;
      if (tag.length() > 64)
        throw new IllegalArgumentException("tag must be at most 64 characters");
      unique.add(tag);
    }
    if (unique.size() > 30) throw new IllegalArgumentException("at most 30 tags are allowed");
    return List.copyOf(unique);
  }

  private List<Long> parseIdList(List<String> raw, String field) {
    List<Long> ids = new ArrayList<>();
    for (String value : raw) {
      ids.add(parseId(value));
    }
    return ids;
  }

  private Long parseId(String raw) {
    if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Resource id is required");
    try {
      long id = Long.parseLong(raw.trim());
      if (id < 1) throw new IllegalArgumentException("Invalid resource ID: " + raw);
      return id;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid resource ID: " + raw);
    }
  }

  private String requireText(String value, String field, int maxLength) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    if (value.length() > maxLength) {
      throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters");
    }
    return value;
  }

  private String requireOptionalText(String value, String field, int maxLength) {
    if (value == null) return "";
    if (value.length() > maxLength) {
      throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters");
    }
    return value;
  }

  private String requireEnum(String value, Set<String> allowed, String field) {
    if (value == null) throw new IllegalArgumentException(field + " is required");
    String normalized = value.trim();
    if (!allowed.contains(normalized)) {
      throw new IllegalArgumentException(field + " must be one of " + String.join(", ", allowed));
    }
    return normalized;
  }

  private String trimToNull(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private String joinedIds(List<Long> ids) {
    return ids.stream().map(String::valueOf).sorted().reduce((a, b) -> a + "," + b).orElse("");
  }

  private String canonicalFilter(CaseFilter filter) {
    return String.join(
        "\u0001",
        filter.suiteId() == null ? "" : String.valueOf(filter.suiteId()),
        String.valueOf(filter.includeDescendants()),
        nullToEmpty(filter.module()),
        nullToEmpty(filter.query()),
        String.join(",", filter.tags()),
        filter.tagsMatch(),
        nullToEmpty(filter.priority()),
        filter.lifecycleStatus(),
        nullToEmpty(filter.sourceType()));
  }

  private String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private String digestOf(List<CaseSelection.CaseRef> refs) {
    String canonical =
        refs.stream()
            .map(ref -> ref.caseId() + ":" + ref.version())
            .sorted()
            .reduce((a, b) -> a + "\n" + b)
            .orElse("");
    return sha256Hex(canonical);
  }

  private String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
