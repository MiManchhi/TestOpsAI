package io.testopsai.server.modules.testcase;

import io.testopsai.server.modules.identity.AppUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TestCaseService {
  private final TestCaseRepository repository;

  TestCaseService(TestCaseRepository repository) {
    this.repository = repository;
  }

  PageResponse<TestCaseResponse> list(
      String projectKey,
      String module,
      String priority,
      String status,
      String sort,
      int page,
      int size) {
    if (sort != null && !sort.isBlank()) {
      String field = sort.split(",")[0].trim();
      if (!repository.isValidSortField(field)) {
        throw new InvalidSortFieldException(field);
      }
    }
    return repository.findAll(projectKey, module, priority, status, sort, page, size);
  }

  TestCase getById(Long id) {
    return repository.findById(id).orElseThrow(() -> new TestCaseNotFoundException(id));
  }

  @Transactional
  TestCase create(CreateTestCaseRequest request, AppUser currentUser) {
    String priority = normalizePriority(request.priority());
    TestCase tc =
        new TestCase(
            null,
            request.projectKey(),
            request.module(),
            request.title(),
            request.tags(),
            priority,
            request.expectedResult(),
            currentUser.id(),
            "ACTIVE",
            null,
            null);
    repository.insert(tc);
    // Re-fetch to get generated id and timestamps
    PageResponse<TestCaseResponse> latest =
        repository.findAll(null, null, null, "ACTIVE", "createdAt,desc", 0, 1);
    Long newId =
        latest.items().stream()
            .filter(
                r ->
                    r.projectKey().equals(request.projectKey())
                        && r.title().equals(request.title())
                        && r.ownerId().equals(currentUser.id()))
            .findFirst()
            .map(TestCaseResponse::id)
            .orElseThrow();
    return getById(newId);
  }

  @Transactional
  TestCase update(Long id, UpdateTestCaseRequest request, AppUser currentUser) {
    TestCase tc = getById(id);
    checkWriteAccess(tc, currentUser);
    String title = request.title() != null ? request.title() : tc.title();
    String module = request.module() != null ? request.module() : tc.module();
    String tags = request.tags() != null ? request.tags() : tc.tags();
    String priority =
        request.priority() != null ? normalizePriority(request.priority()) : tc.priority();
    String expectedResult =
        request.expectedResult() != null ? request.expectedResult() : tc.expectedResult();
    String status = request.status() != null ? request.status() : tc.status();
    TestCase updated =
        new TestCase(
            tc.id(),
            tc.projectKey(),
            module,
            title,
            tags,
            priority,
            expectedResult,
            tc.ownerId(),
            status,
            tc.createdAt(),
            null);
    repository.update(updated);
    return getById(id);
  }

  @Transactional
  void archive(Long id, AppUser currentUser) {
    TestCase tc = getById(id);
    checkWriteAccess(tc, currentUser);
    TestCase archived =
        new TestCase(
            tc.id(),
            tc.projectKey(),
            tc.module(),
            tc.title(),
            tc.tags(),
            tc.priority(),
            tc.expectedResult(),
            tc.ownerId(),
            "ARCHIVED",
            tc.createdAt(),
            null);
    repository.update(archived);
  }

  private void checkWriteAccess(TestCase tc, AppUser currentUser) {
    if (currentUser.roles().contains("ADMIN")) return;
    if (tc.ownerId().equals(currentUser.id())) return;
    throw new AccessDeniedException("No write access to test case " + tc.id());
  }

  private String normalizePriority(String priority) {
    if (priority == null) return "P2";
    String p = priority.toUpperCase();
    if (!p.equals("P0") && !p.equals("P1") && !p.equals("P2") && !p.equals("P3")) {
      throw new IllegalArgumentException("Invalid priority: " + priority);
    }
    return p;
  }
}
