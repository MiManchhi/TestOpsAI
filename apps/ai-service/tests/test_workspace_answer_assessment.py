import pytest

from testops_ai.features.answers.assessment import findings_for
from testops_ai.features.answers.models import EvidenceRef, Proof, RunContext, VisibleBasis


@pytest.mark.parametrize(
    ("clause", "measurement", "verdict"),
    [
        ("设备应在 3 秒内响应。", "response_time = 2.5 s", "MEETS"),
        ("设备应在 2 秒内响应。", "response_time = 2.5 s", "DOES_NOT_MEET"),
        ("响应时间 ≤ 2 秒", "response_time = 2000 ms", "MEETS"),
        ("响应时间 < 2 秒", "response_time = 2000 ms", "DOES_NOT_MEET"),
        ("响应时间 ≤ 2 秒", "elapsed = 2000 ms", "INSUFFICIENT"),
    ],
)
def test_assessment_compares_only_measured_latency(
    clause: str,
    measurement: str,
    verdict: str,
) -> None:
    rule = Proof(
        source=EvidenceRef(
            resource_kind="DOCUMENT",
            resource_id="1",
            version="V1",
            locator="line 2",
        ),
        excerpt=clause,
    )
    run = RunContext(
        run_id="2",
        name="复测",
        executed_at="2026-11-20T12:00:00Z",
        results=[],
        facts=[
            Proof(
                source=EvidenceRef(
                    resource_kind="RUN_LOG",
                    resource_id="3",
                    version="literal",
                    locator="line 1",
                ),
                excerpt=measurement,
            )
        ],
    )
    basis = VisibleBasis(
        basis_id="1",
        label="验收包",
        visible_version_ids=["1"],
        coverage="COMPLETE",
    )
    result = findings_for(run, basis, "CURRENT", [rule])
    assert result["verdict"] == verdict
    assert len(result["sources"]) == (1 if verdict == "INSUFFICIENT" else 2)
    assert (
        findings_for(run, basis.model_copy(update={"coverage": "PARTIAL"}), "CURRENT", [rule])[
            "verdict"
        ]
        == "NEEDS_CONFIRMATION"
    )
