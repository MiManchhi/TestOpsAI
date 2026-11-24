package io.testopsai.server.modules.knowledge;

import io.testopsai.server.platform.security.CurrentUserResolver;
import io.testopsai.server.platform.web.PageResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/v3/projects/{projectId}")
class DocumentVersionController {
  private final WorkspaceDocumentService service;
  private final CurrentUserResolver users;
  private final WorkspaceAttachmentService attachments;

  DocumentVersionController(
      WorkspaceDocumentService service,
      CurrentUserResolver users,
      WorkspaceAttachmentService attachments) {
    this.service = service;
    this.users = users;
    this.attachments = attachments;
  }

  @PostMapping("/documents")
  @ResponseStatus(HttpStatus.CREATED)
  WorkspaceDocument submitVersion(
      Authentication auth,
      @PathVariable long projectId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody WorkspaceDocument.Submission body) {
    return service.submit(users.resolve(auth), projectId, key, body);
  }

  @GetMapping("/documents")
  PageResponse<WorkspaceDocument> findDocuments(
      Authentication auth,
      @PathVariable long projectId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String reviewStatus) {
    return service.findDocuments(users.resolve(auth), projectId, page, size, q, reviewStatus);
  }

  @GetMapping("/documents/{documentId}/versions")
  PageResponse<WorkspaceDocument> listVersions(
      Authentication auth,
      @PathVariable long projectId,
      @PathVariable long documentId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.listVersions(users.resolve(auth), projectId, documentId, page, size);
  }

  @GetMapping("/versions/{versionId}")
  WorkspaceDocument getVersion(
      Authentication auth, @PathVariable long projectId, @PathVariable long versionId) {
    return service.version(users.resolve(auth), projectId, versionId);
  }

  @GetMapping("/versions/{versionId}/content")
  WorkspaceDocument.Content readVersion(
      Authentication auth,
      @PathVariable long projectId,
      @PathVariable long versionId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) String locator) {
    return service.read(users.resolve(auth), projectId, versionId, cursor, locator);
  }

  @PostMapping("/versions/{versionId}/review")
  WorkspaceDocument reviewVersion(
      Authentication auth,
      @PathVariable long projectId,
      @PathVariable long versionId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody WorkspaceDocument.Review body) {
    return service.review(users.resolve(auth), projectId, versionId, key, body);
  }

  @PostMapping("/versions/{versionId}/retry")
  @ResponseStatus(HttpStatus.ACCEPTED)
  WorkspaceDocument.Receipt retryIndex(
      Authentication auth,
      @PathVariable long projectId,
      @PathVariable long versionId,
      @RequestHeader("Idempotency-Key") String key) {
    return service.retry(users.resolve(auth), projectId, versionId, key);
  }

  @GetMapping("/versions/{versionId}/original")
  ResponseEntity<Resource> downloadOriginalVersion(
      Authentication auth, @PathVariable long projectId, @PathVariable long versionId) {
    var entry = service.original(users.resolve(auth), projectId, versionId);
    var resource = attachments.object(UUID.fromString(entry.attachmentId()));
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .contentLength(entry.sizeBytes())
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(entry.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .body(resource);
  }
}
