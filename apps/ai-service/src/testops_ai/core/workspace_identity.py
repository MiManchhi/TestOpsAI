"""Version 2 workspace delegation and the signed authorization scope."""

import hashlib
import hmac
import json
from typing import Annotated, Literal

from pydantic import Field, field_validator, model_validator

from testops_ai.features.robot_parser.models import ContractModel, Id, Sha256, Uuid, check_uuid

WorkspaceAction = Literal["composeAnswer", "resolveEvidence", "exportIndexBlocks"]


class AnswerScope(ContractModel):
    project_id: Id
    turn_id: Uuid
    auth_revision: Annotated[int, Field(ge=1)]
    version_ids: list[Id]
    run_ids: list[Id]
    attachment_ids: list[Uuid]

    _turn = field_validator("turn_id")(check_uuid)

    @field_validator("attachment_ids")
    @classmethod
    def validate_attachments(cls, ids: list[str]) -> list[str]:
        return [check_uuid(value) for value in ids]

    def digest(self) -> str:
        scope = [
            self.project_id,
            self.turn_id,
            self.auth_revision,
            sorted(set(self.version_ids)),
            sorted(set(self.run_ids)),
            sorted(set(self.attachment_ids)),
        ]
        return hashlib.sha256(json.dumps(scope, separators=(",", ":")).encode("utf-8")).hexdigest()


class WorkspaceDelegation(ContractModel):
    iss: Literal["testops-server"]
    aud: Literal["testops-server-tools"]
    iat: Annotated[int, Field(ge=0)]
    exp: Annotated[int, Field(ge=0)]
    jti: Uuid
    operation_id: Uuid
    actor_id: Id
    project_id: Id
    authorization_version: Annotated[int, Field(ge=1)]
    allow_actions: Annotated[list[WorkspaceAction], Field(min_length=1, max_length=2)]
    scope_digest: Sha256 | None = None
    version_id: Id | None = None
    content_hash: Sha256 | None = None

    _uuid = field_validator("jti", "operation_id")(check_uuid)

    @model_validator(mode="after")
    def check_action_scope(self) -> "WorkspaceDelegation":
        if len(set(self.allow_actions)) != len(self.allow_actions):
            raise ValueError("Duplicate workspace actions")
        if "exportIndexBlocks" in self.allow_actions:
            if len(self.allow_actions) != 1 or not self.version_id or not self.content_hash:
                raise ValueError("Expected a version-bound index export")
            if self.scope_digest:
                raise ValueError("Index export cannot authorize an answer")
        elif not self.scope_digest or self.version_id or self.content_hash:
            raise ValueError("Expected a signed answer scope")
        return self

    def matches_answer(self, scope: AnswerScope) -> bool:
        return (
            self.operation_id == scope.turn_id
            and self.project_id == scope.project_id
            and self.authorization_version == scope.auth_revision
            and self.scope_digest is not None
            and hmac.compare_digest(self.scope_digest, scope.digest())
        )
