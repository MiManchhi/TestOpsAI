"""Typed projections of frozen contract 1.0.0; tested against the shared JSON schemas."""

from typing import Annotated, Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator
from pydantic.alias_generators import to_camel

Id = Annotated[str, Field(pattern=r"^[1-9][0-9]*$", max_length=19)]
Sha256 = Annotated[str, Field(pattern=r"^[a-f0-9]{64}$")]
Name = Annotated[str, Field(max_length=256)]
Path = Annotated[str, Field(max_length=512)]
Uuid = Annotated[str, Field(min_length=36, max_length=36)]


class ContractModel(BaseModel):
    model_config = ConfigDict(
        extra="forbid", strict=True, alias_generator=to_camel, populate_by_name=True
    )


def check_uuid(value: str) -> str:
    UUID(value)
    return value


class ObjectRef(ContractModel):
    object_id: Uuid
    sha256: Sha256
    size_bytes: Annotated[int, Field(ge=1, le=50 * 1024 * 1024)]
    logical_path: Annotated[Path, Field(min_length=1)]

    _uuid = field_validator("object_id")(check_uuid)

    @field_validator("logical_path")
    @classmethod
    def safe_path(cls, value: str) -> str:
        if (
            value.startswith("/")
            or "\\" in value
            or ":" in value
            or any(part in {"", ".", ".."} for part in value.split("/"))
            or any(ord(char) < 32 for char in value)
        ):
            raise ValueError("Invalid logical path")
        return value


class ResultParseRequest(ContractModel):
    operation_id: Uuid
    object: ObjectRef

    _uuid = field_validator("operation_id")(check_uuid)


class ImportIssue(ContractModel):
    code: Annotated[str, Field(max_length=64)]
    source_path: Path
    line: Annotated[int, Field(ge=1)] | None
    message: Annotated[str, Field(max_length=1000)]


class SourceRef(ContractModel):
    path: Path
    line: Annotated[int, Field(ge=1)]
    revision: Sha256


class Step(ContractModel):
    keyword: Name
    arguments: Annotated[list[Annotated[str, Field(max_length=2000)]], Field(max_length=50)]
    line: Annotated[int, Field(ge=1)]


class ParsedSuite(ContractModel):
    path: Path
    name: Name
    parent_path: Path | None


class ParsedCase(ContractModel):
    suite_path: Path
    name: Name
    documentation: Annotated[str, Field(max_length=10000)]
    tags: Annotated[list[Annotated[str, Field(max_length=64)]], Field(max_length=30)]
    source: SourceRef
    steps: Annotated[list[Step], Field(max_length=500)]


class RobotParseResult(ContractModel):
    suites: Annotated[list[ParsedSuite], Field(max_length=1000)]
    cases: Annotated[list[ParsedCase], Field(max_length=10000)]
    issues: Annotated[list[ImportIssue], Field(max_length=1000)]


class RunCounts(ContractModel):
    total: Annotated[int, Field(ge=0)]
    pass_: Annotated[int, Field(alias="pass", ge=0)]
    fail: Annotated[int, Field(ge=0)]
    skip: Annotated[int, Field(ge=0)]


class ParsedResult(ContractModel):
    suite_path: Path
    case_name: Name
    status: Literal["PASS", "FAIL", "SKIP"]
    message: Annotated[str, Field(max_length=10000)]
    elapsed_ms: Annotated[int, Field(ge=0)]
    failed_keyword: Name | None


class ResultParseResult(ContractModel):
    counts: RunCounts
    started_at: str | None
    finished_at: str | None
    results: Annotated[list[ParsedResult], Field(max_length=10000)]
    issues: Annotated[list[ImportIssue], Field(max_length=1000)]


class ServiceClaims(ContractModel):
    iss: Literal["testops-server", "testops-ai"]
    aud: Literal["testops-ai", "testops-server-tools"]
    iat: Annotated[int, Field(ge=0)]
    exp: Annotated[int, Field(ge=0)]
    jti: Uuid
    operation_id: Uuid

    _uuids = field_validator("jti", "operation_id")(check_uuid)


class AuthorizedScope(ContractModel):
    project_id: Id
    actor_id: Id
    knowledge_base_ids: Annotated[list[Id], Field(max_length=5)]
    document_ids: Annotated[list[Id], Field(max_length=20)]
    case_ids: Annotated[list[Id], Field(max_length=1000)]
    run_ids: Annotated[list[Id], Field(max_length=10)]
    allowed_tools: Annotated[
        list[
            Literal[
                "search_test_cases",
                "get_test_case_detail",
                "get_run_summary",
                "get_failed_cases",
                "search_knowledge",
                "create_case_draft",
            ]
        ],
        Field(max_length=6),
    ]
    expires_at: str
    case_scope: Literal["PROJECT", "SELECTION"]

    @model_validator(mode="after")
    def restricted_scope(self) -> "AuthorizedScope":
        if self.case_scope == "PROJECT" and self.case_ids:
            raise ValueError("PROJECT requires empty caseIds")
        for values in (self.knowledge_base_ids, self.document_ids, self.case_ids, self.run_ids):
            if len(values) != len(set(values)):
                raise ValueError("Duplicate scope identifiers")
        return self


class DelegationClaims(ContractModel):
    iss: Literal["testops-server"]
    aud: Literal["testops-server-tools"]
    iat: Annotated[int, Field(ge=0)]
    exp: Annotated[int, Field(ge=0)]
    jti: Uuid
    operation_id: Uuid
    actor_id: Id
    project_id: Id
    scope: AuthorizedScope
    object_ids: Annotated[list[Uuid], Field(max_length=200)]
    authorization_version: Annotated[int, Field(ge=1)]

    _uuids = field_validator("jti", "operation_id")(check_uuid)

    @field_validator("object_ids")
    @classmethod
    def unique_objects(cls, value: list[str]) -> list[str]:
        for item in value:
            check_uuid(item)
        if len(value) != len(set(value)):
            raise ValueError("Duplicate objects")
        return value
