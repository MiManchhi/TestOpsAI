package io.testopsai.server.modules.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.testopsai.server.modules.knowledge.WorkspaceServiceIdentity;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class WorkspaceAnswerWorker {
  private static final Logger log = LoggerFactory.getLogger(WorkspaceAnswerWorker.class);
  private final String endpoint;
  private final WorkspaceChatService chat;
  private final WorkspaceServiceIdentity identity;
  private final ObjectMapper json;
  private final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(5))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();
  private final Semaphore capacity = new Semaphore(2);
  private final java.util.concurrent.ExecutorService workers =
      Executors.newVirtualThreadPerTaskExecutor();

  WorkspaceAnswerWorker(
      @Value("${testopsai.workspace.ai-url}") String endpoint,
      WorkspaceChatService chat,
      WorkspaceServiceIdentity identity,
      ObjectMapper json) {
    this.endpoint = endpoint.replaceFirst("/+$", "");
    this.chat = chat;
    this.identity = identity;
    this.json = json;
  }

  @Scheduled(fixedDelay = 750)
  void pick() {
    if (!capacity.tryAcquire()) return;
    WorkspaceChat.Job job;
    try {
      job = chat.claim();
    } catch (RuntimeException ex) {
      capacity.release();
      log.warn("Workspace chat queue unavailable ({})", ex.getClass().getSimpleName());
      return;
    }
    if (job == null) {
      capacity.release();
      return;
    }
    workers.execute(
        () -> {
          try {
            answer(job);
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            chat.fail(job);
          } catch (Exception ex) {
            log.warn(
                "Workspace answer failed requestId={} ({})",
                job.requestId(),
                ex.getClass().getSimpleName());
            chat.fail(job);
          } finally {
            capacity.release();
          }
        });
  }

  private void answer(WorkspaceChat.Job job) throws Exception {
    if (endpoint.isBlank()) throw new IllegalStateException("Workspace AI is not configured");
    var facts = chat.authorize(job);
    var scope = facts.scope();
    var digest =
        HexFormat.of()
            .formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(
                        json.writeValueAsBytes(
                            List.of(
                                scope.projectId(),
                                scope.turnId(),
                                scope.authRevision(),
                                scope.versionIds().stream().distinct().sorted().toList(),
                                scope.runIds().stream().distinct().sorted().toList(),
                                scope.attachmentIds().stream().distinct().sorted().toList()))));
    var body = json.createObjectNode();
    body.set("scope", json.valueToTree(scope));
    body.put("text", job.text());
    body.set("history", json.valueToTree(facts.history()));
    body.set("basisAtRun", json.valueToTree(facts.basisAtRun()));
    body.set("basisCurrent", json.valueToTree(facts.basis()));
    body.set("versions", json.valueToTree(facts.versions()));
    var files = body.putArray("files");
    for (var file : facts.files()) {
      var dto = files.addObject();
      dto.put("attachmentId", file.attachmentId());
      dto.put("objectId", file.objectId());
      dto.put("logicalPath", file.logicalPath());
      dto.put("sizeBytes", file.sizeBytes());
      dto.put("sha256", file.sha256());
    }
    var runs = body.putArray("runs");
    if (facts.run() != null) {
      var run = runs.addObject();
      run.put("runId", facts.run().runId());
      run.put("name", facts.run().name());
      run.put("executedAt", facts.run().executedAt().toString());
      run.set("results", json.valueToTree(facts.runResults()));
      run.set("facts", json.valueToTree(facts.logProofs()));
    }
    body.put("currentMessageId", job.messageId());
    var request =
        HttpRequest.newBuilder(URI.create(endpoint + "/internal/v2/ai/answer"))
            .timeout(Duration.ofSeconds(115))
            .header("Authorization", "Bearer " + identity.serviceToken(job.turnId()))
            .header(
                "X-Delegation-Token",
                identity.answerDelegation(
                    job.turnId(), job.project(), job.actor(), scope.authRevision(), digest))
            .header("X-Request-Id", job.requestId())
            .header("Content-Type", "application/json");
    if (!facts.files().isEmpty())
      request.header(
          "X-Object-Delegation-Token",
          identity.answerObjectDelegation(
              job.turnId(),
              job.project(),
              job.actor(),
              scope.authRevision(),
              scope.attachmentIds()));
    var result =
        http.send(
            request
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)))
                .build(),
            HttpResponse.BodyHandlers.ofInputStream());
    try (var bytes = result.body()) {
      byte[] data = bytes.readNBytes(256 * 1024 + 1);
      if (result.statusCode() != 200 || data.length > 256 * 1024) {
        log.warn(
            "Workspace answer dependency rejected requestId={} status={}",
            job.requestId(),
            result.statusCode());
        var cause =
            data.length > 0 && data.length < 8192
                ? json.readTree(data).path("code").asText()
                : null;
        chat.fail(job, cause);
        return;
      }
      chat.complete(job, facts, json.readTree(data));
    }
  }

  record Status(String chat, String embedding) {}

  Status configuration() {
    if (endpoint.isBlank()) return new Status("UNCONFIGURED", "UNCONFIGURED");
    var unavailable = new Status("UNAVAILABLE", "UNAVAILABLE");
    try {
      var request =
          HttpRequest.newBuilder(URI.create(endpoint + "/health/configuration"))
              .timeout(Duration.ofSeconds(3))
              .GET()
              .build();
      var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
      try (var bytes = response.body()) {
        if (response.statusCode() != 200) return unavailable;
        var data = bytes.readNBytes(2049);
        if (data.length > 2048) return unavailable;
        var flags = json.readTree(data);
        if (!flags.path("chatModelConfigured").isBoolean()
            || !flags.path("embeddingConfigured").isBoolean()) return unavailable;
        return new Status(
            flags.path("chatModelConfigured").asBoolean() ? "READY" : "UNCONFIGURED",
            flags.path("embeddingConfigured").asBoolean() ? "READY" : "UNCONFIGURED");
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return unavailable;
    } catch (java.io.IOException | IllegalArgumentException ex) {
      return unavailable;
    }
  }

  @PreDestroy
  void stop() {
    workers.shutdownNow();
  }
}
