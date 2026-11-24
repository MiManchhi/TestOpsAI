package io.testopsai.server.modules.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class WorkspaceIndexClient {
  private static final String PARSER = "knowledge-v2";
  private final String base;
  private final String profile;
  private final WorkspaceServiceIdentity tokens;
  private final ObjectMapper json;
  private final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  WorkspaceIndexClient(
      @Value("${testopsai.workspace.ai-url}") String endpoint,
      @Value("${testopsai.workspace.embedding-profile}") String profile,
      WorkspaceServiceIdentity tokens,
      ObjectMapper json) {
    this.base = endpoint.replaceFirst("/+$", "");
    if (!base.isEmpty()) {
      URI uri = URI.create(base);
      if (!(List.of("http", "https").contains(uri.getScheme()))
          || uri.getHost() == null
          || uri.getUserInfo() != null
          || !uri.getPath().isEmpty()
          || uri.getQuery() != null
          || uri.getFragment() != null) {
        throw new IllegalArgumentException("Workspace AI URL must be an HTTP origin");
      }
    }
    this.profile = profile;
    this.tokens = tokens;
    this.json = json;
  }

  RemoteJob submit(WorkspaceIndexStore.Lease job) {
    var a = job.file();
    var body =
        Map.of(
            "operationId",
            job.id(),
            "documentId",
            Long.toString(job.version()),
            "knowledgeBaseId",
            Long.toString(job.project()),
            "contentHash",
            a.sha256(),
            "parserVersion",
            PARSER,
            "embeddingProfile",
            profile,
            "object",
            Map.of(
                "objectId",
                a.attachmentId(),
                "sha256",
                a.sha256(),
                "sizeBytes",
                a.sizeBytes(),
                "logicalPath",
                a.fileName()));
    var request =
        request("/internal/v1/index-jobs", job.id())
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", job.id())
            .header(
                "X-Delegation-Token",
                tokens.indexDelegation(
                    job.id(),
                    job.project(),
                    job.actor(),
                    job.version(),
                    job.authRevision(),
                    a.attachmentId()));
    try {
      return checkedJob(
          exchange(
              request.POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body))),
              202,
              RemoteJob.class),
          job);
    } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
      throw new IndexFailure("INVALID_INDEX_REQUEST", false);
    }
  }

  RemoteJob progress(WorkspaceIndexStore.Lease job) {
    return checkedJob(
        exchange(
            request("/internal/v1/index-jobs/" + job.remoteId(), job.id()), 200, RemoteJob.class),
        job);
  }

  private static RemoteJob checkedJob(RemoteJob response, WorkspaceIndexStore.Lease job) {
    if (response.id() == null
        || !job.id().equals(response.operationId())
        || response.status() == null
        || response.stage() == null) throw new IndexFailure("INVALID_INDEX_RESPONSE", false);
    WorkspaceDocumentService.parseUuid(response.id());
    return response;
  }

  List<WorkspaceDocument.Block> blocks(WorkspaceIndexStore.Lease job, String aiJob) {
    List<WorkspaceDocument.Block> result = new ArrayList<>();
    var locators = new HashSet<String>();
    String next = null;
    do {
      String url =
          "/internal/v2/index-jobs/" + aiJob + "/blocks" + (next == null ? "" : "?cursor=" + next);
      var request =
          request(url, aiJob)
              .header(
                  "X-Delegation-Token",
                  tokens.exportDelegation(
                      aiJob,
                      job.project(),
                      job.actor(),
                      job.version(),
                      job.authRevision(),
                      job.file().sha256()));
      Blocks page = exchange(request, 200, Blocks.class);
      if (!aiJob.equals(page.jobId())
          || !Long.toString(job.version()).equals(page.versionId())
          || !PARSER.equals(page.parserVersion())
          || !job.file().sha256().equals(page.contentHash())
          || page.blocks() == null
          || page.blocks().isEmpty()
          || page.blocks().size() > 100) {
        throw new IndexFailure("INVALID_INDEX_RESPONSE", false);
      }
      for (var block : page.blocks()) {
        if (block.locator() == null
            || !block
                .locator()
                .matches("(line|page|paragraph) [1-9][0-9]{0,5}( part [1-9][0-9]{0,5})?")
            || block.text() == null
            || block.text().isBlank()
            || block.text().codePointCount(0, block.text().length()) > 4000
            || !locators.add(block.locator())
            || result.size() >= 50000) throw new IndexFailure("INVALID_INDEX_RESPONSE", false);
        result.add(block);
      }
      int size = result.size();
      next = page.nextCursor();
      if (next != null && (!next.equals(aiJob + "." + size) || page.blocks().size() != 100)) {
        throw new IndexFailure("INVALID_INDEX_RESPONSE", false);
      }
    } while (next != null);
    return result;
  }

  private HttpRequest.Builder request(String path, String operation) {
    if (base.isBlank()) throw new IndexFailure("INDEX_NOT_CONFIGURED", false);
    return HttpRequest.newBuilder(URI.create(base + path))
        .timeout(Duration.ofSeconds(20))
        .header("Authorization", "Bearer " + tokens.serviceToken(operation))
        .header("X-Request-Id", java.util.UUID.randomUUID().toString())
        .header("Accept", "application/json");
  }

  private <T> T exchange(HttpRequest.Builder request, int expected, Class<T> type) {
    try {
      var response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
      try (var stream = response.body()) {
        byte[] data = stream.readNBytes(2 * 1024 * 1024 + 1);
        if (data.length > 2 * 1024 * 1024) throw new IndexFailure("INVALID_INDEX_RESPONSE", false);
        if (response.statusCode() == expected) return json.readValue(data, type);
        boolean temporary = response.statusCode() == 429 || response.statusCode() >= 500;
        String code = "INDEX_UPSTREAM_FAILED";
        try {
          String candidate = json.readTree(data).path("code").asText();
          if (candidate.matches("[A-Z0-9_]{1,64}")) code = candidate.toUpperCase(Locale.ROOT);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
          // A proxy may return an HTML error page.
        }
        throw new IndexFailure(code, temporary && !code.equals("INDEX_NOT_CONFIGURED"));
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IndexFailure("INDEX_UPSTREAM_UNAVAILABLE", true);
    } catch (IOException ex) {
      throw new IndexFailure("INDEX_UPSTREAM_UNAVAILABLE", true);
    }
  }

  record RemoteJob(
      String id,
      String operationId,
      String status,
      String stage,
      int chunkCount,
      RemoteError error) {}

  record RemoteError(String code, String message, String requestId) {}

  record Blocks(
      String jobId,
      String versionId,
      String contentHash,
      String parserVersion,
      List<WorkspaceDocument.Block> blocks,
      String nextCursor) {}

  static final class IndexFailure extends RuntimeException {
    final boolean retryable;

    IndexFailure(String code, boolean retryable) {
      super(code);
      this.retryable = retryable;
    }
  }
}
