package io.testopsai.server.modules.project;

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
@RequestMapping("/api/v3/platform")
class PlatformProjectController {
  private final WorkspaceProjectService service;
  private final CurrentUserResolver users;

  PlatformProjectController(WorkspaceProjectService service, CurrentUserResolver users) {
    this.service = service;
    this.users = users;
  }

  @GetMapping("/projects")
  PageResponse<WorkspaceProject> platformProjects(
      Authentication authentication,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.projects(users.resolve(authentication), true, page, size);
  }

  @GetMapping("/projects/{projectId}")
  WorkspaceProject platformDetail(Authentication authentication, @PathVariable long projectId) {
    return service.read(users.resolve(authentication), projectId, true);
  }

  @PostMapping("/projects")
  @ResponseStatus(HttpStatus.CREATED)
  WorkspaceProject platformCreate(
      Authentication authentication,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody WorkspaceProjectService.ProjectCreate body) {
    return service.create(users.resolve(authentication), key, body);
  }

  @GetMapping("/users")
  PageResponse<WorkspaceProject.Person> platformUsers(
      Authentication authentication,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String q) {
    return service.candidates(users.resolve(authentication), null, false, page, size, q);
  }

  @GetMapping("/projects/{projectId}/successors")
  PageResponse<WorkspaceProject.Person> eligibleOwners(
      Authentication authentication,
      @PathVariable long projectId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String q) {
    return service.candidates(users.resolve(authentication), projectId, true, page, size, q);
  }
}
