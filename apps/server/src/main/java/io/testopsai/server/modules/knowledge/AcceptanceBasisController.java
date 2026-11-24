package io.testopsai.server.modules.knowledge;

import io.testopsai.server.platform.security.CurrentUserResolver;
import io.testopsai.server.platform.web.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v3/projects/{projectId}/bases")
class AcceptanceBasisController {
  private final WorkspaceBasisService service;
  private final CurrentUserResolver users;

  AcceptanceBasisController(WorkspaceBasisService service, CurrentUserResolver users) {
    this.service = service;
    this.users = users;
  }

  @GetMapping
  PageResponse<WorkspaceDocument.Basis> listBases(
      Authentication auth,
      @PathVariable long projectId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(users.resolve(auth), projectId, page, size);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  WorkspaceDocument.Basis setBasis(
      Authentication auth,
      @PathVariable long projectId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody WorkspaceDocument.BasisSet body) {
    return service.publish(users.resolve(auth), projectId, key, body);
  }
}
