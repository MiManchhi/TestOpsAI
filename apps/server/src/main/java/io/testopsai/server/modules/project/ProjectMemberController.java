package io.testopsai.server.modules.project;

import io.testopsai.server.platform.security.CurrentUserResolver;
import io.testopsai.server.platform.web.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v3/projects/{projectId}/members")
class ProjectMemberController {
  private final WorkspaceProjectService service;
  private final CurrentUserResolver users;

  ProjectMemberController(WorkspaceProjectService service, CurrentUserResolver users) {
    this.service = service;
    this.users = users;
  }

  @GetMapping
  PageResponse<WorkspaceProject.Member> projectMembers(
      Authentication authentication,
      @PathVariable long projectId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.members(users.resolve(authentication), projectId, page, size);
  }

  @PutMapping("/{userId}")
  WorkspaceProject.Member saveMember(
      Authentication authentication,
      @PathVariable long projectId,
      @PathVariable long userId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody WorkspaceProjectService.MemberWrite body) {
    return service.saveMember(users.resolve(authentication), projectId, userId, key, body);
  }

  @DeleteMapping("/{userId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void removeMember(
      Authentication authentication,
      @PathVariable long projectId,
      @PathVariable long userId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestParam long revision) {
    service.removeMember(users.resolve(authentication), projectId, userId, revision, key);
  }
}
