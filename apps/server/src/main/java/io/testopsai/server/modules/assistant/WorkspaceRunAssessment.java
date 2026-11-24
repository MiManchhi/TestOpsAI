package io.testopsai.server.modules.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.testopsai.server.modules.knowledge.WorkspaceAnswerEvidence;
import io.testopsai.server.modules.knowledge.WorkspaceAnswerEvidence.EvidenceRef;
import io.testopsai.server.modules.run.WorkspaceRunService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class WorkspaceRunAssessment {
  private static final String VALUE =
      "([0-9]{1,12}(?:[.][0-9]{1,9})?)\\s*(milliseconds?|ms|毫秒|seconds?|秒|s)";
  private static final Pattern LOG =
      Pattern.compile(
          "(?i)^\\s*(?:response_time|response[ -]time|响应时间|抄读耗时)\\s*[:：=]\\s*"
              + VALUE
              + "\\s*[。.]?\\s*$");
  private static final Pattern WITHIN =
      Pattern.compile("(?i)^\\s*(?:设备|电表|抄读指令)\\s*(?:应|必须)\\s*在\\s*" + VALUE + "\\s*内响应[。.]?\\s*$");
  private static final Pattern COMPARISON =
      Pattern.compile(
          "(?i)^\\s*(?:(?:设备|电表|抄读)\\s*)?(?:response[ -]time|响应时间|抄读耗时)\\s*"
              + "(?:应\\s*)?(<=|≤|<|不超过|小于)\\s*"
              + VALUE
              + "\\s*[。.]?\\s*$");

  private WorkspaceRunAssessment() {}

  static List<WorkspaceChat.Proof> logProofs(WorkspaceRunService service, long run) {
    return service.evidenceLines(run).stream()
        .map(
            line ->
                new WorkspaceChat.Proof(
                    new EvidenceRef(
                        "RUN_LOG",
                        Long.toString(line.artifact()),
                        line.hash(),
                        "line " + line.line()),
                    line.text()))
        .toList();
  }

  static String robotStatus(WorkspaceRunService.Run run) {
    if (!"READY".equals(run.parseStatus()) || run.total() == 0) return "UNKNOWN";
    if (run.failed() > 0) return "FAIL";
    return run.passed() > 0 ? "PASS" : "SKIP";
  }

  static ObjectNode compare(
      ObjectMapper json,
      WorkspaceAnswerEvidence evidence,
      WorkspaceChat.Job job,
      WorkspaceChat.ScopeFacts facts,
      CitationWriter writer) {
    var result = json.createObjectNode();
    String robot = robotStatus(facts.run());
    result.put("robotStatus", robot);
    result.putNull("robotDraft");
    var output = result.putArray("findings");
    result.putArray("pending");
    var references = new LinkedHashMap<EvidenceRef, Map<String, Object>>();
    var observed = new ArrayList<Observed>();
    for (var proof : facts.logProofs()) {
      var match = LOG.matcher(proof.excerpt());
      if (match.matches())
        observed.add(new Observed(seconds(match.group(1), match.group(2)), proof.source()));
    }
    var summary = new ArrayList<String>();
    summary.add(
        switch (robot) {
          case "PASS" -> "Robot 执行通过";
          case "FAIL" -> "Robot 执行失败";
          case "SKIP" -> "Robot 执行已跳过";
          default -> "Robot 执行状态待确认";
        });
    add(
        json,
        evidence,
        job,
        facts.basisAtRun(),
        "AT_RUN",
        "当时",
        observed,
        writer,
        references,
        summary,
        output);
    add(
        json,
        evidence,
        job,
        facts.basis(),
        "CURRENT",
        "当前",
        observed,
        writer,
        references,
        summary,
        output);
    result.put(
        "summary",
        summary.getFirst() + "；响应时间：" + String.join("；", summary.subList(1, summary.size())) + "。");
    result.set("citations", json.valueToTree(references.values()));
    return result;
  }

  private static void add(
      ObjectMapper json,
      WorkspaceAnswerEvidence evidence,
      WorkspaceChat.Job job,
      WorkspaceAnswerEvidence.Basis basis,
      String timing,
      String label,
      List<Observed> observed,
      CitationWriter writer,
      Map<EvidenceRef, Map<String, Object>> references,
      List<String> summary,
      com.fasterxml.jackson.databind.node.ArrayNode output) {
    var finding = output.addObject();
    finding.put("basis", timing);
    finding.put("versionLabel", basis == null ? label + "验收依据" : basis.label());
    finding.putNull("observed");
    var ids = finding.putArray("citationIds");
    if (basis == null || !"COMPLETE".equals(basis.coverage())) {
      finding.put("expected", "需要核对完整、可访问的验收依据。");
      finding.put("verdict", "NEEDS_CONFIRMATION");
      summary.add(label + "验收依据待确认");
      return;
    }
    var clauses = new ArrayList<Clause>();
    for (var proof :
        evidence.responseClauses(job.project(), job.actor(), basis.visibleVersionIds())) {
      var within = WITHIN.matcher(proof.excerpt());
      var compare = COMPARISON.matcher(proof.excerpt());
      if (within.matches())
        clauses.add(new Clause(seconds(within.group(1), within.group(2)), false, proof.source()));
      else if (compare.matches())
        clauses.add(
            new Clause(
                seconds(compare.group(2), compare.group(3)),
                List.of("<", "小于").contains(compare.group(1)),
                proof.source()));
    }
    if (clauses.isEmpty()) {
      finding.put("expected", "缺少可核实的响应时间条款。");
      finding.put("verdict", "INSUFFICIENT");
      summary.add("按" + label + "依据缺少条款");
      return;
    }
    var clause = clauses.getFirst();
    boolean conflicts =
        clauses.stream()
            .anyMatch(
                other ->
                    other.limit().compareTo(clause.limit()) != 0
                        || other.strict() != clause.strict());
    if (conflicts) {
      finding.put("expected", "可见响应时间条款存在冲突，需要项目负责人确认。");
      finding.put("verdict", "NEEDS_CONFIRMATION");
      clauses.stream()
          .limit(5)
          .map(Clause::source)
          .forEach(
              source ->
                  ids.add(
                      references
                          .computeIfAbsent(source, writer::write)
                          .get("citationId")
                          .toString()));
      summary.add("按" + label + "依据需人工确认");
      return;
    }
    finding.put(
        "expected", "响应时间 " + (clause.strict() ? "<" : "≤") + " " + plain(clause.limit()) + " 秒");
    ids.add(
        references.computeIfAbsent(clause.source(), writer::write).get("citationId").toString());
    if (observed.size() != 1) {
      finding.put("verdict", "INSUFFICIENT");
      finding.put("observed", observed.isEmpty() ? "缺少独立记录的响应时间" : "存在多条响应时间，无法确认对应测量");
      summary.add("按" + label + "依据缺少可唯一核对的实测");
      return;
    }
    var value = observed.getFirst();
    int cmp = value.value().compareTo(clause.limit());
    boolean ok = clause.strict() ? cmp < 0 : cmp <= 0;
    finding.put("observed", "实测 " + plain(value.value()) + " 秒");
    finding.put("verdict", ok ? "MEETS" : "DOES_NOT_MEET");
    finding.set(
        "metric",
        json.valueToTree(
            Map.of(
                "unit",
                "s",
                "actual",
                value.value(),
                "limit",
                clause.limit(),
                "operator",
                clause.strict() ? "LT" : "LE")));
    ids.add(references.computeIfAbsent(value.source(), writer::write).get("citationId").toString());
    summary.add("按" + label + " " + basis.label() + (ok ? " 达标" : " 未达标"));
  }

  private static BigDecimal seconds(String value, String unit) {
    var number = new BigDecimal(value);
    return unit.equalsIgnoreCase("ms")
            || unit.equals("毫秒")
            || unit.toLowerCase(java.util.Locale.ROOT).startsWith("milli")
        ? number.movePointLeft(3)
        : number;
  }

  private static String plain(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }

  interface CitationWriter {
    Map<String, Object> write(EvidenceRef ref);
  }

  record Observed(BigDecimal value, EvidenceRef source) {}

  record Clause(BigDecimal limit, boolean strict, EvidenceRef source) {}
}
