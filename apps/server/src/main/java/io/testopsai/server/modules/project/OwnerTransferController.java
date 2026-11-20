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
@RequestMapping("/api/v3/projects/{projectId}/owner-transfers")
class OwnerTransferController {
  private final OwnerTransferService service;
  private final CurrentUserResolver users;

  OwnerTransferController(OwnerTransferService service, CurrentUserResolver users) {
    this.service = service;
    this.users = users;
  }

  @GetMapping
  PageResponse<OwnerTransferService.Handover> ownerTransferHistory(
      Authentication auth,
      @PathVariable long projectId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.history(users.resolve(auth), projectId, false, page, size);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  OwnerTransferService.Handover requestOwnerTransfer(
      Authentication auth,
      @PathVariable long projectId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody OwnerTransferService.TransferRequest body) {
    return service.request(users.resolve(auth), projectId, key, body);
  }
}
