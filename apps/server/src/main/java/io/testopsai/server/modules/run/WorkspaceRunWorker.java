package io.testopsai.server.modules.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.testopsai.server.modules.knowledge.WorkspaceServiceIdentity;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class WorkspaceRunWorker {
  private static final Logger log = LoggerFactory.getLogger(WorkspaceRunWorker.class);
  private final WorkspaceRunService runs;
  private final WorkspaceServiceIdentity identity;
  private final ObjectMapper json;
  private final String endpoint;
  private final Semaphore slot = new Semaphore(1);
  private final java.util.concurrent.ExecutorService workers =
      Executors.newVirtualThreadPerTaskExecutor();
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  WorkspaceRunWorker(
      WorkspaceRunService runs,
      WorkspaceServiceIdentity identity,
      ObjectMapper json,
      @Value("${testopsai.workspace.ai-url}") String endpoint) {
    this.runs = runs;
    this.identity = identity;
    this.json = json;
    this.endpoint = endpoint.replaceFirst("/+$", "");
  }

  @Scheduled(fixedDelay = 650)
  void pick() {
    if (!slot.tryAcquire()) return;
    try {
      var job = runs.claim();
      if (job == null) {
        slot.release();
        return;
      }
      workers.execute(
          () -> {
            try {
              parse(job);
            } catch (InterruptedException ex) {
              Thread.currentThread().interrupt();
            } catch (Exception ex) {
              log.warn(
                  "Robot output parse failed job={} type={}",
                  job.jobId(),
                  ex.getClass().getSimpleName());
              runs.fail(job, "ROBOT_PARSE_FAILED");
            } finally {
              slot.release();
            }
          });
    } catch (RuntimeException ex) {
      slot.release();
      throw ex;
    }
  }

  private void parse(WorkspaceRunService.ParseJob job) throws Exception {
    if (endpoint.isBlank()) {
      runs.fail(job, "RUN_PARSER_NOT_CONFIGURED");
      return;
    }
    var body =
        Map.of(
            "operationId",
            job.jobId(),
            "object",
            Map.of(
                "objectId",
                job.objectId(),
                "sha256",
                job.sha256(),
                "sizeBytes",
                job.sizeBytes(),
                "logicalPath",
                "output.xml"));
    var request =
        HttpRequest.newBuilder(URI.create(endpoint + "/internal/v1/parsers/robot-output"))
            .timeout(Duration.ofSeconds(125))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + identity.serviceToken(job.jobId()))
            .header("X-Request-Id", job.jobId())
            .header(
                "X-Delegation-Token",
                identity.runOutputDelegation(
                    job.jobId(),
                    job.project(),
                    job.actor(),
                    job.authRevision(),
                    job.run(),
                    job.objectId()))
            .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body)))
            .build();
    var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
    try (var stream = response.body()) {
      byte[] bytes = stream.readNBytes(16 * 1024 * 1024 + 1);
      if (response.statusCode() != 200 || bytes.length > 16 * 1024 * 1024) {
        String code = "ROBOT_PARSE_FAILED";
        try {
          String reported = json.readTree(bytes).path("code").asText();
          if (reported.matches("[A-Z0-9_]{1,64}")) code = reported;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
          // Non-JSON dependency failures use the safe default code.
        }
        runs.fail(job, code);
        return;
      }
      runs.complete(job, json.readTree(bytes));
    }
  }

  @PreDestroy
  void close() {
    workers.shutdownNow();
  }
}
