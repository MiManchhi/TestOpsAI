import pytest
from conftest import OUTPUT, validate_contract

from testops_ai.features.robot_parser.errors import ParserError
from testops_ai.features.robot_parser.output import parse_output


def test_leaf_statistics_failure_and_contract():
    result = parse_output(OUTPUT)
    assert result.counts.model_dump(by_alias=True) == {"total": 3, "pass": 1, "fail": 1, "skip": 1}
    assert result.results[0].failed_keyword == "Check"
    assert result.results[0].elapsed_ms == 125
    assert result.started_at == "2026-11-18T01:00:00Z"
    assert result.results[0].suite_path == "suites/demo.robot"
    validate_contract("ResultParseResult", result.model_dump(by_alias=True))


@pytest.mark.parametrize(
    "content",
    [
        b"<robot>",
        b"<notrobot/>",
        OUTPUT.replace(b'pass="1"', b'pass="99"'),
        OUTPUT.replace(b'status="SKIP"', b'status="UNKNOWN"'),
        OUTPUT.replace(b'elapsed="0.125"', b'elapsed="NaN"'),
        OUTPUT.replace(b'name="Skipped"', b'name="Example"'),
        b'<!DOCTYPE robot [<!ENTITY x "boom">]><robot>&x;</robot>',
        b'<!DOCTYPE robot SYSTEM "file:///etc/passwd"><robot/>',
    ],
)
def test_invalid_xml_rejected(content):
    with pytest.raises(ParserError):
        parse_output(content)


def test_depth_nodes_and_no_timezone():
    with pytest.raises(ParserError) as error:
        parse_output(OUTPUT, max_depth=2)
    assert error.value.status == 413
    with pytest.raises(ParserError):
        parse_output(OUTPUT, max_nodes=3)
    result = parse_output(OUTPUT.replace(b"T01:00:00Z", b"T01:00:00"))
    assert result.started_at is None
    assert result.issues[0].code == "INVALID_TIME"


def test_absolute_source_does_not_guess_case_identity():
    result = parse_output(
        OUTPUT.replace(b'source="suites/demo.robot"', b'source="/private/demo.robot"')
    )
    assert result.results[0].suite_path.startswith("__unmapped__/")
    assert result.issues[0].code == "UNRESOLVED_SUITE_SOURCE"


def test_nested_suites_and_statistics_containers_are_not_double_counted():
    nested = OUTPUT.replace(b'<suite name="Demo"', b'<suite name="Root"><suite name="Demo"')
    nested = nested.replace(
        b"</suite><statistics>", b'</suite><status status="FAIL" elapsed="1"/></suite><statistics>'
    )
    nested = nested.replace(
        b"</statistics>", b'<suite><stat pass="1" fail="1" skip="1"/></suite></statistics>'
    )
    result = parse_output(nested)
    assert result.counts.total == 3
    assert result.results[0].suite_path == "suites/demo.robot"


def test_missing_source_is_bounded_at_depth_and_extreme_duration_is_rejected():
    nested = b"<robot>" + b'<suite name="x">' * 30
    nested += b'<test name="case"><status status="PASS" elapsed="0"/></test>'
    nested += (b'<status status="PASS" elapsed="0"/></suite>' * 30) + b"</robot>"
    assert len(parse_output(nested).results[0].suite_path) < 100
    with pytest.raises(ParserError):
        parse_output(OUTPUT.replace(b'elapsed="0.125"', b'elapsed="1e999999999"'))
