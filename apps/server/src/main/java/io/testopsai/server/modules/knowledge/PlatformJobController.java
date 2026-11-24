package io.testopsai.server.modules.knowledge;

import io.testopsai.server.platform.security.CurrentUserResolver;
import io.testopsai.server.platform.web.PageResponse;
import java.util.ArrayList;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v3/platform/jobs")
class PlatformJobController {
  private static final String JOBS =
      """
      FROM (
        SELECT id AS job_id,project_id,'INDEX' AS operation,stage,status,error_code,updated_at
        FROM workspace_index_job
        UNION ALL
        SELECT v.review_job_id,d.project_id,'REVIEW','REVIEW',v.review_status,NULL,
          COALESCE(v.reviewed_at,v.created_at)
        FROM workspace_doc_version v JOIN workspace_document d ON d.id=v.document_id
        UNION ALL
        SELECT job_id,project_id,'PARSE_RUN',parse_status,parse_status,error_code,updated_at
        FROM workspace_run
      ) jobs WHERE 1=1
      """;
  private final JdbcTemplate jdbc;
  private final CurrentUserResolver users;

  PlatformJobController(JdbcTemplate jdbc, CurrentUserResolver users) {
    this.jdbc = jdbc;
    this.users = users;
  }

  @GetMapping
  PageResponse<WorkspaceDocument.Job> platformJobs(
      Authentication authentication,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) Long projectId,
      @RequestParam(required = false) String status) {
    if (!users.resolve(authentication).roles().contains("ADMIN"))
      throw new AccessDeniedException("Platform admin required");
    PageResponse.validatePagination(page, size);
    String where = JOBS;
    var args = new ArrayList<Object>();
    if (projectId != null) {
      where += " AND project_id=?";
      args.add(projectId);
    }
    if (status != null && !status.isBlank()) {
      where += " AND status=?";
      args.add(status);
    }
    long total = jdbc.queryForObject("SELECT COUNT(*) " + where, Long.class, args.toArray());
    args.add(size);
    args.add((long) page * size);
    var result =
        jdbc.query(
            "SELECT * " + where + " ORDER BY updated_at DESC,job_id LIMIT ? OFFSET ?",
            (rs, i) ->
                new WorkspaceDocument.Job(
                    rs.getString("job_id"),
                    rs.getString("project_id"),
                    rs.getString("operation"),
                    rs.getString("stage"),
                    rs.getString("status"),
                    rs.getString("error_code"),
                    rs.getTimestamp("updated_at").toInstant()),
            args.toArray());
    return new PageResponse<>(result, total, page, size);
  }
}
