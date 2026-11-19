package io.testopsai.server.modules.project;

import io.testopsai.server.modules.identity.AppUser;
import io.testopsai.server.platform.security.CurrentUserResolver;
import io.testopsai.server.platform.web.PageResponse;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/projects")
public class ProjectController {
  private final ProjectService service;
  private final CurrentUserResolver currentUserResolver;

  public ProjectController(ProjectService service, CurrentUserResolver currentUserResolver) {
    this.service = service;
    this.currentUserResolver = currentUserResolver;
  }

  @GetMapping
  public ResponseEntity<PageResponse<ProjectResponse>> list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Authentication auth) {
    return ResponseEntity.ok(service.listAuthorized(resolveUser(auth), page, size));
  }

  @PostMapping
  public ResponseEntity<ProjectResponse> create(
      @RequestBody CreateProjectRequest req, Authentication auth) {
    ProjectResponse resp = service.create(req, resolveUser(auth));
    return ResponseEntity.created(URI.create("/api/v2/projects/" + resp.id())).body(resp);
  }

  @GetMapping("/{projectId}")
  public ResponseEntity<ProjectResponse> get(@PathVariable String projectId, Authentication auth) {
    return ResponseEntity.ok(service.getById(projectId, resolveUser(auth)));
  }

  @PatchMapping("/{projectId}")
  public ResponseEntity<ProjectResponse> update(
      @PathVariable String projectId, @RequestBody PatchProjectRequest req, Authentication auth) {
    return ResponseEntity.ok(service.update(projectId, req, resolveUser(auth)));
  }

  @GetMapping("/{projectId}/members")
  public ResponseEntity<List<MemberResponse>> listMembers(
      @PathVariable String projectId, Authentication auth) {
    return ResponseEntity.ok(service.listMembers(projectId, resolveUser(auth)));
  }

  @PutMapping("/{projectId}/members/{userId}")
  public ResponseEntity<Void> setMember(
      @PathVariable String projectId,
      @PathVariable String userId,
      @RequestBody SetMemberRequest req,
      Authentication auth) {
    service.setMember(projectId, userId, req, resolveUser(auth));
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{projectId}/members/{userId}")
  public ResponseEntity<Void> removeMember(
      @PathVariable String projectId, @PathVariable String userId, Authentication auth) {
    service.removeMember(projectId, userId, resolveUser(auth));
    return ResponseEntity.noContent().build();
  }

  private AppUser resolveUser(Authentication auth) {
    return currentUserResolver.resolve(auth);
  }
}
