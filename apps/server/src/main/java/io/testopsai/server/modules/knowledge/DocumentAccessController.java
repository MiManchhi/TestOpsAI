package io.testopsai.server.modules.knowledge;

import io.testopsai.server.platform.security.CurrentUserResolver;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v3/projects/{projectId}/documents/{documentId}/readers")
class DocumentAccessController {
  private final WorkspaceDocumentService service;
  private final CurrentUserResolver users;

  DocumentAccessController(WorkspaceDocumentService service, CurrentUserResolver users) {
    this.service = service;
    this.users = users;
  }

  @GetMapping
  WorkspaceDocument.Readers getReaders(
      Authentication auth, @PathVariable long projectId, @PathVariable long documentId) {
    return service.readers(users.resolve(auth), projectId, documentId);
  }

  @PutMapping
  WorkspaceDocument.Readers setReaders(
      Authentication auth,
      @PathVariable long projectId,
      @PathVariable long documentId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody WorkspaceDocument.Readers body) {
    return service.setReaders(users.resolve(auth), projectId, documentId, key, body);
  }
}
