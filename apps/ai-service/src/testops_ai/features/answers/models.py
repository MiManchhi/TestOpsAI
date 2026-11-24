from typing import Annotated, Literal

from pydantic import Field, field_validator, model_validator

from testops_ai.core.workspace_identity import AnswerScope
from testops_ai.features.robot_parser.models import (
    ContractModel,
    Id,
    ObjectRef,
    Sha256,
    Uuid,
    check_uuid,
)

Text = Annotated[str, Field(min_length=1)]


class EvidenceRef(ContractModel):
    resource_kind: Literal["DOCUMENT", "RUN_LOG", "CHAT_ATTACHMENT", "USER_MESSAGE"]
    resource_id: Text
    version: Text
    locator: Text


class Proof(ContractModel):
    source: EvidenceRef
    excerpt: Text


class VersionRef(ContractModel):
    version_id: Id
    knowledge_base_id: Id
    content_hash: Sha256
    version_label: Text


class VisibleBasis(ContractModel):
    basis_id: Id
    label: Text
    visible_version_ids: list[Id]
    coverage: Literal["COMPLETE", "PARTIAL"]


class AllowedFile(ObjectRef):
    attachment_id: Uuid

    _attachment = field_validator("attachment_id")(check_uuid)


class RunResult(ContractModel):
    result_id: Id
    case_name: Text
    suite: Text
    robot_status: Literal["PASS", "FAIL", "SKIP"]
    artifact_id: Id


class RunContext(ContractModel):
    run_id: Id
    name: Text
    executed_at: Text
    results: list[RunResult]
    facts: list[Proof]


class AnswerContext(ContractModel):
    scope: AnswerScope
    text: Annotated[str, Field(max_length=20000)]
    history: list[dict[str, object]]
    basis_at_run: VisibleBasis | None
    basis_current: VisibleBasis | None
    versions: list[VersionRef]
    files: list[AllowedFile]
    runs: Annotated[list[RunContext], Field(max_length=1)]
    current_message_id: Uuid

    _message = field_validator("current_message_id")(check_uuid)

    @model_validator(mode="after")
    def permitted_scope(self) -> "AnswerContext":
        if (
            set(self.scope.version_ids) != {v.version_id for v in self.versions}
            or set(self.scope.attachment_ids) != {f.attachment_id for f in self.files}
            or any(file.attachment_id != file.object_id for file in self.files)
            or set(self.scope.run_ids) != {r.run_id for r in self.runs}
            or any(
                not set(basis.visible_version_ids) <= set(self.scope.version_ids)
                for basis in (self.basis_at_run, self.basis_current)
                if basis
            )
        ):
            raise ValueError("Context exceeds delegated scope")
        return self


class ModelDraft(ContractModel):
    summary: Annotated[str, Field(min_length=1, max_length=5000)]
    robot_draft: Annotated[str, Field(max_length=20000)] | None
    pending: Annotated[
        list[Annotated[str, Field(min_length=1, max_length=500)]], Field(max_length=20)
    ]
    sources: Annotated[list[int], Field(max_length=12)]


class AnswerCandidate(ContractModel):
    summary: str
    robot_status: Literal["NOT_RUN", "UNKNOWN", "PASS", "FAIL", "SKIP"]
    findings: list[dict[str, object]]
    robot_draft: str | None
    pending: list[str]
    sources: list[EvidenceRef]
