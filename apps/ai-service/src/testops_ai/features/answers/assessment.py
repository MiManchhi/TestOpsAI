import re
from decimal import Decimal
from typing import Literal

from .models import AnswerCandidate, AnswerContext, EvidenceRef, Proof, RunContext, VisibleBasis

VALUE = r"([0-9]{1,12}(?:\.[0-9]{1,9})?)\s*(milliseconds?|ms|毫秒|seconds?|秒|s)"
MEASURED = re.compile(
    r"(?:response_time|response[ -]time|响应时间|抄读耗时)\s*[:：=]\s*" + VALUE + r"[。.]?",
    re.I,
)
WITHIN = re.compile(
    r"(?:设备|电表|抄读指令)\s*(?:应|必须)\s*在\s*" + VALUE + r"\s*内响应[。.]?",
    re.I,
)
COMPARISON = re.compile(
    r"(?:(?:设备|电表|抄读)\s*)?(?:response[ -]time|响应时间|抄读耗时)"
    r"\s*(?:应\s*)?(<=|≤|<|不超过|小于)\s*" + VALUE + r"[。.]?",
    re.I,
)


def seconds(value: str, unit: str) -> Decimal:
    n = Decimal(value)
    return n / 1000 if unit.lower() in {"ms", "毫秒", "millisecond", "milliseconds"} else n


def observed(run: RunContext) -> list[tuple[Decimal, EvidenceRef]]:
    values = []
    for p in run.facts:
        m = MEASURED.fullmatch(p.excerpt.strip())
        if m and p.source.resource_kind == "RUN_LOG":
            values.append((seconds(m[1], m[2]), p.source))
    return values


def findings_for(
    run: RunContext, basis: VisibleBasis | None, timing: str, references: list[Proof]
) -> dict[str, object]:
    label = basis.label if basis else "验收依据待确认"
    result: dict[str, object] = {
        "basis": timing,
        "versionLabel": label,
        "expected": "缺少可核实的响应时间条款。",
        "observed": None,
        "verdict": "INSUFFICIENT",
        "sources": [],
    }
    if not basis or basis.coverage != "COMPLETE":
        result["verdict"] = "NEEDS_CONFIRMATION"
        return result
    limits = []
    for p in references:
        if p.source.resource_id not in basis.visible_version_ids:
            continue
        text = p.excerpt.strip()
        within = WITHIN.fullmatch(text)
        compare = COMPARISON.fullmatch(text)
        if within:
            limits.append((seconds(within[1], within[2]), False, p.source))
        elif compare:
            limits.append((seconds(compare[2], compare[3]), compare[1] in {"<", "小于"}, p.source))
    if not limits:
        return result
    limit, strict, source = limits[0]
    if any((x, op) != (limit, strict) for x, op, _ in limits):
        result.update(expected="响应时间条款冲突。", verdict="NEEDS_CONFIRMATION")
        result["sources"] = [ref.model_dump(by_alias=True) for _, _, ref in limits[:5]]
        return result
    result["expected"] = f"响应时间 {'<' if strict else '≤'} {limit} 秒"
    result["sources"] = [source.model_dump(by_alias=True)]
    values = observed(run)
    if len(values) != 1:
        return result
    actual, log = values[0]
    meets = actual < limit if strict else actual <= limit
    result.update(
        observed=f"实测 {actual} 秒",
        verdict="MEETS" if meets else "DOES_NOT_MEET",
        metric={
            "unit": "s",
            "actual": float(actual),
            "limit": float(limit),
            "operator": "LT" if strict else "LE",
        },
    )
    result["sources"] = [ref.model_dump(by_alias=True) for ref in (source, log)]
    return result


def compare_run(context: AnswerContext, proofs: list[Proof]) -> AnswerCandidate:
    run = context.runs[0]
    status: Literal["PASS", "FAIL", "SKIP", "UNKNOWN"]
    statuses = {item.robot_status for item in run.results}
    if "FAIL" in statuses:
        status = "FAIL"
    elif "PASS" in statuses:
        status = "PASS"
    elif "SKIP" in statuses:
        status = "SKIP"
    else:
        status = "UNKNOWN"
    return AnswerCandidate(
        summary="Robot 原始执行状态与验收条款见逐条核对。",
        robot_status=status,
        findings=[
            findings_for(run, context.basis_at_run, "AT_RUN", proofs),
            findings_for(run, context.basis_current, "CURRENT", proofs),
        ],
        robot_draft=None,
        pending=[],
        sources=[],
    )


def timing_assessment_requested(context: AnswerContext, proofs: list[Proof]) -> bool:
    question = context.text.casefold()
    if re.search(r"响应时间|延迟|响应速度|耗时|latency|response[ _-]time", question):
        return True
    # A known time limit does not answer an unrelated compliance question.
    if re.search(r"拒绝|原因|日志记录|字段|权限|加密|校验码|精度|电压|电流", question):
        return False
    versions = {
        version
        for basis in (context.basis_at_run, context.basis_current)
        if basis
        for version in basis.visible_version_ids
    }
    return any(
        p.source.resource_id in versions
        and (WITHIN.fullmatch(p.excerpt.strip()) or COMPARISON.fullmatch(p.excerpt.strip()))
        for p in proofs
    )
