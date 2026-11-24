package io.testopsai.server.modules.knowledge;

import io.testopsai.server.platform.security.CurrentUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v3/projects/{projectId}/attachments")
class AttachmentController {
  private final WorkspaceAttachmentService service;
  private final CurrentUserResolver users;

  AttachmentController(WorkspaceAttachmentService service, CurrentUserResolver users) {
    this.service = service;
    this.users = users;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  WorkspaceAttachmentService.Attachment uploadPrivateFile(
      Authentication auth,
      @PathVariable long projectId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestParam("file") MultipartFile file) {
    return service.upload(users.resolve(auth), projectId, file, key);
  }
}
