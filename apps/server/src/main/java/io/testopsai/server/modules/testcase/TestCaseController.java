package io.testopsai.server.modules.testcase;

import io.testopsai.server.modules.identity.AppUser;
import io.testopsai.server.modules.identity.UserRepository;
import io.testopsai.server.platform.web.PageResponse;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/test-cases")
class TestCaseController {
  private final TestCaseService service;
  private final UserRepository userRepository;

  TestCaseController(TestCaseService service, UserRepository userRepository) {
    this.service = service;
    this.userRepository = userRepository;
  }

  @GetMapping
  ResponseEntity<PageResponse<TestCaseResponse>> list(
      @RequestParam(required = false) String projectKey,
      @RequestParam(required = false) String module,
      @RequestParam(required = false) String priority,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String sort,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    PageResponse.validatePagination(page, size);
    return ResponseEntity.ok(service.list(projectKey, module, priority, status, sort, page, size));
  }

  @PostMapping
  ResponseEntity<TestCaseResponse> create(
      @RequestBody CreateTestCaseRequest request, Authentication authentication) {
    AppUser user = resolveUser(authentication);
    TestCase tc = service.create(request, user);
    return ResponseEntity.created(URI.create("/api/v1/test-cases/" + tc.id()))
        .body(TestCaseResponse.from(tc));
  }

  @GetMapping("/{id}")
  ResponseEntity<TestCaseResponse> get(@PathVariable Long id) {
    TestCase tc = service.getById(id);
    return ResponseEntity.ok(TestCaseResponse.from(tc));
  }

  @PutMapping("/{id}")
  ResponseEntity<TestCaseResponse> update(
      @PathVariable Long id,
      @RequestBody UpdateTestCaseRequest request,
      Authentication authentication) {
    AppUser user = resolveUser(authentication);
    TestCase tc = service.update(id, request, user);
    return ResponseEntity.ok(TestCaseResponse.from(tc));
  }

  @DeleteMapping("/{id}")
  ResponseEntity<Void> archive(@PathVariable Long id, Authentication authentication) {
    AppUser user = resolveUser(authentication);
    service.archive(id, user);
    return ResponseEntity.noContent().build();
  }

  private AppUser resolveUser(Authentication authentication) {
    return userRepository
        .findByUsername(authentication.getName())
        .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database"));
  }
}
