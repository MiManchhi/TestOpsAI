import json
import time
from pathlib import Path
from uuid import uuid4

import jwt
import pytest
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from pydantic import SecretStr

from testops_ai.core.service_tokens import ServiceTokens
from testops_ai.core.settings import Settings
from testops_ai.core.workspace_identity import AnswerScope
from testops_ai.features.robot_parser.errors import ParserError


def test_workspace_scope_matches_contract_fixture() -> None:
    root = Path(__file__).resolve().parents[3]
    data = json.loads((root / "docs/contracts/workspace.examples.json").read_text(encoding="utf-8"))
    scope = AnswerScope.model_validate(data["context"]["scope"])
    assert scope.digest() == data["scopeDigest"]


def test_signed_workspace_scope_is_bound_to_project_operation_and_action() -> None:
    private = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    server_public = private.public_key().public_bytes(
        serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo
    )
    ai_key = rsa.generate_private_key(public_exponent=65537, key_size=2048).private_bytes(
        serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()
    )
    service = ServiceTokens(
        Settings(
            server_public_key=SecretStr(server_public.decode()),
            private_key=SecretStr(ai_key.decode()),
        )
    )
    turn = str(uuid4())
    scope = AnswerScope(
        project_id="42",
        turn_id=turn,
        auth_revision=1,
        version_ids=["10", "2"],
        run_ids=[],
        attachment_ids=[],
    )
    now = int(time.time())
    common = {"iss": "testops-server", "iat": now, "exp": now + 60, "operationId": turn}
    service_token = jwt.encode(
        {**common, "aud": "testops-ai", "jti": str(uuid4())}, private, algorithm="RS256"
    )
    claims = {
        **common,
        "aud": "testops-server-tools",
        "jti": str(uuid4()),
        "actorId": "1",
        "projectId": "42",
        "authorizationVersion": 1,
        "allowActions": ["composeAnswer", "resolveEvidence"],
        "scopeDigest": scope.digest(),
    }
    delegation = jwt.encode(claims, private, algorithm="RS256")
    authorization = "Bearer " + service_token
    assert (
        service.verify_workspace_delegation(
            authorization, delegation, turn, "composeAnswer", scope=scope
        ).actor_id
        == "1"
    )

    for changed_scope in (
        scope.model_copy(update={"project_id": "47"}),
        scope.model_copy(update={"auth_revision": 2}),
        scope.model_copy(update={"version_ids": ["99"]}),
    ):
        with pytest.raises(ParserError) as denied:
            service.verify_workspace_delegation(
                authorization, delegation, turn, "composeAnswer", scope=changed_scope
            )
        assert denied.value.status == 403

    with pytest.raises(ParserError) as wrong_action:
        service.verify_workspace_delegation(authorization, delegation, turn, "exportIndexBlocks")
    assert wrong_action.value.status == 403

    expired = jwt.encode({**claims, "iat": now - 180, "exp": now - 60}, private, algorithm="RS256")
    with pytest.raises(ParserError) as unauthorized:
        service.verify_workspace_delegation(
            authorization, expired, turn, "composeAnswer", scope=scope
        )
    assert unauthorized.value.status == 401
