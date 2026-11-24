package io.testopsai.server.modules.assistant;

import io.testopsai.server.modules.identity.AppUser;
import io.testopsai.server.modules.run.WorkspaceRunService;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class WorkspaceRunSelection {
  private static final Pattern ID =
      Pattern.compile("(?i)(?:运行|复测|run)\\s*#\\s*([1-9][0-9]{0,17})(?!\\d)");
  private static final Pattern LATEST =
      Pattern.compile(
          "(?is)(?:最近|最新|最后一|刚才那|latest|most recent).{0,16}(?:运行|复测|测试结果|run|retest)|(?:运行|复测|run|retest).{0,10}(?:最新|最近一)");
  private static final Pattern REFERENCED =
      Pattern.compile(
          "(?is)(?:(?:这次|本次|那次|上次|昨天|今天|周[一二三四五六日天]).{0,15}(?:运行|复测|测试结果)|(?:this|that|previous) (?:run|retest))");
  private final WorkspaceRunService runs;
  private final JdbcTemplate jdbc;

  WorkspaceRunSelection(WorkspaceRunService runs, JdbcTemplate jdbc) {
    this.runs = runs;
    this.jdbc = jdbc;
  }

  record Choice(Long runId, String clarification, List<String> options) {
    static Choice use(Long id) {
      return new Choice(id, null, List.of());
    }
  }

  Choice resolve(AppUser actor, long project, String conversation, WorkspaceChat.Input input) {
    var id = ID.matcher(input.text());
    if (id.find()) {
      var matches =
          jdbc.queryForList(
              "SELECT id FROM workspace_run WHERE project_id=? AND id=?",
              Long.class,
              project,
              Long.parseLong(id.group(1)));
      if (matches.isEmpty()) return new Choice(null, "当前项目找不到这个运行编号。请从运行记录中选择。", List.of());
      return ready(runs.find(project, matches.getFirst()));
    }
    boolean latest = LATEST.matcher(input.text()).find();
    if (input.runId() != null && !latest) return ready(runs.find(project, input.runId()));
    if (!latest && input.conversationId() != null) {
      var prior =
          jdbc.queryForList(
              "SELECT run_id FROM workspace_chat_turn WHERE conversation_id=? AND status='COMPLETED' ORDER BY created_at DESC,id DESC LIMIT 1",
              Long.class,
              conversation);
      if (!prior.isEmpty()
          && prior.getFirst() != null
          && !input.text().matches("(?s).*(?:昨天|今天|周[一二三四五六日天]|上次).*"))
        return ready(runs.find(project, prior.getFirst()));
    }
    if (!latest && !REFERENCED.matcher(input.text()).find()) return Choice.use(null);
    var page = runs.list(actor, project, 0, 5, "");
    if (page.items().isEmpty())
      return new Choice(null, "当前项目还没有运行记录。请先登记 Robot 运行，再核对复测结果。", List.of());
    var first = page.items().getFirst();
    boolean uniqueLatest =
        latest
            && (page.total() == 1 || !first.executedAt().equals(page.items().get(1).executedAt()));
    if (uniqueLatest
        || (page.total() == 1 && !input.text().matches("(?s).*(?:昨天|今天|周[一二三四五六日天]).*")))
      return ready(first);
    return new Choice(
        null,
        "请确认要核对哪次复测。可回复下列运行编号，或从运行记录中打开其他记录。",
        page.items().stream()
            .map(run -> "运行 #" + run.runId() + " · " + run.name() + " · " + run.executedAt())
            .toList());
  }

  private Choice ready(WorkspaceRunService.Run run) {
    if ("READY".equals(run.parseStatus())) return Choice.use(Long.parseLong(run.runId()));
    return new Choice(
        null,
        "运行 #"
            + run.runId()
            + "「"
            + run.name()
            + "」"
            + ("FAILED".equals(run.parseStatus()) ? "解析失败，请打开运行记录查看原因。" : "正在解析，完成后即可核对。"),
        List.of());
  }
}
