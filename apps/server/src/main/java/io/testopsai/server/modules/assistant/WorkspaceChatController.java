package io.testopsai.server.modules.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.testopsai.server.modules.knowledge.WorkspaceAnswerEvidence;
import io.testopsai.server.modules.knowledge.WorkspaceServiceIdentity;
import io.testopsai.server.platform.security.CurrentUserResolver;
import io.testopsai.server.platform.web.PageResponse;
import io.testopsai.server.platform.web.WorkspaceFailure;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class WorkspaceChatController {
  private static final String PROJECT = "/api/v3/projects/{projectId}";
  private final WorkspaceChatService chat;
  private final WorkspaceAnswerWorker answerWorker;
  private final CurrentUserResolver users;
  private final WorkspaceServiceIdentity service;
  private final ObjectMapper json;
  private final java.util.concurrent.Semaphore streams = new java.util.concurrent.Semaphore(32);
  private final java.util.concurrent.ExecutorService executor =
      java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();

  WorkspaceChatController(
      WorkspaceChatService chat,
      WorkspaceAnswerWorker answerWorker,
      CurrentUserResolver users,
      WorkspaceServiceIdentity service,
      ObjectMapper json) {
    this.chat = chat;
    this.answerWorker = answerWorker;
    this.users = users;
    this.service = service;
    this.json = json;
  }

  @GetMapping(PROJECT + "/assistant-status")
  ResponseEntity<WorkspaceAnswerWorker.Status> assistantStatus(
      Authentication auth, @PathVariable long projectId) {
    chat.requireMember(users.resolve(auth), projectId);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(answerWorker.configuration());
  }

  @GetMapping(PROJECT + "/conversations")
  PageResponse<WorkspaceChat.Conversation> myHistory(
      Authentication auth,
      @PathVariable long projectId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String q) {
    return chat.history(users.resolve(auth), projectId, page, size, q);
  }

  @GetMapping(PROJECT + "/conversations/{conversationId}/messages")
  PageResponse<WorkspaceChat.Message> myMessages(
      Authentication auth,
      @PathVariable long projectId,
      @PathVariable UUID conversationId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return chat.messages(users.resolve(auth), projectId, conversationId, page, size);
  }

  @PostMapping(PROJECT + "/turns")
  @ResponseStatus(HttpStatus.ACCEPTED)
  WorkspaceChat.Turn startTurn(
      Authentication auth,
      @PathVariable long projectId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody WorkspaceChat.Input input) {
    return chat.start(users.resolve(auth), projectId, key, input);
  }

  @GetMapping(PROJECT + "/turns/{turnId}")
  WorkspaceChat.Turn getMyTurn(
      Authentication auth, @PathVariable long projectId, @PathVariable UUID turnId) {
    return chat.turn(users.resolve(auth), projectId, turnId);
  }

  @PostMapping(PROJECT + "/turns/{turnId}/cancel")
  WorkspaceChat.Turn stopTurn(
      Authentication auth,
      @PathVariable long projectId,
      @PathVariable UUID turnId,
      @RequestHeader("Idempotency-Key") String key) {
    return chat.cancel(users.resolve(auth), projectId, turnId, key);
  }

  @GetMapping(
      value = PROJECT + "/turns/{turnId}/events",
      produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.SseEmitter> watchTurn(
      Authentication auth,
      @PathVariable long projectId,
      @PathVariable UUID turnId,
      @RequestHeader(value = "Last-Event-ID", defaultValue = "0") int last) {
    if (last < 0) throw new IllegalArgumentException("Invalid last event sequence");
    chat.events(users.resolve(auth), projectId, turnId, last);
    if (!streams.tryAcquire())
      throw new WorkspaceFailure(
          HttpStatus.SERVICE_UNAVAILABLE, "EVENT_STREAM_LIMIT", "Too many active subscriptions");
    var emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(125000L);
    var finished = new java.util.concurrent.atomic.AtomicBoolean();
    emitter.onCompletion(() -> finished.set(true));
    emitter.onTimeout(
        () -> {
          finished.set(true);
          emitter.complete();
        });
    emitter.onError(error -> finished.set(true));
    try {
      executor.execute(
          () -> {
            try {
              int cursor = last;
              var deadline = java.time.Instant.now().plusSeconds(122);
              long nextHeartbeat = 0;
              while (!finished.get() && java.time.Instant.now().isBefore(deadline)) {
                var actor = users.resolve(auth);
                if (actor.mustChangePassword()) break;
                var events = chat.events(actor, projectId, turnId, cursor);
                for (var event : events) {
                  emitter.send(
                      org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                          .id(Integer.toString(event.sequence()))
                          .name(event.event())
                          .data(json.writeValueAsString(event), MediaType.TEXT_PLAIN));
                  cursor = event.sequence();
                  if (java.util.List.of("turn.completed", "turn.failed", "turn.cancelled")
                      .contains(event.event())) return;
                }
                if (events.isEmpty()
                    && java.util.List.of("COMPLETED", "FAILED", "CANCELLED")
                        .contains(chat.turn(actor, projectId, turnId).status())) {
                  // The terminal state may have committed between two reads. Try its event once
                  // more.
                  var terminal = chat.events(actor, projectId, turnId, cursor);
                  for (var event : terminal)
                    emitter.send(
                        org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                            .id(Integer.toString(event.sequence()))
                            .name(event.event())
                            .data(json.writeValueAsString(event), MediaType.TEXT_PLAIN));
                  return;
                }
                if (System.nanoTime() > nextHeartbeat) {
                  emitter.send(
                      org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                          .comment("heartbeat"));
                  nextHeartbeat = System.nanoTime() + java.time.Duration.ofSeconds(10).toNanos();
                }
                Thread.sleep(650);
              }
            } catch (InterruptedException ex) {
              Thread.currentThread().interrupt();
            } catch (java.io.IOException | WorkspaceFailure | IllegalStateException ex) {
              // A disconnected or no longer authorized stream closes without exposing data.
            } finally {
              streams.release();
              emitter.complete();
            }
          });
    } catch (RuntimeException ex) {
      streams.release();
      throw ex;
    }
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_EVENT_STREAM)
        .cacheControl(CacheControl.noStore())
        .header("X-Accel-Buffering", "no")
        .body(emitter);
  }

  @jakarta.annotation.PreDestroy
  void closeStreams() {
    executor.shutdownNow();
  }

  @GetMapping(PROJECT + "/citations/{citationId}")
  ResponseEntity<Map<String, Object>> readCitation(
      Authentication auth, @PathVariable long projectId, @PathVariable UUID citationId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(chat.citation(users.resolve(auth), projectId, citationId));
  }

  @PostMapping("/internal/v2/evidence/resolve")
  ResponseEntity<Map<String, Object>> resolveEvidence(
      @RequestHeader(value = "Authorization", defaultValue = "") String auth,
      @RequestHeader(value = "X-Delegation-Token", defaultValue = "") String delegation,
      @RequestBody EvidenceRequest request) {
    var claims = service.readDelegation(auth, delegation);
    if (request.source() == null
        || request.turnId() == null
        || !request.turnId().toString().equals(claims.get("operationId", String.class))
        || !(claims.get("allowActions") instanceof java.util.List<?> actions)
        || !actions.contains("resolveEvidence")) throw WorkspaceFailure.notFound();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            chat.resolve(
                Long.parseLong(claims.get("projectId", String.class)),
                Long.parseLong(claims.get("actorId", String.class)),
                claims.get("authorizationVersion", Number.class).longValue(),
                request.turnId(),
                request.source()));
  }

  record EvidenceRequest(UUID turnId, WorkspaceAnswerEvidence.EvidenceRef source) {}
}
