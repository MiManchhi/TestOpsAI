package io.testopsai.server.modules.project;

import io.testopsai.server.platform.security.CurrentUserResolver;
import io.testopsai.server.platform.web.PageResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v3/projects")
class ProjectV3Controller {
  private final WorkspaceProjectService service;
  private final CurrentUserResolver users;

  ProjectV3Controller(WorkspaceProjectService service, CurrentUserResolver users) {
    this.service = service;
    this.users = users;
  }

  @GetMapping
  PageResponse<WorkspaceProject> myProjects(
      Authentication authentication,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.projects(users.resolve(authentication), false, page, size);
  }

  @GetMapping("/{projectId}")
  WorkspaceProject readProject(Authentication authentication, @PathVariable long projectId) {
    return service.read(users.resolve(authentication), projectId, false);
  }

  @PatchMapping("/{projectId}")
  WorkspaceProject editProject(
      Authentication authentication,
      @PathVariable long projectId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody WorkspaceProjectService.ProjectEdit body) {
    return service.edit(users.resolve(authentication), projectId, key, body);
  }

  @GetMapping("/{projectId}/member-candidates")
  PageResponse<WorkspaceProject.Person> memberCandidates(
      Authentication authentication,
      @PathVariable long projectId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String q) {
    return service.candidates(users.resolve(authentication), projectId, false, page, size, q);
  }
}
