package io.testopsai.server.modules.project;

import io.testopsai.server.modules.identity.AppUser;
import io.testopsai.server.platform.web.PageResponse;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {
  private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_-]{1,63}$");

  private final ProjectRepository repository;

  public ProjectService(ProjectRepository repository) {
    this.repository = repository;
  }

  public PageResponse<ProjectResponse> listAuthorized(AppUser user, int page, int size) {
    PageResponse.validatePagination(page, size);
    long total = repository.countAuthorizedProjects(user.id());
    long offset = (long) page * size;
    List<Project> projects =
        total == 0 || offset >= total
            ? List.of()
            : repository.findAuthorizedProjects(user.id(), size, offset);
    List<ProjectResponse> items =
        projects.stream()
            .map(
                p -> {
                  String role =
                      legacyRole(repository.getMemberRole(p.id(), user.id()).orElse("VIEWER"));
                  return ProjectResponse.from(p, role);
                })
            .toList();
    return new PageResponse<>(items, total, page, size);
  }

  @Transactional
  public ProjectResponse create(CreateProjectRequest req, AppUser user) {
    WorkspaceProjectService.requireAdmin(user);
    validateKey(req.key());
    if (repository.existsByKey(req.key())) {
      throw new ProjectKeyDuplicateException(req.key());
    }
    Project p = new Project(null, req.key(), req.name(), req.description(), null);
    repository.insert(p);
    // Re-fetch to get generated id
    Project created =
        repository
            .findByKey(req.key())
            .orElseThrow(() -> new IllegalStateException("Insert failed"));
    repository.setMember(created.id(), user.id(), "OWNER");
    return ProjectResponse.from(created, "OWNER");
  }

  public ProjectResponse getById(String idStr, AppUser user) {
    Long id = parseId(idStr);
    Project p = getProjectOrThrow(id);
    String role = resolveRole(p.id(), user);
    return ProjectResponse.from(p, role);
  }

  @Transactional
  public ProjectResponse update(String idStr, PatchProjectRequest req, AppUser user) {
    Long id = parseId(idStr);
    Project p = getProjectOrThrow(id);
    requireRole(p.id(), user, "OWNER");
    String name = req.name() != null ? req.name() : p.name();
    String description = req.description() != null ? req.description() : p.description();
    Project updated = new Project(p.id(), p.projectKey(), name, description, p.createdAt());
    repository.update(updated);
    return ProjectResponse.from(getProjectOrThrow(id), resolveRole(id, user));
  }

  public List<MemberResponse> listMembers(String idStr, AppUser user) {
    Long id = parseId(idStr);
    getProjectOrThrow(id);
    requireRole(id, user, "OWNER");
    return repository.listMembers(id).stream()
        .map(
            m -> {
              // Username lookup would need UserRepository; for now use userId as username
              // placeholder
              return new MemberResponse(
                  String.valueOf(m.userId()), "user-" + m.userId(), legacyRole(m.role()));
            })
        .toList();
  }

  @Transactional
  public void setMember(String idStr, String userIdStr, SetMemberRequest req, AppUser user) {
    Long projectId = parseId(idStr);
    Long targetUserId = parseId(userIdStr);
    getProjectOrThrow(projectId);
    requireRole(projectId, user, "OWNER");
    if (!"EDITOR".equals(req.role()) && !"VIEWER".equals(req.role())) {
      throw new IllegalArgumentException("Role must be EDITOR or VIEWER");
    }
    if ("OWNER".equals(repository.getMemberRole(projectId, targetUserId).orElse(null))) {
      throw new LastOwnerException(projectId);
    }
    repository.setMember(projectId, targetUserId, req.role());
  }

  @Transactional
  public void removeMember(String idStr, String userIdStr, AppUser user) {
    Long projectId = parseId(idStr);
    Long targetUserId = parseId(userIdStr);
    getProjectOrThrow(projectId);
    requireRole(projectId, user, "OWNER");
    // Check last owner constraint
    Optional<String> targetRole = repository.getMemberRole(projectId, targetUserId);
    if ("OWNER".equals(targetRole.orElse(null)) && repository.countOwners(projectId) <= 1) {
      throw new LastOwnerException(projectId);
    }
    repository.removeMember(projectId, targetUserId);
  }

  public Project getProjectOrThrow(Long id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ProjectNotFoundException(String.valueOf(id)));
  }

  public String resolveRole(Long projectId, AppUser user) {
    return repository
        .getMemberRole(projectId, user.id())
        .map(ProjectService::legacyRole)
        .orElseThrow(() -> new AccessDeniedException("Not a member of project " + projectId));
  }

  public void requireRole(Long projectId, AppUser user, String minRole) {
    String role =
        repository
            .getMemberRole(projectId, user.id())
            .orElseThrow(() -> new AccessDeniedException("Not a member of project " + projectId));
    if (!hasAtLeast(role, minRole)) {
      throw new AccessDeniedException(
          "Requires " + minRole + " role, has " + role + " in project " + projectId);
    }
  }

  public void requireMembership(Long projectId, AppUser user) {
    if (!repository.isMember(projectId, user.id())) {
      throw new AccessDeniedException("Not a member of project " + projectId);
    }
  }

  private static String legacyRole(String role) {
    return "TEST".equals(role) || "DEV".equals(role) ? "EDITOR" : role;
  }

  private boolean hasAtLeast(String role, String minRole) {
    int roleLevel =
        switch (role) {
          case "OWNER" -> 3;
          case "EDITOR", "TEST", "DEV" -> 2;
          case "VIEWER" -> 1;
          default -> 0;
        };
    int minLevel =
        switch (minRole) {
          case "OWNER" -> 3;
          case "EDITOR" -> 2;
          case "VIEWER" -> 1;
          default -> 0;
        };
    return roleLevel >= minLevel;
  }

  private void validateKey(String key) {
    if (key == null || !KEY_PATTERN.matcher(key).matches()) {
      throw new IllegalArgumentException("Project key must match ^[A-Z][A-Z0-9_-]{1,63}$: " + key);
    }
  }

  private Long parseId(String idStr) {
    try {
      return Long.parseLong(idStr);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid resource ID: " + idStr);
    }
  }
}
