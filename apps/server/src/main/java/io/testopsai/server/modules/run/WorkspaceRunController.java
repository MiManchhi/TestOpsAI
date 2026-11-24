package io.testopsai.server.modules.run;

import io.testopsai.server.platform.security.CurrentUserResolver;
import io.testopsai.server.platform.web.PageResponse;
import java.nio.charset.StandardCharsets;
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
@RequestMapping("/api/v3/projects/{projectId}/runs")
class WorkspaceRunController {
  private final WorkspaceRunService runs;
  private final CurrentUserResolver users;

  WorkspaceRunController(WorkspaceRunService runs, CurrentUserResolver users) {
    this.runs = runs;
    this.users = users;
  }

  @GetMapping
  PageResponse<WorkspaceRunService.Run> list(
      Authentication auth,
      @PathVariable long projectId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String q) {
    return runs.list(users.resolve(auth), projectId, page, size, q);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  WorkspaceRunService.Run register(
      Authentication auth,
      @PathVariable long projectId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody WorkspaceRunService.Registration request) {
    return runs.register(users.resolve(auth), projectId, key, request);
  }

  @GetMapping("/{runId}")
  WorkspaceRunService.Run detail(
      Authentication auth, @PathVariable long projectId, @PathVariable long runId) {
    return runs.detail(users.resolve(auth), projectId, runId);
  }

  @GetMapping("/{runId}/results")
  PageResponse<WorkspaceRunService.Result> results(
      Authentication auth,
      @PathVariable long projectId,
      @PathVariable long runId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return runs.results(users.resolve(auth), projectId, runId, page, size);
  }

  @GetMapping("/{runId}/artifacts")
  PageResponse<WorkspaceRunService.Artifact> artifacts(
      Authentication auth,
      @PathVariable long projectId,
      @PathVariable long runId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return runs.artifacts(users.resolve(auth), projectId, runId, page, size);
  }

  @GetMapping("/{runId}/artifacts/{artifactId}/lines")
  PageResponse<WorkspaceRunService.LogLine> lines(
      Authentication auth,
      @PathVariable long projectId,
      @PathVariable long runId,
      @PathVariable long artifactId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "1") int fromLine) {
    return runs.logLines(users.resolve(auth), projectId, runId, artifactId, page, size, fromLine);
  }

  @GetMapping("/{runId}/artifacts/{artifactId}/content")
  ResponseEntity<Resource> download(
      Authentication auth,
      @PathVariable long projectId,
      @PathVariable long runId,
      @PathVariable long artifactId) {
    var result = runs.download(users.resolve(auth), projectId, runId, artifactId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .contentLength(result.bytes())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(result.name(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .body(result.resource());
  }
}
