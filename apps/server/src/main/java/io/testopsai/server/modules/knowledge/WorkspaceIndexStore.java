package io.testopsai.server.modules.knowledge;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class WorkspaceIndexStore {
  private final JdbcTemplate jdbc;

  WorkspaceIndexStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public Lease claim() {
    var jobs =
        jdbc.query(
            """
        SELECT j.id,j.project_id,j.version_id,j.actor_id,j.ai_job_id,j.failure_count,
          p.auth_revision,a.id AS attachment_id,a.file_name,a.sha256,a.size_bytes,a.expires_at
        FROM workspace_index_job j
        JOIN projects p ON p.id=j.project_id
        JOIN workspace_doc_version v ON v.id=j.version_id
        JOIN workspace_attachment a ON a.id=v.attachment_id
        WHERE j.status IN ('QUEUED','PROCESSING') AND j.next_attempt_at<=UTC_TIMESTAMP(6)
          AND (j.lease_until IS NULL OR j.lease_until<=UTC_TIMESTAMP(6))
          AND v.review_status='APPROVED' AND v.index_status IN ('QUEUED','PROCESSING')
        ORDER BY j.next_attempt_at,j.id LIMIT 1 FOR UPDATE SKIP LOCKED
        """,
            (rs, i) ->
                new Lease(
                    rs.getString("id"),
                    rs.getLong("project_id"),
                    rs.getLong("version_id"),
                    rs.getLong("actor_id"),
                    rs.getString("ai_job_id"),
                    rs.getInt("failure_count"),
                    rs.getLong("auth_revision"),
                    UUID.randomUUID().toString(),
                    new WorkspaceAttachmentService.StoredAttachment(
                        rs.getString("attachment_id"),
                        rs.getString("file_name"),
                        rs.getString("sha256"),
                        rs.getLong("size_bytes"),
                        rs.getTimestamp("expires_at").toInstant())));
    if (jobs.isEmpty()) return null;
    var job = jobs.getFirst();
    jdbc.update(
        """
        UPDATE workspace_index_job SET status='PROCESSING',lease_token=?,
        lease_until=UTC_TIMESTAMP(6)+INTERVAL 180 SECOND,updated_at=UTC_TIMESTAMP(6) WHERE id=?
        """,
        job.token(),
        job.id());
    jdbc.update(
        "UPDATE workspace_doc_version SET index_status='PROCESSING' WHERE id=?", job.version());
    return job;
  }

  @Transactional
  public void poll(Lease job, WorkspaceIndexClient.RemoteJob remote) {
    jdbc.update(
        """
        UPDATE workspace_index_job SET ai_job_id=?,stage=?,lease_token=NULL,lease_until=NULL,
          failure_count=0,next_attempt_at=UTC_TIMESTAMP(6)+INTERVAL 1 SECOND,updated_at=UTC_TIMESTAMP(6)
        WHERE id=? AND lease_token=?
        """,
        remote.id(),
        safeStage(remote.stage()),
        job.id(),
        job.token());
  }

  @Transactional
  public void ready(Lease job, String remote, List<WorkspaceDocument.Block> blocks) {
    if (jdbc.update(
            """
        UPDATE workspace_index_job SET ai_job_id=?,stage='FINISHED',status='READY',
          lease_token=NULL,lease_until=NULL,error_code=NULL,updated_at=UTC_TIMESTAMP(6)
        WHERE id=? AND lease_token=?
        """,
            remote,
            job.id(),
            job.token())
        == 0) return;
    jdbc.update("DELETE FROM workspace_document_block WHERE version_id=?", job.version());
    var values = new java.util.ArrayList<Object[]>();
    for (int i = 0; i < blocks.size(); i++) {
      var block = blocks.get(i);
      values.add(new Object[] {job.version(), i, block.locator(), block.text()});
    }
    jdbc.batchUpdate(
        "INSERT INTO workspace_document_block (version_id,ordinal,locator,content) VALUES (?,?,?,?)",
        values);
    jdbc.update(
        "UPDATE workspace_doc_version SET index_status='READY',error_code=NULL WHERE id=?",
        job.version());
  }

  @Transactional
  public void fail(Lease job, String code, boolean retryable) {
    String error = code != null && code.matches("[A-Z0-9_]{1,64}") ? code : "INDEX_FAILED";
    if (retryable && job.failures() < 3) {
      int wait = (int) Math.pow(4, job.failures());
      jdbc.update(
          """
          UPDATE workspace_index_job SET failure_count=failure_count+1,lease_token=NULL,lease_until=NULL,
            next_attempt_at=TIMESTAMPADD(SECOND,?,UTC_TIMESTAMP(6)),updated_at=UTC_TIMESTAMP(6)
          WHERE id=? AND lease_token=?
          """,
          wait,
          job.id(),
          job.token());
      return;
    }
    if (jdbc.update(
            """
        UPDATE workspace_index_job SET stage='FINISHED',status='FAILED',error_code=?,
          lease_token=NULL,lease_until=NULL,updated_at=UTC_TIMESTAMP(6) WHERE id=? AND lease_token=?
        """,
            error,
            job.id(),
            job.token())
        > 0) {
      jdbc.update(
          "UPDATE workspace_doc_version SET index_status='FAILED',error_code=? WHERE id=?",
          error,
          job.version());
    }
  }

  private static String safeStage(String stage) {
    return List.of("PARSING", "CHUNKING", "EMBEDDING", "CLEANUP", "FINISHED").contains(stage)
        ? stage
        : "QUEUED";
  }

  record Lease(
      String id,
      long project,
      long version,
      long actor,
      String remoteId,
      int failures,
      long authRevision,
      String token,
      WorkspaceAttachmentService.StoredAttachment file) {}
}
