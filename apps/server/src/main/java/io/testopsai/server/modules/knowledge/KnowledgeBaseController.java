package io.testopsai.server.modules.knowledge;

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
@RequestMapping("/api/v1/knowledge-bases")
class KnowledgeBaseController {
  private final KnowledgeBaseService service;
  private final UserRepository userRepository;

  KnowledgeBaseController(KnowledgeBaseService service, UserRepository userRepository) {
    this.service = service;
    this.userRepository = userRepository;
  }

  @GetMapping
  ResponseEntity<PageResponse<KnowledgeBaseResponse>> list(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    PageResponse.validatePagination(page, size);
    return ResponseEntity.ok(service.list(page, size));
  }

  @PostMapping
  ResponseEntity<KnowledgeBaseResponse> create(
      @RequestBody CreateKnowledgeBaseRequest request, Authentication authentication) {
    AppUser user = resolveUser(authentication);
    KnowledgeBase kb = service.create(request, user);
    return ResponseEntity.created(URI.create("/api/v1/knowledge-bases/" + kb.id()))
        .body(KnowledgeBaseResponse.from(kb));
  }

  @GetMapping("/{id}")
  ResponseEntity<KnowledgeBaseResponse> get(@PathVariable Long id, Authentication authentication) {
    AppUser user = resolveUser(authentication);
    KnowledgeBase kb = service.getById(id);
    service.checkReadAccess(kb, user);
    return ResponseEntity.ok(KnowledgeBaseResponse.from(kb));
  }

  @PutMapping("/{id}")
  ResponseEntity<KnowledgeBaseResponse> update(
      @PathVariable Long id,
      @RequestBody UpdateKnowledgeBaseRequest request,
      Authentication authentication) {
    AppUser user = resolveUser(authentication);
    KnowledgeBase kb = service.update(id, request, user);
    return ResponseEntity.ok(KnowledgeBaseResponse.from(kb));
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
