package io.testopsai.server.modules.project;

import io.testopsai.server.platform.security.CurrentUserResolver;
import io.testopsai.server.platform.web.PageResponse;
import java.util.UUID;
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
@RequestMapping("/api/v3/platform/projects/{projectId}/owner-transfers")
class PlatformTransferController {
  private final OwnerTransferService service;
  private final CurrentUserResolver users;

  PlatformTransferController(OwnerTransferService service, CurrentUserResolver users) {
    this.service = service;
    this.users = users;
  }

  @GetMapping
  PageResponse<OwnerTransferService.Handover> adminTransferHistory(
      Authentication auth,
      @PathVariable long projectId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.history(users.resolve(auth), projectId, true, page, size);
  }

  @PostMapping("/proxy")
  @ResponseStatus(HttpStatus.CREATED)
  OwnerTransferService.Handover proxyOwnerTransfer(
      Authentication auth,
      @PathVariable long projectId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody OwnerTransferService.ProxyRequest body) {
    return service.proxy(users.resolve(auth), projectId, key, body);
  }

  @PostMapping("/{handoverId}/decision")
  OwnerTransferService.Handover decideOwnerTransfer(
      Authentication auth,
      @PathVariable long projectId,
      @PathVariable UUID handoverId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody OwnerTransferService.Decision body) {
    return service.decide(users.resolve(auth), projectId, handoverId, key, body);
  }
}
