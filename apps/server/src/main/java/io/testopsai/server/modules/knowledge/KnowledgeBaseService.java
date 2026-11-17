package io.testopsai.server.modules.knowledge;

import io.testopsai.server.modules.identity.AppUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class KnowledgeBaseService {
  private final KnowledgeBaseRepository repository;

  KnowledgeBaseService(KnowledgeBaseRepository repository) {
    this.repository = repository;
  }

  PageResponse<KnowledgeBaseResponse> list(int page, int size) {
    return repository.findAll(page, size);
  }

  KnowledgeBase getById(Long id) {
    return repository.findById(id).orElseThrow(() -> new KnowledgeBaseNotFoundException(id));
  }

  @Transactional
  KnowledgeBase create(CreateKnowledgeBaseRequest request, AppUser currentUser) {
    String visibility = normalizeVisibility(request.visibility());
    if (repository.existsByOwnerAndName(currentUser.id(), request.name())) {
      throw new KnowledgeBaseNameDuplicateException(currentUser.id(), request.name());
    }
    KnowledgeBase kb =
        new KnowledgeBase(
            null,
            request.name(),
            request.description(),
            currentUser.id(),
            visibility,
            "ACTIVE",
            null,
            null);
    repository.insert(kb);
    // Re-fetch to get generated id and timestamps
    return repository
        .findById(
            repository.findAll(0, 1).items().stream()
                .filter(
                    r -> r.name().equals(request.name()) && r.ownerId().equals(currentUser.id()))
                .findFirst()
                .orElseThrow()
                .id())
        .orElseThrow();
  }

  @Transactional
  KnowledgeBase update(Long id, UpdateKnowledgeBaseRequest request, AppUser currentUser) {
    KnowledgeBase kb = getById(id);
    checkWriteAccess(kb, currentUser);
    String name = request.name() != null ? request.name() : kb.name();
    String description = request.description() != null ? request.description() : kb.description();
    String visibility =
        request.visibility() != null ? normalizeVisibility(request.visibility()) : kb.visibility();
    String status = request.status() != null ? request.status() : kb.status();
    if (!name.equals(kb.name())
        && repository.existsByOwnerAndNameExcluding(kb.ownerId(), name, id)) {
      throw new KnowledgeBaseNameDuplicateException(kb.ownerId(), name);
    }
    KnowledgeBase updated =
        new KnowledgeBase(
            kb.id(), name, description, kb.ownerId(), visibility, status, kb.createdAt(), null);
    repository.update(updated);
    return getById(id);
  }

  @Transactional
  void archive(Long id, AppUser currentUser) {
    KnowledgeBase kb = getById(id);
    checkWriteAccess(kb, currentUser);
    KnowledgeBase archived =
        new KnowledgeBase(
            kb.id(),
            kb.name(),
            kb.description(),
            kb.ownerId(),
            kb.visibility(),
            "ARCHIVED",
            kb.createdAt(),
            null);
    repository.update(archived);
  }

  void checkReadAccess(KnowledgeBase kb, AppUser currentUser) {
    if (currentUser.roles().contains("ADMIN")) return;
    if (kb.ownerId().equals(currentUser.id())) return;
    if ("PUBLIC".equals(kb.visibility())) return;
    if ("SHARED".equals(kb.visibility())
        && repository.hasMembership(kb.id(), currentUser.id(), "READ")) return;
    throw new AccessDeniedException("No read access to knowledge base " + kb.id());
  }

  void checkWriteAccess(KnowledgeBase kb, AppUser currentUser) {
    if (currentUser.roles().contains("ADMIN")) return;
    if (kb.ownerId().equals(currentUser.id())) return;
    if ("SHARED".equals(kb.visibility())
        && repository.hasMembership(kb.id(), currentUser.id(), "WRITE")) return;
    throw new AccessDeniedException("No write access to knowledge base " + kb.id());
  }

  private String normalizeVisibility(String visibility) {
    if (visibility == null) return "PRIVATE";
    String v = visibility.toUpperCase();
    if (!v.equals("PRIVATE") && !v.equals("SHARED") && !v.equals("PUBLIC")) {
      throw new IllegalArgumentException("Invalid visibility: " + visibility);
    }
    return v;
  }
}
