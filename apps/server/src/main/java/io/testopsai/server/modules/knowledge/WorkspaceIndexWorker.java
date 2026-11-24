package io.testopsai.server.modules.knowledge;

import io.testopsai.server.platform.web.WorkspaceFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "testopsai.workspace.index-worker-enabled", havingValue = "true")
class WorkspaceIndexWorker {
  private static final Logger log = LoggerFactory.getLogger(WorkspaceIndexWorker.class);
  private final WorkspaceIndexStore store;
  private final WorkspaceIndexClient client;

  WorkspaceIndexWorker(WorkspaceIndexStore store, WorkspaceIndexClient client) {
    this.store = store;
    this.client = client;
  }

  @Scheduled(fixedDelay = 500)
  void dispatch() {
    var job = store.claim();
    if (job == null) return;
    try {
      var remote = job.remoteId() == null ? client.submit(job) : client.progress(job);
      switch (remote.status()) {
        case "QUEUED", "PROCESSING" -> store.poll(job, remote);
        case "SUCCEEDED" -> store.ready(job, remote.id(), client.blocks(job, remote.id()));
        case "FAILED", "CANCELLED" ->
            store.fail(job, remote.error() == null ? "INDEX_FAILED" : remote.error().code(), false);
        default -> store.fail(job, "INVALID_INDEX_RESPONSE", false);
      }
    } catch (WorkspaceIndexClient.IndexFailure ex) {
      store.fail(job, ex.getMessage(), ex.retryable);
    } catch (WorkspaceFailure ex) {
      store.fail(job, ex.code(), false);
    } catch (RuntimeException ex) {
      log.warn("Workspace index job {} failed within {}", job.id(), ex.getClass().getSimpleName());
      store.fail(job, "INDEX_FAILED", true);
    }
  }
}
