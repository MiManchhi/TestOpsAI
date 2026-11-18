"""Bounded output.xml parsing; statistics come exclusively from individual tests."""

from datetime import UTC, datetime
from decimal import Decimal, InvalidOperation
from io import BytesIO
from typing import Literal, cast
from xml.etree.ElementTree import Element, ParseError

from defusedxml.common import DefusedXmlException
from defusedxml.ElementTree import iterparse
from pydantic import ValidationError

from .catalog import issue
from .errors import ParserError, invalid_content
from .models import ImportIssue, ObjectRef, ParsedResult, ResultParseResult, RunCounts


def timestamp(value: str | None, path: str, issues: list[ImportIssue]) -> str | None:
    if not value:
        return None
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        if parsed.tzinfo is None:
            raise ValueError("Missing timezone")
        return parsed.astimezone(UTC).isoformat().replace("+00:00", "Z")
    except ValueError:
        issues.append(issue("INVALID_TIME", path, None, "Timestamp has no valid explicit timezone"))
        return None


def elapsed(status: Element) -> int:
    try:
        if "elapsed" in status.attrib:
            seconds = Decimal(status.attrib["elapsed"])
            if not seconds.is_finite() or not 0 <= seconds <= Decimal(2**63 - 1) / 1000:
                raise ValueError("Invalid duration")
            return int(seconds * 1000)
        start = status.get("start", status.get("starttime"))
        end = status.get("end", status.get("endtime"))
        if not start or not end:
            raise ValueError("Missing duration")
        try:
            begin, finish = datetime.fromisoformat(start), datetime.fromisoformat(end)
        except ValueError:
            begin = datetime.strptime(start, "%Y%m%d %H:%M:%S.%f")
            finish = datetime.strptime(end, "%Y%m%d %H:%M:%S.%f")
        duration = int((finish - begin).total_seconds() * 1000)
        if duration < 0:
            raise ValueError("Negative duration")
        return duration
    except (ValueError, InvalidOperation, OverflowError) as exc:
        raise invalid_content("Invalid result duration") from exc


def parse_output(
    content: bytes, source_path: str = "output.xml", *, max_depth: int = 64, max_nodes: int = 250000
) -> ResultParseResult:
    if len(content) > 50 * 1024 * 1024:
        raise ParserError(413, "CONTENT_TOO_LARGE", "output.xml exceeds 50 MiB")
    try:
        return _parse_output(content, source_path, max_depth, max_nodes)
    except (ParseError, DefusedXmlException, ValidationError, RecursionError) as exc:
        raise invalid_content("Invalid or prohibited Robot XML") from exc


def _parse_output(content: bytes, path: str, max_depth: int, max_nodes: int) -> ResultParseResult:
    stack: list[Element] = []
    suites: list[str] = []
    suite_names: list[str] = []
    results: list[ParsedResult] = []
    issues: list[ImportIssue] = []
    seen: set[tuple[str, str]] = set()
    counts = {"PASS": 0, "FAIL": 0, "SKIP": 0}
    root_status: Element | None = None
    declared_counts: dict[str, int] | None = None
    nodes = 0
    root_suites = 0
    allowed = {
        "robot",
        "suite",
        "test",
        "kw",
        "status",
        "doc",
        "tag",
        "tags",
        "arg",
        "arguments",
        "assign",
        "var",
        "msg",
        "statistics",
        "total",
        "stat",
        "errors",
        "metadata",
        "meta",
        "timeout",
        "for",
        "iter",
        "if",
        "branch",
        "while",
        "try",
        "return",
        "break",
        "continue",
        "variable",
        "value",
        "group",
        "body",
        "error",
    }
    for event, element in iterparse(
        BytesIO(content),
        events=("start", "end"),
        forbid_dtd=True,
        forbid_entities=True,
        forbid_external=True,
    ):
        if event == "start":
            nodes += 1
            stack.append(element)
            if nodes > max_nodes or len(stack) > max_depth:
                raise ParserError(413, "CONTENT_TOO_LARGE", "XML structural limit exceeded")
            if len(stack) == 1 and element.tag != "robot":
                raise invalid_content("Expected Robot output.xml")
            if element.tag not in allowed:
                raise invalid_content("Unsupported Robot XML element")
            if element.tag == "suite":
                if len(stack) < 2 or stack[-2].tag not in {"robot", "suite", "statistics"}:
                    raise invalid_content("Invalid suite hierarchy")
                # The statistics section has a distinct <suite> container, not an actual suite.
                if stack[-2].tag == "statistics":
                    continue
                if stack[-2].tag == "robot":
                    root_suites += 1
                    if root_suites > 1:
                        raise invalid_content("Multiple root suites")
                suite_names.append(element.get("name", "unnamed"))
                source = element.get("source", "")
                try:
                    ObjectRef.safe_path(source)
                except ValueError:
                    source = ""
                if not source:
                    source = "/".join(["__unmapped__", *suite_names])
                    if len(source) > 512:
                        raise invalid_content("Suite identity exceeds contract limits")
                    issues.append(
                        issue(
                            "UNRESOLVED_SUITE_SOURCE",
                            path,
                            None,
                            "No relative suite source; Java must retain unmatched results",
                        )
                    )
                suites.append(source)
            continue
        parent = stack[-2] if len(stack) > 1 else None
        if element.tag == "test":
            if parent is None or parent.tag != "suite" or not suites:
                raise invalid_content("Test must belong to a suite")
            status = element.find("status")
            if status is None:
                raise invalid_content("Test status is missing")
            state = status.get("status", "")
            if state not in counts:
                raise invalid_content("Unsupported test status")
            identity = (suites[-1], element.get("name", ""))
            if identity in seen:
                raise invalid_content("Duplicate result identity")
            seen.add(identity)
            failed_keyword = None
            for keyword in element.iter("kw"):
                keyword_status = keyword.find("status")
                if (
                    state == "FAIL"
                    and keyword_status is not None
                    and keyword_status.get("status") == "FAIL"
                ):
                    failed_keyword = keyword.get("name")
            results.append(
                ParsedResult(
                    suite_path=identity[0],
                    case_name=identity[1],
                    status=cast(Literal["PASS", "FAIL", "SKIP"], state),
                    message=status.text or "",
                    elapsed_ms=elapsed(status),
                    failed_keyword=failed_keyword,
                )
            )
            counts[state] += 1
            if len(results) > 10000:
                raise ParserError(413, "CONTENT_TOO_LARGE", "Too many test results")
            parent.remove(element)
            element.clear()
        elif element.tag == "suite" and parent is not None and parent.tag != "statistics":
            if len(suites) == 1:
                root_status = element.find("status")
            suites.pop()
            suite_names.pop()
            if parent is not None:
                parent.remove(element)
        elif element.tag == "stat" and parent is not None and parent.tag == "total":
            if declared_counts is not None:
                raise invalid_content("Ambiguous root statistics")
            try:
                declared_counts = {k: int(element.attrib[k.lower()]) for k in counts}
            except (KeyError, ValueError) as exc:
                raise invalid_content("Invalid root statistics") from exc
        # Retain test children until the test is consumed. Discard unrelated log payloads.
        elif element.tag == "msg" and not any(e.tag == "test" for e in stack):
            if parent is not None:
                parent.remove(element)
        stack.pop()
    if root_status is None:
        raise invalid_content("Root suite status is missing")
    if declared_counts is not None and declared_counts != counts:
        raise invalid_content("Root statistics disagree with leaf test results")
    return ResultParseResult(
        counts=RunCounts(
            total=len(results), pass_=counts["PASS"], fail=counts["FAIL"], skip=counts["SKIP"]
        ),
        started_at=timestamp(root_status.get("start", root_status.get("starttime")), path, issues),
        finished_at=timestamp(root_status.get("end", root_status.get("endtime")), path, issues),
        results=results,
        issues=issues,
    )
