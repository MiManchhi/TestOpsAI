import pytest
from conftest import ROBOT, source, validate_contract
from pydantic import ValidationError

from testops_ai.features.robot_parser.catalog import parse_catalog
from testops_ai.features.robot_parser.errors import ParserError


def test_catalog_source_tags_steps_and_contract():
    data = b"""*** Settings ***
Test Tags    smoke    Smoke
Default Tags    default
Library    NeverImportThisModule
*** Test Cases ***
Example
    [Documentation]    first
    ...    second
    [Tags]    alpha    alpha    ${DYNAMIC}
    Log    one
    ...    two
"""
    file = source(data)
    result = parse_catalog([file])
    case = result.cases[0]
    assert case.tags == ["smoke", "Smoke", "alpha"]
    assert case.documentation == "first\nsecond"
    assert case.steps[0].arguments == ["one", "two"]
    assert case.steps[0].line == 10
    assert case.source.revision == file.reference.sha256
    assert {i.code for i in result.issues} == {"IMPORT_NOT_EXECUTED", "DYNAMIC_VALUE"}
    validate_contract("RobotParseResult", result.model_dump(by_alias=True))


def test_hierarchy_and_same_name_across_suites():
    result = parse_catalog([source(), source(ROBOT, "suites/nested/other.robot")])
    assert len(result.cases) == 2
    assert next(s for s in result.suites if s.path == "suites/nested").parent_path == "suites"
    files = [source(), source(ROBOT, "suites/other.robot")]
    assert parse_catalog(files) == parse_catalog(files[::-1])


def test_snapshot_resource_is_never_executed():
    robot = source(b"*** Settings ***\nResource    helper.resource\n" + ROBOT)
    resource = source(
        b"*** Keywords ***\nHelper\n    Fail    never run\n", "suites/helper.resource"
    )
    assert len(parse_catalog([robot, resource]).cases) == 1


@pytest.mark.parametrize(
    "target", ["../secret.resource", "/etc/file", "C:/file", "missing.resource"]
)
def test_external_resources_are_rejected(target):
    data = ("*** Settings ***\nResource    " + target + "\n").encode() + ROBOT
    with pytest.raises(ParserError):
        parse_catalog([source(data)])


@pytest.mark.parametrize(
    "data",
    [
        ROBOT + b"Example\n    Log    duplicate\n",
        b"*** Invalid ***\nbad\n",
        b"\xff\xfe\xff",
    ],
)
def test_invalid_catalog_is_not_partially_returned(data):
    with pytest.raises(ParserError):
        parse_catalog([source(data)])


def test_dynamic_control_flow_is_not_flattened():
    data = b"*** Test Cases ***\nExample\n    IF    ${FLAG}\n        Fail    not run\n    END\n"
    result = parse_catalog([source(data)])
    assert result.cases[0].steps == []
    assert "STATIC_STRUCTURE" in {i.code for i in result.issues}


def test_init_inheritance_default_tags_and_removal():
    files = [
        source(b"*** Settings ***\nTest Tags    root\n", "__init__.robot"),
        source(b"*** Settings ***\nTest Tags    domain\n", "suites/__init__.robot"),
        source(
            b"*** Settings ***\nDefault Tags    fallback\n*** Test Cases ***\n"
            b"Default\n    Log    hi\nExplicit\n    [Tags]    -domain    exact\n    Log    hi\n"
        ),
    ]
    result = parse_catalog(files)
    assert result.cases[0].tags == ["root", "domain", "fallback"]
    assert result.cases[1].tags == ["root", "exact"]
    assert result == parse_catalog(files[::-1])


@pytest.mark.parametrize(
    "path", ["../a.robot", "/a.robot", "C:/a.robot", "a\\b.robot", "a/./b.robot"]
)
def test_unsafe_input_paths(path):
    with pytest.raises(ValidationError):
        source(path=path)


def test_duplicate_paths_and_limits():
    file = source()
    with pytest.raises(ParserError):
        parse_catalog([file, file])
    with pytest.raises(ParserError) as error:
        parse_catalog([source(b"#" * (2 * 1024 * 1024 + 1))])
    assert error.value.status == 413


def test_hash_mismatch_and_field_limits():
    file = source()
    file.reference.sha256 = "0" * 64
    with pytest.raises(ParserError):
        parse_catalog([file])
    with pytest.raises(ParserError):
        parse_catalog([source(b"*** Test Cases ***\n" + b"x" * 257 + b"\n    Log    hi\n")])
